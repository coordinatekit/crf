/*
 * Copyright 2025-present Andy Marek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.coordinatekit.crf.core.feature.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Tests {@link XmlFeatureConfigurationParser}: a well-formed, correctly namespaced document builds
 * the expected node tree, each node's {@link SourceLocation} is captured from the reader, and every
 * other malformed-input problem — including a schema violation reported by
 * {@code feature-configuration.xsd} validation — surfaces as a located
 * {@link FeatureConfigurationParseException}.
 */
class XmlFeatureConfigurationParserTest {
    private static final String NAMESPACE = "https://coordinatekit.org/schema/crf/feature-configuration";
    private static final XmlFeatureConfigurationParser PARSER = new XmlFeatureConfigurationParser();

    /**
     * Builds a document with {@code depth} nested {@code <extractor type="composite">} elements
     * wrapping an innermost {@code <extractor type="length"/>}, wrapped in the
     * {@code <featureExtractors>} root.
     *
     * @param depth the number of nested composite elements
     * @return the XML document text
     */
    private static String nestedExtractors(int depth) {
        StringBuilder xml = new StringBuilder("<featureExtractors xmlns=\"" + NAMESPACE + "\">");
        xml.repeat("<extractor type=\"composite\">", Math.max(0, depth));
        xml.append("<extractor type=\"length\"/>");
        xml.repeat("</extractor>", Math.max(0, depth));
        xml.append("</featureExtractors>");
        return xml.toString();
    }

    private static FeatureExtractorNode parse(String xml) {
        return PARSER.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), URI.create("config.xml"));
    }

    @Test
    void parse__capturesLineAndColumnOnEachNode() {
        // ARRANGE //
        String xml = """
                <featureExtractors xmlns="https://coordinatekit.org/schema/crf/feature-configuration">
                    <extractor type="composite">
                        <extractor type="length"/>
                    </extractor>
                </featureExtractors>
                """;

        // ACT //
        FeatureExtractorNode root = parse(xml);
        FeatureExtractorNode child = root.children().getFirst();

        // ASSERT //
        assertEquals(URI.create("config.xml"), root.sourceLocation().orElseThrow().uri());
        assertTrue(root.sourceLocation().orElseThrow().line() > 0);
        assertTrue(root.sourceLocation().orElseThrow().column() > 0);
        assertTrue(
                child.sourceLocation().orElseThrow().line() > root.sourceLocation().orElseThrow().line(),
                "nested extractor should be located on a later line than its parent"
        );
        assertTrue(child.sourceLocation().orElseThrow().column() > 0);
    }

    @Test
    void parse__correctlyNamespacedPrefixedDocumentParses() {
        // ARRANGE //
        String xml = """
                <f:featureExtractors xmlns:f="https://coordinatekit.org/schema/crf/feature-configuration">
                    <f:extractor type="window">
                        <f:parameter name="before" value="3"/>
                        <f:extractor type="length"/>
                    </f:extractor>
                </f:featureExtractors>
                """;

        // ACT //
        FeatureExtractorNode root = parse(xml);

        // ASSERT //
        assertEquals("window", root.type());
        assertEquals(Map.of("before", "3"), root.parameters());
        assertEquals(1, root.children().size());
        assertEquals("length", root.children().getFirst().type());
    }

    @Test
    void parse__deepNestingWithinCapSucceeds() {
        // ARRANGE //
        String xml = nestedExtractors(FeatureExtractorNode.MAXIMUM_NESTING_DEPTH - 1);

        // ACT //
        FeatureExtractorNode root = parse(xml);

        // ASSERT //
        assertEquals("composite", root.type());
    }

    record DepthCapParameters(String name, int depth) {}

    static Stream<DepthCapParameters> parse__depthCap() {
        return Stream.of(
                new DepthCapParameters("at_cap", FeatureExtractorNode.MAXIMUM_NESTING_DEPTH),
                new DepthCapParameters("over_cap", FeatureExtractorNode.MAXIMUM_NESTING_DEPTH + 5)
        );
    }

    @MethodSource
    @ParameterizedTest
    void parse__depthCap(DepthCapParameters parameters) {
        // ARRANGE //
        String xml = nestedExtractors(parameters.depth());

        // ACT //
        FeatureConfigurationParseException exception = assertThrows(
                FeatureConfigurationParseException.class,
                () -> parse(xml)
        );

        // ASSERT //
        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(
                message.contains("nesting is deeper than the maximum of " + FeatureExtractorNode.MAXIMUM_NESTING_DEPTH),
                "message should report the depth cap; was: " + message
        );
        assertTrue(message.startsWith("config.xml:"));
    }

    record ParseExceptionParameters(
            String name,
            Executable action,
            Class<? extends Exception> expectedClass,
            String expectedMessageContains
    ) {}

    static Stream<ParseExceptionParameters> parse__exception() {
        return Stream.of(
                new ParseExceptionParameters("doctype_rejected", () -> parse("""
                        <?xml version="1.0"?>
                        <!DOCTYPE featureExtractors [<!ENTITY xxe "test">]>
                        <featureExtractors xmlns="https://coordinatekit.org/schema/crf/feature-configuration">
                            <extractor type="length"/>
                        </featureExtractors>
                        """), FeatureConfigurationParseException.class, "DOCTYPE"),
                new ParseExceptionParameters(
                        "malformed_xml",
                        () -> parse(
                                "<featureExtractors xmlns=\"https://coordinatekit.org/schema/crf/feature-configuration\">"
                                        + "<extractor type=\"length\"></featureExtractors>"
                        ),
                        FeatureConfigurationParseException.class,
                        // "extractor" is the JDK's mismatched-end-tag text, incidental to this case; the
                        // real guarantee is the shared config.xml: location-prefix assertion below.
                        "extractor"
                ),
                new ParseExceptionParameters(
                        "no_namespace_rejected",
                        () -> parse("<featureExtractors><extractor type=\"length\"/></featureExtractors>"),
                        FeatureConfigurationParseException.class,
                        "featureExtractors"
                ),
                new ParseExceptionParameters(
                        "wrong_namespace_rejected",
                        () -> parse(
                                "<featureExtractors xmlns=\"urn:test\"><extractor type=\"length\"/></featureExtractors>"
                        ),
                        FeatureConfigurationParseException.class,
                        "featureExtractors"
                ),
                new ParseExceptionParameters(
                        "no_top_level_extractor",
                        () -> parse(
                                "<featureExtractors xmlns=\"https://coordinatekit.org/schema/crf/feature-configuration\"/>"
                        ),
                        FeatureConfigurationParseException.class,
                        "extractor"
                ),
                new ParseExceptionParameters(
                        "multiple_top_level_extractors",
                        () -> parse(
                                "<featureExtractors xmlns=\"https://coordinatekit.org/schema/crf/feature-configuration\">"
                                        + "<extractor type=\"length\"/><extractor type=\"length\"/></featureExtractors>"
                        ),
                        FeatureConfigurationParseException.class,
                        "extractor"
                ),
                new ParseExceptionParameters(
                        "unexpected_root_child",
                        () -> parse(
                                "<featureExtractors xmlns=\"https://coordinatekit.org/schema/crf/feature-configuration\"><foo/></featureExtractors>"
                        ),
                        FeatureConfigurationParseException.class,
                        "foo"
                ),
                new ParseExceptionParameters(
                        "unexpected_extractor_child",
                        () -> parse(
                                "<featureExtractors xmlns=\"https://coordinatekit.org/schema/crf/feature-configuration\">"
                                        + "<extractor type=\"length\"><foo/></extractor></featureExtractors>"
                        ),
                        FeatureConfigurationParseException.class,
                        "foo"
                ),
                new ParseExceptionParameters(
                        "missing_type_attribute",
                        () -> parse(
                                "<featureExtractors xmlns=\"https://coordinatekit.org/schema/crf/feature-configuration\">"
                                        + "<extractor/></featureExtractors>"
                        ),
                        FeatureConfigurationParseException.class,
                        "type"
                ),
                new ParseExceptionParameters(
                        "bad_key_attribute",
                        () -> parse(
                                "<featureExtractors xmlns=\"https://coordinatekit.org/schema/crf/feature-configuration\">"
                                        + "<extractor type=\"length\" key=\"maybe\"/></featureExtractors>"
                        ),
                        FeatureConfigurationParseException.class,
                        "maybe"
                ),
                new ParseExceptionParameters(
                        "missing_parameter_name",
                        () -> parse(
                                "<featureExtractors xmlns=\"https://coordinatekit.org/schema/crf/feature-configuration\">"
                                        + "<extractor type=\"length\"><parameter value=\"3\"/></extractor></featureExtractors>"
                        ),
                        FeatureConfigurationParseException.class,
                        "name"
                ),
                new ParseExceptionParameters(
                        "missing_parameter_value",
                        () -> parse(
                                "<featureExtractors xmlns=\"https://coordinatekit.org/schema/crf/feature-configuration\">"
                                        + "<extractor type=\"length\"><parameter name=\"before\"/></extractor>"
                                        + "</featureExtractors>"
                        ),
                        FeatureConfigurationParseException.class,
                        "value"
                ),
                new ParseExceptionParameters(
                        "duplicate_parameter_name",
                        () -> parse(
                                "<featureExtractors xmlns=\"https://coordinatekit.org/schema/crf/feature-configuration\">"
                                        + "<extractor type=\"window\">" + "<parameter name=\"before\" value=\"1\"/>"
                                        + "<parameter name=\"before\" value=\"2\"/>"
                                        + "</extractor></featureExtractors>"
                        ),
                        FeatureConfigurationParseException.class,
                        "before"
                ),
                new ParseExceptionParameters(
                        "non_empty_parameter_child_element",
                        () -> parse(
                                "<featureExtractors xmlns=\"https://coordinatekit.org/schema/crf/feature-configuration\">"
                                        + "<extractor type=\"length\">"
                                        + "<parameter name=\"before\" value=\"1\"><bogus/></parameter>"
                                        + "</extractor></featureExtractors>"
                        ),
                        FeatureConfigurationParseException.class,
                        "parameter"
                ),
                new ParseExceptionParameters(
                        "non_empty_parameter_text",
                        () -> parse(
                                "<featureExtractors xmlns=\"https://coordinatekit.org/schema/crf/feature-configuration\">"
                                        + "<extractor type=\"length\"><parameter name=\"before\" value=\"1\">junk</parameter>"
                                        + "</extractor></featureExtractors>"
                        ),
                        FeatureConfigurationParseException.class,
                        "parameter"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void parse__exception(ParseExceptionParameters parameters) {
        // ACT //
        Exception exception = assertThrows(parameters.expectedClass(), parameters.action());

        // ASSERT //
        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(
                message.contains(parameters.expectedMessageContains()),
                "expected message to contain '" + parameters.expectedMessageContains() + "' but was: " + message
        );
        assertTrue(message.startsWith("config.xml:"), "message should carry the source location; was: " + message);
    }

    @Test
    void parse__exceptionPinsRealLineNumber() {
        // ARRANGE //
        String xml = """
                <featureExtractors xmlns="https://coordinatekit.org/schema/crf/feature-configuration">
                <foo/>
                </featureExtractors>
                """;

        // ACT //
        FeatureConfigurationParseException exception = assertThrows(
                FeatureConfigurationParseException.class,
                () -> parse(xml)
        );

        // ASSERT //
        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(
                message.startsWith("config.xml:2:"),
                "message should pin the real line number, not -1; was: " + message
        );
    }

    @SuppressWarnings("resource")
    @Test
    void parse__inputStreamReadFailureThrowsUncheckedIOException() {
        // ARRANGE //
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("boom");
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                throw new IOException("boom");
            }
        };

        // ACT //
        UncheckedIOException exception = assertThrows(
                UncheckedIOException.class,
                () -> PARSER.parse(failing, URI.create("config.xml"))
        );

        // ASSERT //
        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(
                message.startsWith("could not read feature configuration:"),
                "message should report the read failure; was: " + message
        );
    }

    record KeyAttributeParameters(String name, String rawValue, boolean expectedKey) {}

    static Stream<KeyAttributeParameters> parse__keyAttributeLexicalForms() {
        return Stream.of(
                new KeyAttributeParameters("one", "1", true),
                new KeyAttributeParameters("zero", "0", false),
                new KeyAttributeParameters("false", "false", false),
                new KeyAttributeParameters("whitespace_padded_true", " true ", true)
        );
    }

    @MethodSource
    @ParameterizedTest
    void parse__keyAttributeLexicalForms(KeyAttributeParameters parameters) {
        // ARRANGE //
        String xml = "<featureExtractors xmlns=\"" + NAMESPACE + "\"><extractor type=\"length\" key=\""
                + parameters.rawValue() + "\"/></featureExtractors>";

        // ACT //
        FeatureExtractorNode root = parse(xml);

        // ASSERT //
        assertEquals(parameters.expectedKey(), root.key());
    }

    @SuppressWarnings("resource")
    @Test
    void parse__oversizedConfigurationRejected() {
        // ARRANGE //
        // A filler stream that yields MAXIMUM_DOCUMENT_BYTES + 1 bytes then EOF. Content need not be
        // valid XML: the size guard trips before any parsing.
        InputStream oversized = new InputStream() {
            private int remaining = XmlFeatureConfigurationParser.MAXIMUM_DOCUMENT_BYTES + 1;

            @Override
            public int read() {
                return remaining-- > 0 ? 'a' : -1;
            }
        };

        // ACT //
        FeatureConfigurationParseException exception = assertThrows(
                FeatureConfigurationParseException.class,
                () -> PARSER.parse(oversized, URI.create("config.xml"))
        );

        // ASSERT //
        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(message.startsWith("config.xml:"), "message should carry the source location; was: " + message);
        assertTrue(
                message.contains(
                        "the configuration is larger than the maximum of "
                                + XmlFeatureConfigurationParser.MAXIMUM_DOCUMENT_BYTES + " bytes"
                ),
                "message should report the size cap; was: " + message
        );
    }

    @Test
    void parse__urlNamesSourceFromFile(@TempDir Path directory) throws IOException {
        // ARRANGE //
        Path file = directory.resolve("features.xml");
        Files.writeString(
                file,
                "<featureExtractors xmlns=\"" + NAMESPACE + "\"><extractor type=\"length\"/></featureExtractors>"
        );

        // ACT //
        FeatureExtractorNode root = PARSER.parse(file.toUri().toURL());

        // ASSERT //
        assertEquals(file.toUri(), root.sourceLocation().orElseThrow().uri());
    }

    @Test
    void parse__urlNotValidUriThrowsIllegalArgumentException(@TempDir Path directory) throws IOException {
        // ARRANGE //
        Path file = directory.resolve("a b.xml");
        Files.writeString(
                file,
                "<featureExtractors xmlns=\"" + NAMESPACE + "\"><extractor type=\"length\"/></featureExtractors>"
        );
        // File.toURL() (unlike File.toURI().toURL()) does not percent-encode the space, producing a
        // URL that opens successfully but whose toURI() rejects the raw space.
        @SuppressWarnings("deprecation")
        URL url = file.toFile().toURL();

        // ACT //
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> PARSER.parse(url));

        // ASSERT //
        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(
                message.startsWith("configuration URL is not a valid URI:"),
                "message should report the invalid URI; was: " + message
        );
    }

    @Test
    void parse__urlOpenStreamFailureThrowsUncheckedIOException(@TempDir Path directory) throws IOException {
        // ARRANGE //
        URL url = directory.resolve("missing.xml").toUri().toURL();

        // ACT //
        UncheckedIOException exception = assertThrows(UncheckedIOException.class, () -> PARSER.parse(url));

        // ASSERT //
        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(
                message.startsWith("could not read feature configuration:"),
                "message should report the read failure; was: " + message
        );
    }

    @SuppressWarnings("SequencedCollectionMethodCanBeUsed")
    @Test
    void parse__wellFormedDocumentBuildsExpectedTree() {
        // ARRANGE //
        String xml = """
                <featureExtractors xmlns="https://coordinatekit.org/schema/crf/feature-configuration">
                    <extractor type="window" key="true">
                        <parameter name="before" value="3"/>
                        <parameter name="after" value="3"/>
                        <extractor type="composite">
                            <extractor type="length"/>
                            <extractor type="prefix">
                                <parameter name="name" value="PREFIX2"/>
                            </extractor>
                        </extractor>
                    </extractor>
                </featureExtractors>
                """;

        // ACT //
        FeatureExtractorNode root = parse(xml);

        // ASSERT //
        assertEquals("window", root.type());
        assertTrue(root.key());
        assertEquals(Map.of("before", "3", "after", "3"), root.parameters());
        assertEquals(1, root.children().size());

        FeatureExtractorNode composite = root.children().getFirst();
        assertEquals("composite", composite.type());
        assertFalse(composite.key());
        assertEquals(Map.of(), composite.parameters());
        assertEquals(2, composite.children().size());

        FeatureExtractorNode length = composite.children().get(0);
        assertEquals("length", length.type());
        assertEquals(Map.of(), length.parameters());
        assertEquals(List.of(), length.children());

        FeatureExtractorNode prefix = composite.children().get(1);
        assertEquals("prefix", prefix.type());
        assertEquals(Map.of("name", "PREFIX2"), prefix.parameters());
        assertEquals(List.of(), prefix.children());
    }
}
