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
import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.preprocessing.TrainingSegment;
import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingConsumer;
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
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.coordinatekit.crf.core.io.TrainingSequenceFixtures.assertBrownFox;
import static org.coordinatekit.crf.core.io.TrainingSequenceFixtures.assertContainsBrownFoxThenLazyDog;
import static org.coordinatekit.crf.core.io.TrainingSequenceFixtures.assertLazySleepingDog;
import static org.coordinatekit.crf.core.io.TrainingSequenceFixtures.brownFox;
import static org.coordinatekit.crf.core.io.TrainingSequenceFixtures.lazySleepingDog;
import static org.coordinatekit.crf.core.preprocessing.TrainingSegments.excluded;
import static org.coordinatekit.crf.core.preprocessing.TrainingSegments.token;
import static org.junit.jupiter.api.Assertions.*;

class XmlTrainingDataWriterTest {
    private static final XmlTrainingData<String> DATA = new XmlTrainingData<>(new StringTagProvider("0"));

    private static final String MALFORMED_XML = "<crf:Collection xmlns:crf=\"unclosed";

    // language=XML
    private static final String NON_COLLECTION_ROOT = """
            <crf:TrainingData xmlns:crf="https://coordinatekit.org/crf/schema">
            </crf:TrainingData>
            """;

    // language=XML
    private static final String VALID_EMPTY_COLLECTION = """
            <?xml version="1.0" encoding="UTF-8"?>
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema">
            </crf:Collection>
            """;

    // language=XML
    private static final String WRONG_NAMESPACE_ROOT = """
            <Collection xmlns="https://example.org/other">
            </Collection>
            """;

    @TempDir
    Path temporaryDirectory;

    record AfterCloseExceptionParameters(
            String name,
            ThrowingConsumer<TrainingSequenceWriter<String>> action,
            String expectedMessage
    ) {}

    static Stream<AfterCloseExceptionParameters> afterClose__exception() {
        return Stream.of(
                new AfterCloseExceptionParameters("flush", TrainingSequenceWriter::flush, "Cannot flush after close."),
                new AfterCloseExceptionParameters(
                        "write",
                        writer -> writer.write(brownFox()),
                        "Cannot write after close."
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void afterClose__exception(AfterCloseExceptionParameters parameters) throws IOException {
        // ARRANGE //
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TrainingSequenceWriter<String> writer = DATA.writer(output);
        writer.close();

        // ACT //
        IOException exception = assertThrows(IOException.class, () -> parameters.action().accept(writer));

        // ASSERT //
        assertEquals(parameters.expectedMessage(), exception.getMessage());
    }

    @Test
    void appendingWriter__customRootElementName() throws IOException {
        // ARRANGE //
        XmlTrainingData<String> customData = withRoot("TrainingData");
        Path file = temporaryDirectory.resolve("training.xml");

        // ACT //
        try (var writer = customData.appendingWriter(file)) {
            writer.write(brownFox());
        }
        try (var writer = customData.appendingWriter(file)) {
            writer.write(lazySleepingDog());
        }

        // ASSERT //
        String emitted = Files.readString(file);
        assertTrue(
                emitted.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<crf:TrainingData"),
                "File should begin with XML prolog and configured root open tag: " + emitted
        );
        assertTrue(
                emitted.endsWith("</crf:TrainingData>\n"),
                "File should end with configured root close tag: " + emitted
        );
        assertContainsBrownFoxThenLazyDog(file, customData);
    }

    @Test
    void appendingWriter__declaresTargetNamespaceAsDefault() throws IOException {
        // ARRANGE //
        XmlTrainingData<String> data = new XmlTrainingData<>(
                new StringTagProvider("0"),
                XmlTrainingDataConfiguration.builder().targetNamespace("https://example.org/tags").build()
        );
        Path file = temporaryDirectory.resolve("training.xml");

        // ACT //
        try (var writer = data.appendingWriter(file)) {
            writer.write(brownFox());
        }

        // ASSERT //
        assertTrue(
                Files.readString(file).contains("xmlns=\"https://example.org/tags\""),
                "Configured appending writer should declare the target namespace as the default"
        );
    }

    record AppendingWriterExceptionParameters(String name, String existingContent, String expectedMessageSubstring) {}

    static Stream<AppendingWriterExceptionParameters> appendingWriter__exception() {
        return Stream.of(
                new AppendingWriterExceptionParameters("malformedXml", MALFORMED_XML, "malformed XML"),
                new AppendingWriterExceptionParameters("wrongRootLocalName", NON_COLLECTION_ROOT, "expected root"),
                new AppendingWriterExceptionParameters("wrongNamespace", WRONG_NAMESPACE_ROOT, "expected root")
        );
    }

    @MethodSource
    @ParameterizedTest
    void appendingWriter__exception(AppendingWriterExceptionParameters parameters) throws IOException {
        // ARRANGE //
        Path file = temporaryDirectory.resolve("training.xml");
        Files.writeString(file, parameters.existingContent());
        String originalContent = Files.readString(file);

        // ACT //
        IOException exception = assertThrows(IOException.class, () -> {
            try (var writer = DATA.appendingWriter(file)) {
                assertNotNull(writer);
                fail();
            }
        });

        // ASSERT //
        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(
                message.contains(parameters.expectedMessageSubstring()),
                "Expected message to contain '" + parameters.expectedMessageSubstring() + "' but was: " + message
        );
        assertEquals(originalContent, Files.readString(file), "File should be unchanged after rejection");

        // ACT (recovery) //
        Files.writeString(file, VALID_EMPTY_COLLECTION);
        try (var writer = DATA.appendingWriter(file)) {
            writer.write(brownFox());
        }

        // ASSERT (recovery) //
        try (var sequences = DATA.read(file)) {
            List<TrainingSequence<String>> actual = sequences.toList();
            assertEquals(1, actual.size());
            assertBrownFox(actual.getFirst());
        }
    }

    record AppendHappyPathParameters(String name, ThrowingConsumer<Path> seed) {}

    static Stream<AppendHappyPathParameters> appendingWriter__happyPath() {
        return Stream.of(new AppendHappyPathParameters("cleanCloseTag", file -> {
            try (var writer = DATA.appendingWriter(file)) {
                writer.write(brownFox());
            }
        }), new AppendHappyPathParameters("chunkBoundaryStraddle", file -> {
            try (var writer = DATA.appendingWriter(file)) {
                writer.write(brownFox());
            }
            // Place the chunk boundary mid-needle so the test would fail if the chunk-overlap
            // mechanism in findLastCloseTagOffset were dropped. The close tag occupies the 18
            // bytes ending at fileSize - 1 (the trailing newline). Padding by 8182 spaces makes
            // the first chunk start at oldSize - 10, splitting the close tag in half.
            Files.writeString(file, Files.readString(file) + " ".repeat(8182));
        }), new AppendHappyPathParameters("multiChunkSearch", file -> {
            try (var writer = DATA.appendingWriter(file)) {
                writer.write(brownFox());
            }
            Files.writeString(file, Files.readString(file) + " ".repeat(8300));
        }), new AppendHappyPathParameters("trailingWhitespace", file -> {
            try (var writer = DATA.appendingWriter(file)) {
                writer.write(brownFox());
            }
            Files.writeString(file, Files.readString(file) + "\n   \n  ");
        }), new AppendHappyPathParameters("missingCloseTag", file -> Files.writeString(file, """
                <?xml version="1.0" encoding="UTF-8"?>
                <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema">
                    <crf:Sequence><Adjective>Brown</Adjective><Noun>Fox</Noun></crf:Sequence>
                """)));
    }

    @MethodSource
    @ParameterizedTest
    void appendingWriter__happyPath(AppendHappyPathParameters parameters) throws Throwable {
        // ARRANGE //
        Path file = temporaryDirectory.resolve("training.xml");
        parameters.seed().accept(file);

        // ACT //
        try (var writer = DATA.appendingWriter(file)) {
            writer.write(lazySleepingDog());
        }

        // ASSERT //
        assertContainsBrownFoxThenLazyDog(file, DATA);
    }

    @Test
    void appendingWriter__noWrites() throws IOException {
        // ARRANGE //
        Path file = temporaryDirectory.resolve("training.xml");

        // ACT //
        try (var writer = DATA.appendingWriter(file)) {
            assertNotNull(writer);
        }

        // ASSERT //
        String emitted = Files.readString(file);
        assertTrue(emitted.contains("<crf:Collection"), "File should contain root open tag: " + emitted);
        assertTrue(emitted.contains("</crf:Collection>"), "File should contain root close tag: " + emitted);
        try (var sequences = DATA.read(file)) {
            assertTrue(sequences.findAny().isEmpty());
        }
    }

    @ParameterizedTest(name = "preCreate={0}")
    @ValueSource(booleans = {true, false})
    void appendingWriter__singleSession__freshFile(boolean preCreate) throws IOException {
        // ARRANGE //
        Path file = temporaryDirectory.resolve("training.xml");
        if (preCreate) {
            Files.createFile(file);
        }

        // ACT //
        try (var writer = DATA.appendingWriter(file)) {
            writer.write(brownFox());
            writer.write(lazySleepingDog());
        }

        // ASSERT //
        assertContainsBrownFoxThenLazyDog(file, DATA);
    }

    @Test
    void close__idempotent() throws IOException {
        // ARRANGE //
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TrainingSequenceWriter<String> writer = DATA.writer(output);
        writer.write(brownFox());

        // ACT //
        writer.close();
        byte[] snapshot = output.toByteArray();
        assertDoesNotThrow(writer::close);

        // ASSERT //
        assertArrayEquals(snapshot, output.toByteArray());
    }

    @Test
    void write__exceptionWhenTagEncodesToNull() throws IOException {
        // ARRANGE //
        XmlTrainingData<String> data = new XmlTrainingData<>(new NullEncodingTagProvider("Noun"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TrainingSequenceWriter<String> writer = data.writer(output);

        // ACT //
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> writer.write(brownFox())
        );

        // ASSERT //
        assertEquals("Tag 'Noun' encodes to null and cannot be serialized.", exception.getMessage());
        assertDoesNotThrow(writer::close);
    }

    @Test
    void writer__customRootElementName() throws IOException {
        // ARRANGE //
        XmlTrainingData<String> customData = withRoot("Sequences");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // ACT //
        try (var writer = customData.writer(output)) {
            writer.write(brownFox());
        }

        // ASSERT //
        String emitted = output.toString(StandardCharsets.UTF_8);
        assertTrue(
                emitted.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<crf:Sequences"),
                "Output should begin with XML prolog and configured root open tag: " + emitted
        );
        assertTrue(
                emitted.endsWith("</crf:Sequences>\n"),
                "Output should end with configured root close tag: " + emitted
        );

        try (var sequences = DATA.read(new ByteArrayInputStream(output.toByteArray()))) {
            List<TrainingSequence<String>> actual = sequences.toList();
            assertEquals(1, actual.size());
            assertBrownFox(actual.getFirst());
        }
    }

    @Test
    void writer__declaresTargetNamespaceAsDefault() throws IOException {
        // ARRANGE //
        XmlTrainingData<String> data = new XmlTrainingData<>(
                new StringTagProvider("0"),
                XmlTrainingDataConfiguration.builder().targetNamespace("https://example.org/tags").build()
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // ACT //
        try (var writer = data.writer(output)) {
            writer.write(brownFox());
        }

        // ASSERT //
        String emitted = output.toString(StandardCharsets.UTF_8);
        assertTrue(
                emitted.contains("xmlns=\"https://example.org/tags\""),
                "Configured writer should declare the target namespace as the default: " + emitted
        );
    }

    @Test
    void writer__excludedRunsRoundTripLosslessly() throws IOException {
        // ARRANGE //
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TrainingSequence<String> sequence = TrainingSequence.ofSegments(
                List.of(
                        excluded("  "),
                        token("Adjective", "Brown"),
                        excluded("  "),
                        token("Noun", "Fox"),
                        excluded(" .")
                )
        );

        // ACT //
        try (var writer = DATA.writer(output)) {
            writer.write(sequence);
        }

        // ASSERT //
        try (var sequences = DATA.read(new ByteArrayInputStream(output.toByteArray()))) {
            TrainingSequence<String> read = sequences.toList().getFirst();
            assertEquals(sequence.surface(), read.surface());
            assertEquals("  Brown  Fox .", read.surface());
            assertEquals(
                    sequence.segments().stream().map(TrainingSegment::text).toList(),
                    read.segments().stream().map(TrainingSegment::text).toList()
            );
        }
    }

    @Test
    void writer__escapesSpecialCharacters() throws IOException {
        // ARRANGE //
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TrainingSequence<String> sequence = TrainingSequence
                .ofTokens(List.of("a<b", "c&d", "e>f"), List.of("Symbol", "Symbol", "Symbol"));

        // ACT //
        try (var writer = DATA.writer(output)) {
            writer.write(sequence);
        }

        // ASSERT //
        try (var sequences = DATA.read(new ByteArrayInputStream(output.toByteArray()))) {
            List<TrainingSequence<String>> actual = sequences.toList();
            assertEquals(1, actual.size());
            TrainingSequence<String> read = actual.getFirst();
            assertEquals(3, read.size());
            assertEquals("a<b", read.get(0).token());
            assertEquals("c&d", read.get(1).token());
            assertEquals("e>f", read.get(2).token());
        }
    }

    @Test
    void writer__flush() throws IOException {
        // ARRANGE //
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // ACT //
        try (var writer = DATA.writer(output)) {
            writer.write(brownFox());
            writer.flush();
            String emitted = output.toString(StandardCharsets.UTF_8);

            // ASSERT //
            assertTrue(
                    emitted.contains("<Adjective>Brown</Adjective>"),
                    "Bytes should be on the underlying stream after flush: " + emitted
            );
            assertFalse(
                    emitted.contains("</crf:Collection>"),
                    "Root close tag should not be present after flush: " + emitted
            );
        }

        try (var sequences = DATA.read(new ByteArrayInputStream(output.toByteArray()))) {
            List<TrainingSequence<String>> actual = sequences.toList();
            assertEquals(1, actual.size());
            assertBrownFox(actual.getFirst());
        }
    }

    @Test
    void writer__fromStream() throws IOException {
        // ARRANGE //
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // ACT //
        try (var writer = DATA.writer(output)) {
            writer.write(brownFox());
            writer.write(lazySleepingDog());
        }

        // ASSERT //
        String emitted = output.toString(StandardCharsets.UTF_8);
        assertTrue(
                emitted.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<crf:Collection"),
                "Output should begin with XML prolog and root open tag: " + emitted
        );
        assertTrue(emitted.endsWith("</crf:Collection>\n"), "Output should end with root close tag: " + emitted);

        try (var sequences = DATA.read(new ByteArrayInputStream(output.toByteArray()))) {
            List<TrainingSequence<String>> actual = sequences.toList();
            assertEquals(2, actual.size());
            assertBrownFox(actual.get(0));
            assertLazySleepingDog(actual.get(1));
        }
    }

    record EmitParameters(String name, TrainingSequence<String> sequence, List<String> expectedFragments) {}

    static Stream<EmitParameters> writer__emitsAdjacentTagElementsForTokenOnlySequence() {
        return Stream.of(
                new EmitParameters(
                        "two_tokens",
                        brownFox(),
                        List.of("<crf:Sequence><Adjective>Brown</Adjective><Noun>Fox</Noun></crf:Sequence>")
                ),
                new EmitParameters(
                        "three_tokens_with_repeated_tag",
                        lazySleepingDog(),
                        List.of(
                                "<crf:Sequence><Adjective>Lazy</Adjective>"
                                        + "<Adjective>Sleeping</Adjective><Noun>Dog</Noun></crf:Sequence>"
                        )
                ),
                new EmitParameters(
                        "single_token",
                        TrainingSequence.ofTokens(List.of("Fox"), List.of("Noun")),
                        List.of("<crf:Sequence><Noun>Fox</Noun></crf:Sequence>")
                )
        );
    }

    static Stream<EmitParameters> writer__emitsExcludedRunsBetweenTokens() {
        return Stream.of(
                new EmitParameters(
                        "spaces_and_punctuation_between_tokens",
                        TrainingSequence.ofSegments(
                                List.of(token("Adjective", "Brown"), excluded(" "), token("Noun", "Fox"), excluded("!"))
                        ),
                        List.of(
                                "<crf:Sequence><Adjective>Brown</Adjective>"
                                        + "<crf:Excluded> </crf:Excluded><Noun>Fox</Noun>"
                                        + "<crf:Excluded>!</crf:Excluded></crf:Sequence>"
                        )
                ),
                new EmitParameters(
                        "leading_and_trailing_excluded_runs",
                        TrainingSequence.ofSegments(List.of(excluded("  "), token("Noun", "Fox"), excluded("\n"))),
                        List.of(
                                "<crf:Sequence><crf:Excluded>  </crf:Excluded>"
                                        + "<Noun>Fox</Noun><crf:Excluded>\n</crf:Excluded></crf:Sequence>"
                        )
                )
        );
    }

    static Stream<EmitParameters> writer__emitsExpectedFragments() {
        return Stream.concat(
                writer__emitsAdjacentTagElementsForTokenOnlySequence(),
                writer__emitsExcludedRunsBetweenTokens()
        );
    }

    @MethodSource
    @ParameterizedTest
    void writer__emitsExpectedFragments(EmitParameters parameters) throws IOException {
        // ARRANGE //
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // ACT //
        try (var writer = DATA.writer(output)) {
            writer.write(parameters.sequence());
        }

        // ASSERT //
        String emitted = output.toString(StandardCharsets.UTF_8);
        parameters.expectedFragments().forEach(
                fragment -> assertTrue(emitted.contains(fragment), "Expected '" + fragment + "' in: " + emitted)
        );
    }

    private static XmlTrainingData<String> withRoot(String rootElementName) {
        return new XmlTrainingData<>(
                new StringTagProvider("0"),
                XmlTrainingDataConfiguration.builder().rootElementName(rootElementName).build()
        );
    }

    private record NullEncodingTagProvider(String nullTag) implements TagProvider<String> {
        @Override
        public String decode(@Nullable String tag) {
            return tag == null ? "0" : tag;
        }

        @Override
        public @Nullable String encode(String rawTag) {
            return nullTag.equals(rawTag) ? null : rawTag;
        }

        @Override
        public String startingTag() {
            return "0";
        }

        @Override
        public SortedSet<String> tags() {
            return new TreeSet<>(Set.of("0", "Adjective", "Noun"));
        }
    }
}
