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
import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.coordinatekit.crf.core.io.TrainingSequenceFixtures.assertBrownFox;
import static org.coordinatekit.crf.core.io.TrainingSequenceFixtures.assertLazySleepingDog;
import static org.coordinatekit.crf.core.io.TrainingSequenceFixtures.brownFox;
import static org.coordinatekit.crf.core.io.TrainingSequenceFixtures.brownFoxWithExcluded;
import static org.coordinatekit.crf.core.io.TrainingSequenceFixtures.emptyTagProviderMessage;
import static org.coordinatekit.crf.core.io.TrainingSequenceFixtures.lazySleepingDog;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlTrainingDataValidationTest {
    private static final String TAGS_NAMESPACE = "https://example.org/tags";

    // A library-shaped document with tags in the default (tag) namespace and a crf:Excluded run.
    // language=XML
    private static final String VALID_DOCUMENT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema" xmlns="https://example.org/tags">
                <crf:Sequence><Adjective>Brown</Adjective><crf:Excluded> </crf:Excluded><Noun>Fox</Noun><crf:Excluded>!</crf:Excluded></crf:Sequence>
            </crf:Collection>
            """;

    // A tag element with no declaration in the tag schema; the strict wildcard rejects it.
    // language=XML
    private static final String INVALID__UNKNOWN_TAG = """
            <?xml version="1.0" encoding="UTF-8"?>
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema" xmlns="https://example.org/tags">
                <crf:Sequence><Pronoun>It</Pronoun></crf:Sequence>
            </crf:Collection>
            """;

    // A tag element directly under the root, where the structure permits only crf:Sequence.
    // language=XML
    private static final String INVALID__TAG_UNDER_ROOT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema" xmlns="https://example.org/tags">
                <Adjective>Brown</Adjective>
            </crf:Collection>
            """;

    // A crf:Excluded run directly under the root, where the structure permits only crf:Sequence.
    // language=XML
    private static final String INVALID__EXCLUDED_UNDER_ROOT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema" xmlns="https://example.org/tags">
                <crf:Excluded> </crf:Excluded>
            </crf:Collection>
            """;

    // A crf:Excluded run containing a child element; its xs:string type forbids element content.
    // language=XML
    private static final String INVALID__EXCLUDED_WITH_CHILD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema" xmlns="https://example.org/tags">
                <crf:Sequence><crf:Excluded><Adjective>Brown</Adjective></crf:Excluded></crf:Sequence>
            </crf:Collection>
            """;

    // A single sequence with two distinct undeclared tags; the validator should report both, not just
    // the first.
    // language=XML
    private static final String INVALID__MULTIPLE_UNKNOWN_TAGS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema" xmlns="https://example.org/tags">
                <crf:Sequence><Pronoun>It</Pronoun><Conjunction>and</Conjunction></crf:Sequence>
            </crf:Collection>
            """;

    // A non-well-formed document: the crf:Sequence start tag is never closed.
    // language=XML
    private static final String INVALID__MALFORMED = """
            <?xml version="1.0" encoding="UTF-8"?>
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema" xmlns="https://example.org/tags">
                <crf:Sequence><Noun>Fox</Noun>
            </crf:Collection>
            """;

    // A document carrying a DOCTYPE declaration; with DTDs disabled the validator must reject it.
    // language=XML
    private static final String INVALID__WITH_DOCTYPE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE crf:Collection>
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema" xmlns="https://example.org/tags">
                <crf:Sequence><Noun>Fox</Noun></crf:Sequence>
            </crf:Collection>
            """;

    // An empty collection: the structural grammar permits zero sequences (minOccurs="0").
    // language=XML
    private static final String VALID_EMPTY_COLLECTION = """
            <?xml version="1.0" encoding="UTF-8"?>
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema" xmlns="https://example.org/tags"/>
            """;

    // Tag elements left in no namespace, validated by a namespace-configured instance whose tag schema
    // declares the tags in the target namespace; the lax no-namespace wildcard finds no matching
    // declaration and skips them, so the document validates.
    // language=XML
    private static final String VALID_NO_NAMESPACE_TAGS_UNDER_NAMESPACE_VALIDATOR = """
            <?xml version="1.0" encoding="UTF-8"?>
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema">
                <crf:Sequence><Adjective>Brown</Adjective><Noun>Fox</Noun></crf:Sequence>
            </crf:Collection>
            """;

    // A no-namespace document: tags carry no namespace (no default xmlns), matching what the writer
    // emits when no target namespace is configured.
    // language=XML
    private static final String VALID_NO_NAMESPACE_DOCUMENT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema">
                <crf:Sequence><Adjective>Brown</Adjective><crf:Excluded> </crf:Excluded><Noun>Fox</Noun><crf:Excluded>!</crf:Excluded></crf:Sequence>
            </crf:Collection>
            """;

    // A no-namespace document carrying a tag with no declaration; the lax wildcard treats the
    // no-namespace path as an open vocabulary, so it validates (the user's bare-tag case).
    // language=XML
    private static final String VALID_NO_NAMESPACE_UNDECLARED_TAG = """
            <?xml version="1.0" encoding="UTF-8"?>
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema">
                <crf:Sequence><Pronoun>It</Pronoun></crf:Sequence>
            </crf:Collection>
            """;

    @TempDir
    Path temporaryDirectory;

    private static XmlTrainingData<String> configuredData() {
        return new XmlTrainingData<>(
                new StringTagProvider(Set.of("Adjective", "Noun"), "Noun"),
                XmlTrainingDataConfiguration.builder().targetNamespace(TAGS_NAMESPACE).build()
        );
    }

    private static XmlTrainingData<String> noNamespaceData() {
        return new XmlTrainingData<>(new StringTagProvider(Set.of("Adjective", "Noun"), "Noun"));
    }

    private static ByteArrayInputStream inputStream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void validate__appendedFileValidates() throws IOException {
        // ARRANGE //
        XmlTrainingData<String> data = configuredData();
        Path file = temporaryDirectory.resolve("training.xml");
        try (var writer = data.appendingWriter(file)) {
            writer.write(brownFox());
        }
        try (var writer = data.appendingWriter(file)) {
            writer.write(lazySleepingDog());
        }

        // ACT & ASSERT //
        assertDoesNotThrow(() -> data.validate(file));
    }

    @Test
    void validate__customRootNameValidates() throws IOException {
        // ARRANGE //
        XmlTrainingData<String> data = new XmlTrainingData<>(
                new StringTagProvider(Set.of("Adjective", "Noun"), "Noun"),
                XmlTrainingDataConfiguration.builder().rootElementName("AddressCollection")
                        .targetNamespace(TAGS_NAMESPACE).build()
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (var writer = data.writer(output)) {
            writer.write(brownFox());
        }

        // ACT //
        String emitted = output.toString(StandardCharsets.UTF_8);

        // ASSERT //
        assertTrue(emitted.contains("<crf:AddressCollection"), "Root should use the custom name: " + emitted);
        assertDoesNotThrow(() -> data.validate(new ByteArrayInputStream(output.toByteArray())));
    }

    @Test
    void validate__emptyTagProvider() {
        // ARRANGE //
        XmlTrainingData<String> data = new XmlTrainingData<>(
                new StringTagProvider(Set.of(), "O"),
                XmlTrainingDataConfiguration.builder().targetNamespace(TAGS_NAMESPACE).build()
        );

        // ACT //
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> data.validate(new ByteArrayInputStream(new byte[0]))
        );

        // ASSERT //
        assertEquals(emptyTagProviderMessage(StringTagProvider.class), exception.getMessage());
    }

    record InvalidDocumentParameters(
            String name,
            Supplier<XmlTrainingData<String>> data,
            String xml,
            String expectedElement
    ) {}

    static Stream<InvalidDocumentParameters> validate__invalidDocument() {
        return Stream.of(
                new InvalidDocumentParameters(
                        "unknown_tag",
                        XmlTrainingDataValidationTest::configuredData,
                        INVALID__UNKNOWN_TAG,
                        "Pronoun"
                ),
                new InvalidDocumentParameters(
                        "tag_under_root",
                        XmlTrainingDataValidationTest::configuredData,
                        INVALID__TAG_UNDER_ROOT,
                        "Adjective"
                ),
                new InvalidDocumentParameters(
                        "excluded_under_root",
                        XmlTrainingDataValidationTest::configuredData,
                        INVALID__EXCLUDED_UNDER_ROOT,
                        "Excluded"
                ),
                new InvalidDocumentParameters(
                        "excluded_with_child",
                        XmlTrainingDataValidationTest::configuredData,
                        INVALID__EXCLUDED_WITH_CHILD,
                        "Excluded"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void validate__invalidDocument(InvalidDocumentParameters parameters) {
        // ARRANGE //
        XmlTrainingData<String> data = parameters.data().get();

        // ACT //
        UncheckedCrfException exception = assertThrows(
                UncheckedCrfException.class,
                () -> data.validate(inputStream(parameters.xml()))
        );

        // ASSERT //
        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(
                message.contains(parameters.expectedElement()),
                "Expected the offending element '" + parameters.expectedElement() + "' in the message but was: "
                        + message
        );
    }

    @Test
    void validate__malformedDocument() {
        // ARRANGE //
        XmlTrainingData<String> data = configuredData();

        // ACT //
        UncheckedCrfException exception = assertThrows(
                UncheckedCrfException.class,
                () -> data.validate(inputStream(INVALID__MALFORMED))
        );

        // ASSERT //
        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(
                message.contains("line "),
                "A malformed document should report the parse location but was: " + message
        );
    }

    @Test
    void validate__multipleErrorsAreAllReported() {
        // ARRANGE //
        XmlTrainingData<String> data = configuredData();

        // ACT //
        UncheckedCrfException exception = assertThrows(
                UncheckedCrfException.class,
                () -> data.validate(inputStream(INVALID__MULTIPLE_UNKNOWN_TAGS))
        );

        // ASSERT //
        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(
                message.contains("Pronoun") && message.contains("Conjunction"),
                "Both undeclared tags should be reported but was: " + message
        );
    }

    @Test
    void validate__rejectsDoctype() {
        // ARRANGE //
        XmlTrainingData<String> data = configuredData();

        // ACT //
        UncheckedCrfException exception = assertThrows(
                UncheckedCrfException.class,
                () -> data.validate(inputStream(INVALID__WITH_DOCTYPE))
        );

        // ASSERT //
        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(
                message.toLowerCase(Locale.ROOT).contains("doctype"),
                "Rejection should cite the DOCTYPE restriction but was: " + message
        );
    }

    record ValidDocumentParameters(String name, Supplier<XmlTrainingData<String>> data, String xml) {}

    static Stream<ValidDocumentParameters> validate__validDocument() {
        return Stream.of(
                new ValidDocumentParameters(
                        "empty_collection",
                        XmlTrainingDataValidationTest::configuredData,
                        VALID_EMPTY_COLLECTION
                ),
                new ValidDocumentParameters(
                        "with_namespace",
                        XmlTrainingDataValidationTest::configuredData,
                        VALID_DOCUMENT
                ),
                new ValidDocumentParameters(
                        "no_namespace",
                        XmlTrainingDataValidationTest::noNamespaceData,
                        VALID_NO_NAMESPACE_DOCUMENT
                ),
                new ValidDocumentParameters(
                        "no_namespace_undeclared_tag",
                        XmlTrainingDataValidationTest::noNamespaceData,
                        VALID_NO_NAMESPACE_UNDECLARED_TAG
                ),
                new ValidDocumentParameters(
                        "no_namespace_tags_under_namespace_validator",
                        XmlTrainingDataValidationTest::configuredData,
                        VALID_NO_NAMESPACE_TAGS_UNDER_NAMESPACE_VALIDATOR
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void validate__validDocument(ValidDocumentParameters parameters) {
        // ARRANGE //
        XmlTrainingData<String> data = parameters.data().get();

        // ACT & ASSERT //
        assertDoesNotThrow(() -> data.validate(inputStream(parameters.xml())));
    }

    record WrittenRoundTripParameters(
            String name,
            Supplier<XmlTrainingData<String>> data,
            boolean expectsDefaultNamespace
    ) {}

    static Stream<WrittenRoundTripParameters> validate__writtenDocumentValidates() {
        return Stream.of(
                new WrittenRoundTripParameters("with_namespace", XmlTrainingDataValidationTest::configuredData, true),
                new WrittenRoundTripParameters("no_namespace", XmlTrainingDataValidationTest::noNamespaceData, false)
        );
    }

    @MethodSource
    @ParameterizedTest
    void validate__writtenDocumentValidates(WrittenRoundTripParameters parameters) throws IOException {
        // ARRANGE //
        XmlTrainingData<String> data = parameters.data().get();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (var writer = data.writer(output)) {
            writer.write(brownFox());
            writer.write(lazySleepingDog());
            writer.write(brownFoxWithExcluded());
        }
        String emitted = output.toString(StandardCharsets.UTF_8);

        // ASSERT //
        if (parameters.expectsDefaultNamespace()) {
            assertTrue(
                    emitted.contains("xmlns=\"" + TAGS_NAMESPACE + "\""),
                    "Namespace-mode output must declare the default namespace: " + emitted
            );
        } else {
            assertFalse(
                    emitted.contains("xmlns=\""),
                    "No-namespace output must not declare a default namespace: " + emitted
            );
        }
        assertDoesNotThrow(() -> data.validate(inputStream(emitted)));
        try (var sequences = data.read(inputStream(emitted))) {
            List<TrainingSequence<String>> actual = sequences.toList();
            assertEquals(3, actual.size());
            assertBrownFox(actual.get(0));
            assertLazySleepingDog(actual.get(1));
            assertBrownFox(actual.get(2));
        }
    }
}
