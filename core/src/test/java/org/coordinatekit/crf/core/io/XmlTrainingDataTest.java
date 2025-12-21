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
import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlTrainingDataTest {
    private static final XmlTrainingData<String> DATA = new XmlTrainingData<>(new StringTagProvider("0"));

    // language=XML
    private static final String GENERATE_SCHEMA__ADJECTIVE_NOUN_VERB = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="https://example.org/tags" elementFormDefault="qualified">
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
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="https://example.org/tags" elementFormDefault="qualified">
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
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="https://example.org/tags" elementFormDefault="qualified">
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
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="https://example.org/my-tags" elementFormDefault="qualified">
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

    @TempDir
    Path temporaryDirectory;

    record GenerateSchemaParameters(StringTagProvider tagProvider, String targetNamespace, String expected) {}

    static Stream<GenerateSchemaParameters> generateSchema() {
        return Stream.of(
                new GenerateSchemaParameters(
                        new StringTagProvider(Set.of("Noun"), "Noun"),
                        "https://example.org/tags",
                        GENERATE_SCHEMA__NOUN
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
        XmlTrainingData<String> data = new XmlTrainingData<>(parameters.tagProvider(), parameters.targetNamespace());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // ACT //
        data.generateSchema(output);

        // ASSERT //
        String schema = output.toString(StandardCharsets.UTF_8);
        assertEquals(parameters.expected(), schema);
    }

    @Test
    void generateSchema__containsTargetNamespace() {
        StringTagProvider tagProvider = new StringTagProvider(Set.of("Noun"), "Noun");
        XmlTrainingData<String> data = new XmlTrainingData<>(tagProvider, "https://example.org/my-tags");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        data.generateSchema(output);

        String schema = output.toString(StandardCharsets.UTF_8);
        assertTrue(schema.contains("targetNamespace=\"https://example.org/my-tags\""));
    }

    record GenerateSchemaExceptionParameters(
            StringTagProvider tagProvider,
            @Nullable String targetNamespace,
            Class<? extends RuntimeException> expectedException,
            String expectedMessage
    ) {}

    static Stream<GenerateSchemaExceptionParameters> generateSchema_exception() {
        return Stream.of(
                new GenerateSchemaExceptionParameters(
                        new StringTagProvider(Set.of(), "O"),
                        "https://fake.url",
                        IllegalStateException.class,
                        "The tag provider must contain at least one tag. "
                                + "This can be accomplished by ensuring `tags()` returns a value on `"
                                + StringTagProvider.class.getName() + "`."
                ),
                new GenerateSchemaExceptionParameters(
                        new StringTagProvider(Set.of("Noun", "Verb", "O"), "O"),
                        null,
                        IllegalStateException.class,
                        "A target namespace must be specified to generate a schema. "
                                + "This can be accomplished by setting the `targetNamespace` parameter on `"
                                + XmlTrainingData.class.getName() + "`."
                ),
                new GenerateSchemaExceptionParameters(
                        new StringTagProvider(Set.of("Noun", "Verb", "O"), "O"),
                        "",
                        IllegalStateException.class,
                        "A non-blank target namespace (`\"\"`) must be specified to generate a schema. "
                                + "This can be accomplished by setting the `targetNamespace` parameter on `"
                                + XmlTrainingData.class.getName() + "`."
                ),
                new GenerateSchemaExceptionParameters(
                        new StringTagProvider(Set.of("Noun", "Verb", "O"), "O"),
                        "  \t ",
                        IllegalStateException.class,
                        "A non-blank target namespace (`\"  \t \"`) must be specified to generate a schema. "
                                + "This can be accomplished by setting the `targetNamespace` parameter on `"
                                + XmlTrainingData.class.getName() + "`."
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void generateSchema_exception(GenerateSchemaExceptionParameters parameters) {
        XmlTrainingData<String> data = new XmlTrainingData<>(parameters.tagProvider(), parameters.targetNamespace());

        RuntimeException exception = assertThrows(
                parameters.expectedException(),
                () -> data.generateSchema(new ByteArrayOutputStream())
        );
        assertEquals(parameters.expectedMessage(), exception.getMessage());
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
    @ValueSource(strings = {READ__SINGLE_RECORD_XML, READ__SINGLE_RECORD_SCHEMA_XML,
                    SINGLE_RECORD_SCHEMA_XML__DEEP_EXCLUDED})
    void read_singleRecord(String xml) {
        try (var sequences = DATA.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))) {
            List<TrainingSequence<String>> actual = sequences.toList();

            assertEquals(1, actual.size());
            assertBrownFox(actual.getFirst());
        }
    }

    static void assertBrownFox(TrainingSequence<String> sequence) {
        assertEquals(2, sequence.size());
        assertEquals("Adjective", sequence.get(0).tag());
        assertEquals("Brown", sequence.get(0).token());
        assertEquals("Noun", sequence.get(1).tag());
        assertEquals("Fox", sequence.get(1).token());
    }

    static void assertLazySleepingDog(TrainingSequence<String> sequence) {
        assertEquals(3, sequence.size());
        assertEquals("Adjective", sequence.get(0).tag());
        assertEquals("Lazy", sequence.get(0).token());
        assertEquals("Adjective", sequence.get(1).tag());
        assertEquals("Sleeping", sequence.get(1).token());
        assertEquals("Noun", sequence.get(2).tag());
        assertEquals("Dog", sequence.get(2).token());
    }
}
