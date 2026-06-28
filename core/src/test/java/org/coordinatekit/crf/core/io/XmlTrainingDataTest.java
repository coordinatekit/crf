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
package org.coordinatekit.crf.core.io;

import org.coordinatekit.crf.core.StringTagProvider;
import org.coordinatekit.crf.core.UncheckedCrfException;
import org.coordinatekit.crf.core.preprocessing.SegmentKind;
import org.coordinatekit.crf.core.preprocessing.TrainingSegment;
import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Stream;

import static org.coordinatekit.crf.core.io.TrainingSequenceFixtures.DOCTYPE_DOCUMENT;
import static org.coordinatekit.crf.core.io.TrainingSequenceFixtures.assertBrownFox;
import static org.coordinatekit.crf.core.io.TrainingSequenceFixtures.assertLazySleepingDog;
import static org.coordinatekit.crf.core.io.TrainingSequenceFixtures.emptyTagProviderMessage;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlTrainingDataTest {
    private static final XmlTrainingData<String> DATA = new XmlTrainingData<>(new StringTagProvider("0"));

    // language=XML
    private static final String GENERATE_SCHEMA__ADJECTIVE_NOUN_VERB = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns="https://example.org/tags" targetNamespace="https://example.org/tags" elementFormDefault="qualified">
                <xs:complexType name="TagType" mixed="true">
                    <xs:simpleContent>
                        <xs:extension base="xs:string"/>
                    </xs:simpleContent>
                </xs:complexType>

                <xs:element name="Adjective" type="TagType"/>
                <xs:element name="Noun" type="TagType"/>
                <xs:element name="Verb" type="TagType"/>
            </xs:schema>
            """;
    // language=XML
    private static final String GENERATE_SCHEMA__NOUN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns="https://example.org/tags" targetNamespace="https://example.org/tags" elementFormDefault="qualified">
                <xs:complexType name="TagType" mixed="true">
                    <xs:simpleContent>
                        <xs:extension base="xs:string"/>
                    </xs:simpleContent>
                </xs:complexType>

                <xs:element name="Noun" type="TagType"/>
            </xs:schema>
            """;
    // language=XML
    private static final String GENERATE_SCHEMA__NOUN_VERB = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns="https://example.org/tags" targetNamespace="https://example.org/tags" elementFormDefault="qualified">
                <xs:complexType name="TagType" mixed="true">
                    <xs:simpleContent>
                        <xs:extension base="xs:string"/>
                    </xs:simpleContent>
                </xs:complexType>

                <xs:element name="Noun" type="TagType"/>
                <xs:element name="Verb" type="TagType"/>
            </xs:schema>
            """;
    // language=XML
    private static final String GENERATE_SCHEMA__NOUN_VERB__DIFFERENT_NAMESPACE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns="https://example.org/my-tags" targetNamespace="https://example.org/my-tags" elementFormDefault="qualified">
                <xs:complexType name="TagType" mixed="true">
                    <xs:simpleContent>
                        <xs:extension base="xs:string"/>
                    </xs:simpleContent>
                </xs:complexType>

                <xs:element name="Noun" type="TagType"/>
                <xs:element name="Verb" type="TagType"/>
            </xs:schema>
            """;
    // A schema generated with no target namespace: TagType and the tag elements are declared in no
    // namespace, so the bare tag elements the writer emits by default validate against it.
    // language=XML
    private static final String GENERATE_SCHEMA__NO_NAMESPACE__NOUN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:complexType name="TagType" mixed="true">
                    <xs:simpleContent>
                        <xs:extension base="xs:string"/>
                    </xs:simpleContent>
                </xs:complexType>

                <xs:element name="Noun" type="TagType"/>
            </xs:schema>
            """;
    // language=XML
    private static final String READ__MULTIPLE_RECORDS_XML = """
            <Collection>
                <Sequence><Adjective>Brown</Adjective> <Noun>Fox</Noun>!</Sequence>
                <Sequence><Adjective>Lazy</Adjective> <Adjective>Sleeping</Adjective> <Noun>Dog</Noun>!</Sequence>
            </Collection>
            """;
    // language=XML
    private static final String READ__MULTIPLE_RECORDS_SCHEMA_XML = """
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema" xmlns="https://coordinatekit.org/crf/schema/tags">
                <crf:Sequence>
                    <Adjective>Brown</Adjective>
                    <crf:Excluded> </crf:Excluded>
                    <Noun>Fox</Noun>
                    <crf:Excluded>!</crf:Excluded>
                </crf:Sequence>
                <crf:Sequence>
                    <Adjective>Lazy</Adjective>
                    <crf:Excluded> </crf:Excluded>
                    <Adjective>Sleeping</Adjective>
                    <Excluded> </Excluded>
                    <Noun>Dog</Noun>
                    <crf:Excluded>!</crf:Excluded>
                </crf:Sequence>
            </crf:Collection>
            """;
    // language=XML
    private static final String READ__NO_RECORDS_XML = "<Collection />";
    // language=XML
    private static final String READ__NO_RECORDS_SCHEMA_XML = """
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema" />
            """;
    // language=XML
    private static final String READ__SINGLE_RECORD_XML = """
            <Collection>
                <Sequence><Adjective>Brown</Adjective> <Noun>Fox</Noun>!</Sequence>
            </Collection>
            """;
    // language=XML
    private static final String READ__SINGLE_RECORD_SCHEMA_XML = """
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema" xmlns="https://coordinatekit.org/crf/schema/tags">
                <crf:Sequence>
                    <Adjective>Brown</Adjective>
                    <crf:Excluded> </crf:Excluded>
                    <Noun>Fox</Noun>
                    <crf:Excluded>!</crf:Excluded>
                </crf:Sequence>
            </crf:Collection>
            """;
    // A classic "billion laughs" document: nested internal entity definitions inside a DOCTYPE.
    // With DTDs disabled the parser must reject it (via the DOCTYPE guard) rather than expand.
    // language=XML
    private static final String READ__BILLION_LAUGHS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE crf:Collection [
                <!ENTITY lol "lol">
                <!ENTITY lol2 "&lol;&lol;&lol;&lol;&lol;">
                <!ENTITY lol3 "&lol2;&lol2;&lol2;&lol2;&lol2;">
            ]>
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema" xmlns="https://example.org/tags">
                <crf:Sequence><Noun>&lol3;</Noun></crf:Sequence>
            </crf:Collection>
            """;
    // An undeclared general entity with no DOCTYPE: with DTDs/external entities disabled it must
    // fail as undeclared rather than expand or resolve (probes the SUPPORT_DTD=false posture
    // directly, surviving a narrowing of the explicit DOCTYPE guard).
    // language=XML
    private static final String READ__UNDECLARED_ENTITY = """
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema" xmlns="https://example.org/tags">
                <crf:Sequence><Noun>&xxe;</Noun></crf:Sequence>
            </crf:Collection>
            """;

    // A hand-authored document may contain an empty <crf:Excluded></crf:Excluded>, which the XSD does
    // not require. A zero-length excluded run carries no characters and is dropped on read.
    @SuppressWarnings("CheckTagEmptyBody")
    // language=XML
    private static final String SINGLE_RECORD_SCHEMA_XML__EMPTY_EXCLUDED = """
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema" xmlns="https://coordinatekit.org/crf/schema/tags">
                <crf:Sequence>
                    <Adjective>Brown</Adjective>
                    <crf:Excluded></crf:Excluded>
                    <crf:Excluded> </crf:Excluded>
                    <Noun>Fox</Noun>
                    <crf:Excluded>!</crf:Excluded>
                </crf:Sequence>
            </crf:Collection>
            """;

    // The XSD will not allow there to be a nested element in `crf:Excluded`. However, a user may not
    // use an XSD and do something unexpected. This facilitates testing that anything within the crf
    // namespace is ignored within a `crf:Sequence` is ignored.
    // language=XML
    private static final String SINGLE_RECORD_SCHEMA_XML__DEEP_EXCLUDED = """
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema" xmlns="https://coordinatekit.org/crf/schema/tags">
                <crf:Sequence>
                    <crf:Excluded>
                        <Adverb>Quick</Adverb>
                    </crf:Excluded>
                    <Adjective>Brown</Adjective>
                    <crf:Excluded> </crf:Excluded>
                    <Noun>Fox</Noun>
                    <crf:Excluded>!</crf:Excluded>
                </crf:Sequence>
            </crf:Collection>
            """;

    // The XSD models tag elements as text-only, but a hand-authored document may nest an element
    // inside one. Such a token run is skipped (logged, not aborted) and the remaining tokens still
    // parse, mirroring the crf:Excluded skip-and-log behavior.
    // language=XML
    private static final String SINGLE_RECORD_SCHEMA_XML__TOKEN_WITH_CHILD = """
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema" xmlns="https://coordinatekit.org/crf/schema/tags">
                <crf:Sequence>
                    <Verb>Jumped<Adverb>quickly</Adverb></Verb>
                    <Adjective>Brown</Adjective>
                    <crf:Excluded> </crf:Excluded>
                    <Noun>Fox</Noun>
                    <crf:Excluded>!</crf:Excluded>
                </crf:Sequence>
            </crf:Collection>
            """;

    @TempDir
    Path temporaryDirectory;

    @Test
    @SuppressWarnings("deprecation")
    void constructor__deprecatedTargetNamespaceOverload__delegatesToBuilder() {
        // ARRANGE //
        StringTagProvider provider = new StringTagProvider(Set.of("Noun"), "Noun");
        String namespace = "https://example.org/tags";
        ByteArrayOutputStream deprecatedOutput = new ByteArrayOutputStream();
        ByteArrayOutputStream builderOutput = new ByteArrayOutputStream();

        // ACT //
        new XmlTrainingData<>(provider, namespace).generateSchema(deprecatedOutput);
        new XmlTrainingData<>(provider, XmlTrainingDataConfiguration.builder().targetNamespace(namespace).build())
                .generateSchema(builderOutput);

        // ASSERT //
        assertArrayEquals(builderOutput.toByteArray(), deprecatedOutput.toByteArray());
    }

    record GenerateSchemaParameters(StringTagProvider tagProvider, @Nullable String targetNamespace, String expected) {}

    static Stream<GenerateSchemaParameters> generateSchema() {
        return Stream.of(
                new GenerateSchemaParameters(
                        new StringTagProvider(Set.of("Noun"), "Noun"),
                        "https://example.org/tags",
                        GENERATE_SCHEMA__NOUN
                ),
                new GenerateSchemaParameters(
                        new StringTagProvider(Set.of("Noun"), "Noun"),
                        null,
                        GENERATE_SCHEMA__NO_NAMESPACE__NOUN
                ),
                new GenerateSchemaParameters(
                        new StringTagProvider(Set.of("Noun"), "Noun"),
                        "  \t ",
                        GENERATE_SCHEMA__NO_NAMESPACE__NOUN
                ),
                new GenerateSchemaParameters(
                        new StringTagProvider(Set.of("Noun", "Verb"), "Noun"),
                        "https://example.org/tags",
                        GENERATE_SCHEMA__NOUN_VERB
                ),
                new GenerateSchemaParameters(
                        new StringTagProvider(Set.of("Adjective", "Noun", "Verb"), "Noun"),
                        "https://example.org/tags",
                        GENERATE_SCHEMA__ADJECTIVE_NOUN_VERB
                ),
                new GenerateSchemaParameters(
                        new StringTagProvider(Set.of("Noun", "Verb"), "Noun"),
                        "https://example.org/my-tags",
                        GENERATE_SCHEMA__NOUN_VERB__DIFFERENT_NAMESPACE
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void generateSchema(GenerateSchemaParameters parameters) {
        // ARRANGE //
        XmlTrainingData<String> data = new XmlTrainingData<>(
                parameters.tagProvider(),
                XmlTrainingDataConfiguration.builder().targetNamespace(parameters.targetNamespace()).build()
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // ACT //
        data.generateSchema(output);

        // ASSERT //
        String schema = output.toString(StandardCharsets.UTF_8);
        assertEquals(parameters.expected(), schema);
        assertDoesNotThrow(
                () -> SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                        .newSchema(new StreamSource(new ByteArrayInputStream(output.toByteArray())))
        );
    }

    @Test
    void generateSchema__emptyTagProvider() {
        // ARRANGE //
        XmlTrainingData<String> data = new XmlTrainingData<>(
                new StringTagProvider(Set.of(), "O"),
                XmlTrainingDataConfiguration.builder().targetNamespace("https://fake.url").build()
        );

        // ACT //
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> data.generateSchema(new ByteArrayOutputStream())
        );

        // ASSERT //
        assertEquals(emptyTagProviderMessage(StringTagProvider.class), exception.getMessage());
    }

    @Test
    void read__bareTagsNamespaceExcludedDropped() {
        // The second sequence contains a bare <Excluded> </Excluded> in the tags (default) namespace,
        // which is not crf-namespaced and is therefore treated as a trimmed, dropped-if-empty tag element.
        try (var sequences = DATA
                .read(new ByteArrayInputStream(READ__MULTIPLE_RECORDS_SCHEMA_XML.getBytes(StandardCharsets.UTF_8)))) {
            TrainingSequence<String> second = sequences.toList().get(1);

            assertLazySleepingDog(second);
            assertEquals("Lazy SleepingDog!", second.surface());
        }
    }

    record ReadSegmentsParameters(
            String name,
            String xml,
            List<SegmentKind> expectedKinds,
            List<String> expectedTexts,
            String expectedSurface
    ) {}

    static Stream<ReadSegmentsParameters> read__capturesSegments() {
        List<SegmentKind> brownFoxKinds = List
                .of(SegmentKind.TOKEN, SegmentKind.EXCLUDED, SegmentKind.TOKEN, SegmentKind.EXCLUDED);
        List<String> brownFoxTexts = List.of("Brown", " ", "Fox", "!");
        return Stream.of(
                new ReadSegmentsParameters(
                        "captures_excluded_runs_as_segments",
                        READ__SINGLE_RECORD_SCHEMA_XML,
                        brownFoxKinds,
                        brownFoxTexts,
                        "Brown Fox!"
                ),
                // An empty <crf:Excluded></crf:Excluded> carries no characters; the zero-length run is
                // dropped, so it produces no EXCLUDED segment and does not affect the surface.
                new ReadSegmentsParameters(
                        "empty_excluded_run_dropped",
                        SINGLE_RECORD_SCHEMA_XML__EMPTY_EXCLUDED,
                        brownFoxKinds,
                        brownFoxTexts,
                        "Brown Fox!"
                ),
                // A crf:Excluded element containing a child element is skipped entirely; its run is not
                // captured, so it does not appear in the reconstructed surface.
                new ReadSegmentsParameters(
                        "deep_excluded_ignored_but_tokens_captured",
                        SINGLE_RECORD_SCHEMA_XML__DEEP_EXCLUDED,
                        brownFoxKinds,
                        brownFoxTexts,
                        "Brown Fox!"
                ),
                // A token element containing a nested child element is skipped (not aborted); the
                // remaining tokens and excluded runs still parse, so the brown-fox shape is preserved.
                new ReadSegmentsParameters(
                        "token_with_child_skipped_but_rest_captured",
                        SINGLE_RECORD_SCHEMA_XML__TOKEN_WITH_CHILD,
                        brownFoxKinds,
                        brownFoxTexts,
                        "Brown Fox!"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void read__capturesSegments(ReadSegmentsParameters parameters) {
        try (var sequences = DATA.read(new ByteArrayInputStream(parameters.xml().getBytes(StandardCharsets.UTF_8)))) {
            TrainingSequence<String> sequence = sequences.toList().getFirst();

            assertBrownFox(sequence);
            assertEquals(parameters.expectedSurface(), sequence.surface());
            assertEquals(parameters.expectedKinds(), sequence.segments().stream().map(TrainingSegment::kind).toList());
            assertEquals(parameters.expectedTexts(), sequence.segments().stream().map(TrainingSegment::text).toList());
        }
    }

    record ReadRejectsParameters(String name, String xml, String expectedMessageSubstring) {}

    static Stream<ReadRejectsParameters> read__rejects() {
        return Stream.of(
                new ReadRejectsParameters("doctype", DOCTYPE_DOCUMENT, "DOCTYPE"),
                new ReadRejectsParameters("billionLaughs", READ__BILLION_LAUGHS, "DOCTYPE"),
                new ReadRejectsParameters("undeclaredEntity", READ__UNDECLARED_ENTITY, "xxe")
        );
    }

    @MethodSource
    @ParameterizedTest
    void read__rejects(ReadRejectsParameters parameters) {
        // ACT //
        UncheckedCrfException exception = assertThrows(UncheckedCrfException.class, () -> {
            try (var sequences = DATA
                    .read(new ByteArrayInputStream(parameters.xml().getBytes(StandardCharsets.UTF_8)))) {
                sequences.toList();
            }
        });

        // ASSERT //
        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(
                message.contains(parameters.expectedMessageSubstring()),
                "Expected message to contain '" + parameters.expectedMessageSubstring() + "' but was: " + message
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {READ__MULTIPLE_RECORDS_XML, READ__MULTIPLE_RECORDS_SCHEMA_XML})
    void read_fromPath(String xml) throws IOException {
        Path xmlFile = temporaryDirectory.resolve("training.xml");
        Files.writeString(xmlFile, xml);

        try (var sequences = DATA.read(xmlFile)) {
            List<TrainingSequence<String>> actual = sequences.toList();

            assertEquals(2, actual.size());
            assertBrownFox(actual.get(0));
            assertLazySleepingDog(actual.get(1));
        }
    }

    @Test
    void read_iteratorExhausted() {
        try (var sequences = DATA
                .read(new ByteArrayInputStream(READ__SINGLE_RECORD_XML.getBytes(StandardCharsets.UTF_8)))) {
            Iterator<TrainingSequence<String>> actual = sequences.iterator();

            assertBrownFox(actual.next());
            assertFalse(actual.hasNext());
            assertThrows(NoSuchElementException.class, actual::next);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {READ__MULTIPLE_RECORDS_XML, READ__MULTIPLE_RECORDS_SCHEMA_XML})
    void read_multipleRecord(String xml) {
        try (var sequences = DATA.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))) {
            List<TrainingSequence<String>> actual = sequences.toList();

            assertEquals(2, actual.size());
            assertBrownFox(actual.get(0));
            assertLazySleepingDog(actual.get(1));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {READ__NO_RECORDS_XML, READ__NO_RECORDS_SCHEMA_XML})
    void read_noRecords(String xml) {
        try (var sequences = DATA.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))) {
            assertTrue(sequences.findAny().isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {READ__SINGLE_RECORD_XML, READ__SINGLE_RECORD_SCHEMA_XML})
    void read_singleRecord(String xml) {
        try (var sequences = DATA.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))) {
            List<TrainingSequence<String>> actual = sequences.toList();

            assertEquals(1, actual.size());
            assertBrownFox(actual.getFirst());
        }
    }
}
