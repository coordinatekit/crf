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
package org.coordinatekit.crf.annotator;

import org.coordinatekit.crf.core.Sequence;
import org.coordinatekit.crf.core.StringTagProvider;
import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.io.XmlTrainingData;
import org.coordinatekit.crf.core.preprocessing.TrainingPositionedToken;
import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.coordinatekit.crf.core.preprocessing.WhitespaceTokenizer;
import org.coordinatekit.crf.core.tag.CrfTagger;
import org.coordinatekit.crf.core.tag.TaggedPositionedToken;
import org.coordinatekit.crf.core.tag.TaggedSequence;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.coordinatekit.crf.annotator.AnnotatorModels.taggingResult;
import static org.coordinatekit.crf.annotator.TaggingAction.ACCEPT;
import static org.coordinatekit.crf.annotator.TaggingAction.EXIT;
import static org.coordinatekit.crf.annotator.TaggingAction.SKIP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotatorTest {
    private static final List<String> INPUT_LINES = List
            .of("the quick brown", "fox jumps over", "the lazy dog", "a second sentence", "one more line");

    private static final TagProvider<String> TAG_PROVIDER = new StringTagProvider(Set.of("DT", "NN", "VB"), "NN");

    record BuilderExceptionParameters(
            String name,
            Executable action,
            Class<? extends Exception> expectedClass,
            String expectedMessage
    ) {}

    record SessionParameters(
            String name,
            List<TaggingResult<String>> script,
            int expectedPresented,
            List<List<String>> expectedWrittenTokens,
            List<List<String>> expectedWrittenTags
    ) {}

    static Stream<BuilderExceptionParameters> builder__exception() {
        return Stream.of(
                new BuilderExceptionParameters(
                        "no_tagProvider",
                        () -> Annotator.<String, String>builder().taggingInterface(new ScriptedTaggingInterface<>())
                                .terminal(quietTerminal()).tokenizer(new WhitespaceTokenizer()).build(),
                        IllegalStateException.class,
                        "tagProvider must be set"
                ),
                new BuilderExceptionParameters(
                        "no_taggingInterface",
                        () -> Annotator.<String, String>builder().tagProvider(TAG_PROVIDER).terminal(quietTerminal())
                                .tokenizer(new WhitespaceTokenizer()).build(),
                        IllegalStateException.class,
                        "taggingInterface must be set"
                ),
                new BuilderExceptionParameters(
                        "no_terminal",
                        () -> Annotator.<String, String>builder().tagProvider(TAG_PROVIDER)
                                .taggingInterface(new ScriptedTaggingInterface<>()).tokenizer(new WhitespaceTokenizer())
                                .build(),
                        IllegalStateException.class,
                        "terminal must be set"
                ),
                new BuilderExceptionParameters(
                        "empty_tags",
                        () -> Annotator.<String, String>builder().tagProvider(new StringTagProvider("NN"))
                                .taggingInterface(new ScriptedTaggingInterface<>()).terminal(quietTerminal())
                                .tokenizer(new WhitespaceTokenizer()).build(),
                        IllegalStateException.class,
                        "tagProvider.tags() must not be empty"
                ),
                new BuilderExceptionParameters(
                        "no_tokenizer_and_no_tagger",
                        () -> Annotator.<String, String>builder().tagProvider(TAG_PROVIDER)
                                .taggingInterface(new ScriptedTaggingInterface<>()).terminal(quietTerminal()).build(),
                        IllegalStateException.class,
                        "tokenizer must be set when tagger is null"
                )
        );
    }

    static Stream<SessionParameters> session() {
        return Stream.of(
                new SessionParameters(
                        "all_accepted",
                        List.of(
                                accept("DT", "NN", "NN"),
                                accept("NN", "VB", "DT"),
                                accept("DT", "NN", "NN"),
                                accept("DT", "NN", "NN"),
                                accept("DT", "NN", "NN")
                        ),
                        5,
                        List.of(
                                List.of("the", "quick", "brown"),
                                List.of("fox", "jumps", "over"),
                                List.of("the", "lazy", "dog"),
                                List.of("a", "second", "sentence"),
                                List.of("one", "more", "line")
                        ),
                        List.of(
                                List.of("DT", "NN", "NN"),
                                List.of("NN", "VB", "DT"),
                                List.of("DT", "NN", "NN"),
                                List.of("DT", "NN", "NN"),
                                List.of("DT", "NN", "NN")
                        )
                ),
                new SessionParameters(
                        "accept_skip_mixed",
                        List.of(
                                accept("DT", "NN", "NN"),
                                skip(),
                                accept("DT", "NN", "NN"),
                                skip(),
                                accept("NN", "VB", "NN")
                        ),
                        5,
                        List.of(
                                List.of("the", "quick", "brown"),
                                List.of("the", "lazy", "dog"),
                                List.of("one", "more", "line")
                        ),
                        List.of(List.of("DT", "NN", "NN"), List.of("DT", "NN", "NN"), List.of("NN", "VB", "NN"))
                ),
                new SessionParameters(
                        "exit_truncates",
                        List.of(accept("DT", "NN", "NN"), exit()),
                        2,
                        List.of(List.of("the", "quick", "brown")),
                        List.of(List.of("DT", "NN", "NN"))
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void builder__exception(BuilderExceptionParameters parameters) {
        // ACT //
        Exception exception = assertThrows(parameters.expectedClass(), parameters.action());

        // ASSERT //
        assertEquals(parameters.expectedMessage(), exception.getMessage());
    }

    @Test
    void crash__partialWrittenSequencesRereadable(@TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        Path inputFile = writeInput(tempDirectory, INPUT_LINES);
        Path outputFile = tempDirectory.resolve("output.xml");
        ScriptedTaggingInterface<String, String> tagging = new ScriptedTaggingInterface<>();
        tagging.results.add(accept("DT", "NN", "NN"));
        tagging.throwAfterScriptedResults = new RuntimeException("simulated tagging interface crash");

        // ACT //
        try (Terminal terminal = quietTerminal()) {
            Annotator<String, String> annotator = annotatorWith(tagging, terminal);
            RuntimeException thrown = assertThrows(
                    RuntimeException.class,
                    () -> annotator.annotate(inputFile, outputFile)
            );

            // ASSERT //
            assertEquals("simulated tagging interface crash", thrown.getMessage());
        }

        List<TrainingSequence<String>> written = readOutput(outputFile);
        assertEquals(1, written.size());
        assertEquals(List.of("the", "quick", "brown"), tokensOf(written.getFirst()));
        assertEquals(List.of("DT", "NN", "NN"), tagsOf(written.getFirst()));
    }

    @Test
    void resume__skipsLinesAlreadyInOutput(@TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        Path inputFile = writeInput(tempDirectory, INPUT_LINES);
        Path outputFile = tempDirectory.resolve("output.xml");
        prepopulateOutput(
                outputFile,
                new TrainingSequence<>(List.of("the", "quick", "brown"), List.of("DT", "NN", "NN")),
                new TrainingSequence<>(List.of("fox", "jumps", "over"), List.of("NN", "VB", "DT"))
        );
        ScriptedTaggingInterface<String, String> tagging = new ScriptedTaggingInterface<>();
        tagging.results.add(accept("DT", "NN", "NN"));
        tagging.results.add(accept("DT", "NN", "NN"));
        tagging.results.add(accept("DT", "NN", "NN"));

        // ACT //
        try (Terminal terminal = quietTerminal()) {
            annotatorWith(tagging, terminal).annotate(inputFile, outputFile);
        }

        // ASSERT //
        assertEquals(3, tagging.presented.size(), "the first two pre-existing lines should be skipped");
        AnnotatorSequence<String, String> firstPresented = tagging.presented.getFirst();
        assertEquals(List.of("the", "lazy", "dog"), tokensOf(firstPresented));
        assertEquals(
                1,
                firstPresented.sequenceNumber(),
                "presentation numbering starts at 1 regardless of how many lines were auto-skipped"
        );
        assertEquals(5, firstPresented.totalSequences(), "total sequence count includes auto-skipped lines");

        List<TrainingSequence<String>> written = readOutput(outputFile);
        List<List<String>> expectedTokens = List.of(
                List.of("the", "quick", "brown"),
                List.of("fox", "jumps", "over"),
                List.of("the", "lazy", "dog"),
                List.of("a", "second", "sentence"),
                List.of("one", "more", "line")
        );
        List<List<String>> expectedTags = List.of(
                List.of("DT", "NN", "NN"),
                List.of("NN", "VB", "DT"),
                List.of("DT", "NN", "NN"),
                List.of("DT", "NN", "NN"),
                List.of("DT", "NN", "NN")
        );
        assertEquals(expectedTokens, written.stream().map(AnnotatorTest::tokensOf).toList());
        assertEquals(expectedTags, written.stream().map(AnnotatorTest::tagsOf).toList());
    }

    @Test
    void resume__startupMessageEmitted(@TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        Path inputFile = writeInput(tempDirectory, INPUT_LINES);
        Path outputFile = tempDirectory.resolve("output.xml");
        prepopulateOutput(
                outputFile,
                new TrainingSequence<>(List.of("the", "quick", "brown"), List.of("DT", "NN", "NN")),
                new TrainingSequence<>(List.of("fox", "jumps", "over"), List.of("NN", "VB", "DT"))
        );
        ScriptedTaggingInterface<String, String> tagging = new ScriptedTaggingInterface<>();
        tagging.results.add(accept("DT", "NN", "NN"));
        tagging.results.add(accept("DT", "NN", "NN"));
        tagging.results.add(accept("DT", "NN", "NN"));
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // ACT //
        try (Terminal terminal = new DumbTerminal("test", "ansi", in, out, StandardCharsets.UTF_8)) {
            annotatorWith(tagging, terminal).annotate(inputFile, outputFile);
            terminal.flush();
        }

        // ASSERT //
        String captured = out.toString(StandardCharsets.UTF_8);
        assertTrue(captured.contains("2"), "expected captured terminal output to include skip count '2': " + captured);
        assertTrue(captured.contains("5"), "expected captured terminal output to include total count '5': " + captured);
        String lowered = captured.toLowerCase(Locale.ROOT);
        assertTrue(
                lowered.contains("resumed") || lowered.contains("skipped"),
                "expected captured terminal output to mention 'resumed' or 'skipped': " + captured
        );
    }

    @Test
    void resume__tokensEqualButLineDifferent(@TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        List<String> inputs = List.of("   the    quick     brown   ", "fox jumps over");
        Path inputFile = writeInput(tempDirectory, inputs);
        Path outputFile = tempDirectory.resolve("output.xml");
        prepopulateOutput(
                outputFile,
                new TrainingSequence<>(List.of("the", "quick", "brown"), List.of("DT", "NN", "NN"))
        );
        ScriptedTaggingInterface<String, String> tagging = new ScriptedTaggingInterface<>();
        tagging.results.add(accept("NN", "VB", "DT"));

        // ACT //
        try (Terminal terminal = quietTerminal()) {
            annotatorWith(tagging, terminal).annotate(inputFile, outputFile);
        }

        // ASSERT //
        assertEquals(
                1,
                tagging.presented.size(),
                "extra whitespace should not prevent matching against the resume set"
        );
        assertEquals(List.of("fox", "jumps", "over"), tokensOf(tagging.presented.getFirst()));

        List<TrainingSequence<String>> written = readOutput(outputFile);
        assertEquals(2, written.size(), "prepopulated sequence must remain and the new line must be appended");
        assertEquals(List.of("the", "quick", "brown"), tokensOf(written.get(0)));
        assertEquals(List.of("DT", "NN", "NN"), tagsOf(written.get(0)));
        assertEquals(List.of("fox", "jumps", "over"), tokensOf(written.get(1)));
        assertEquals(List.of("NN", "VB", "DT"), tagsOf(written.get(1)));
    }

    @MethodSource
    @ParameterizedTest
    void session(SessionParameters parameters, @TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        Path inputFile = writeInput(tempDirectory, INPUT_LINES);
        Path outputFile = tempDirectory.resolve("output.xml");
        ScriptedTaggingInterface<String, String> tagging = new ScriptedTaggingInterface<>();
        tagging.results.addAll(parameters.script());

        // ACT //
        try (Terminal terminal = quietTerminal()) {
            annotatorWith(tagging, terminal).annotate(inputFile, outputFile);
        }

        // ASSERT //
        assertEquals(parameters.expectedPresented(), tagging.presented.size());
        List<TrainingSequence<String>> written = readOutput(outputFile);
        assertEquals(parameters.expectedWrittenTokens(), written.stream().map(AnnotatorTest::tokensOf).toList());
        assertEquals(parameters.expectedWrittenTags(), written.stream().map(AnnotatorTest::tagsOf).toList());
    }

    @Test
    void skip__notPersistedAcrossRuns(@TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        Path inputFile = writeInput(tempDirectory, INPUT_LINES);
        Path outputFile = tempDirectory.resolve("output.xml");

        ScriptedTaggingInterface<String, String> firstRun = new ScriptedTaggingInterface<>();
        INPUT_LINES.forEach(line -> firstRun.results.add(skip()));

        ScriptedTaggingInterface<String, String> secondRun = new ScriptedTaggingInterface<>();
        INPUT_LINES.forEach(line -> secondRun.results.add(accept("DT", "NN", "NN")));

        // ACT //
        try (Terminal terminal = quietTerminal()) {
            annotatorWith(firstRun, terminal).annotate(inputFile, outputFile);
        }
        try (Terminal terminal = quietTerminal()) {
            annotatorWith(secondRun, terminal).annotate(inputFile, outputFile);
        }

        // ASSERT //
        assertEquals(INPUT_LINES.size(), firstRun.presented.size());
        assertEquals(
                INPUT_LINES.size(),
                secondRun.presented.size(),
                "SKIP must not be persisted; every line should be re-presented on the next run"
        );
        List<List<String>> expectedInputTokens = List.of(
                List.of("the", "quick", "brown"),
                List.of("fox", "jumps", "over"),
                List.of("the", "lazy", "dog"),
                List.of("a", "second", "sentence"),
                List.of("one", "more", "line")
        );
        assertEquals(
                expectedInputTokens,
                secondRun.presented.stream().map(AnnotatorTest::tokensOf).toList(),
                "the second run must re-present the 5 input lines in order"
        );
        List<TrainingSequence<String>> written = readOutput(outputFile);
        assertEquals(expectedInputTokens, written.stream().map(AnnotatorTest::tokensOf).toList());
        List<List<String>> expectedTags = INPUT_LINES.stream().map(line -> List.of("DT", "NN", "NN")).toList();
        assertEquals(expectedTags, written.stream().map(AnnotatorTest::tagsOf).toList());
    }

    @Test
    void tagger__tokenizerIgnoredWhenBothProvided(@TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        Path inputFile = writeInput(tempDirectory, List.of("the quick brown"));
        Path outputFile = tempDirectory.resolve("output.xml");
        FixedTagger tagger = new FixedTagger(
                Map.of(
                        "the quick brown",
                        new TaggedSequence<>(
                                List.of("the_quick", "brown"),
                                List.of(Set.of(), Set.of()),
                                List.of(Map.of("NN", 1.0), Map.of("NN", 1.0))
                        )
                )
        );
        ScriptedTaggingInterface<String, String> tagging = new ScriptedTaggingInterface<>();
        tagging.results.add(accept("DT", "NN"));

        // ACT //
        try (Terminal terminal = quietTerminal()) {
            Annotator.<String, String>builder().tagger(tagger).tagProvider(TAG_PROVIDER).taggingInterface(tagging)
                    .terminal(terminal).tokenizer(new WhitespaceTokenizer()).build().annotate(inputFile, outputFile);
        }

        // ASSERT //
        assertEquals(1, tagging.presented.size());
        assertEquals(
                List.of("the_quick", "brown"),
                tokensOf(tagging.presented.getFirst()),
                "tagger's tokens must take precedence over the WhitespaceTokenizer's"
        );
        List<TrainingSequence<String>> written = readOutput(outputFile);
        assertEquals(1, written.size());
        assertEquals(List.of("the_quick", "brown"), tokensOf(written.getFirst()));
        assertEquals(List.of("DT", "NN"), tagsOf(written.getFirst()));
    }

    @Test
    void tagger__usesTaggerTokensInsteadOfTokenizer(@TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        Path inputFile = writeInput(tempDirectory, List.of("the quick brown"));
        Path outputFile = tempDirectory.resolve("output.xml");
        FixedTagger tagger = new FixedTagger(
                Map.of(
                        "the quick brown",
                        new TaggedSequence<>(
                                List.of("THE", "QUICK", "BROWN"),
                                List.of(Set.of(), Set.of(), Set.of()),
                                List.of(Map.of("DT", 1.0), Map.of("NN", 1.0), Map.of("NN", 1.0))
                        )
                )
        );
        ScriptedTaggingInterface<String, String> tagging = new ScriptedTaggingInterface<>();
        tagging.results.add(accept("DT", "NN", "NN"));

        // ACT //
        try (Terminal terminal = quietTerminal()) {
            Annotator.<String, String>builder().tagger(tagger).tagProvider(TAG_PROVIDER).taggingInterface(tagging)
                    .terminal(terminal).build().annotate(inputFile, outputFile);
        }

        // ASSERT //
        assertEquals(1, tagging.presented.size());
        assertEquals(List.of("THE", "QUICK", "BROWN"), tokensOf(tagging.presented.getFirst()));
        List<TrainingSequence<String>> written = readOutput(outputFile);
        assertEquals(1, written.size());
        assertEquals(List.of("THE", "QUICK", "BROWN"), tokensOf(written.getFirst()));
        assertEquals(List.of("DT", "NN", "NN"), tagsOf(written.getFirst()));
    }

    private static TaggingResult<String> accept(String... tags) {
        return taggingResult(ACCEPT, List.of(tags));
    }

    private static Annotator<String, String> annotatorWith(
            TaggingInterface<String, String> tagging,
            Terminal terminal
    ) {
        return Annotator.<String, String>builder().tagProvider(TAG_PROVIDER).taggingInterface(tagging)
                .terminal(terminal).tokenizer(new WhitespaceTokenizer()).build();
    }

    private static TaggingResult<String> exit() {
        return taggingResult(EXIT, List.of());
    }

    @SafeVarargs
    private static void prepopulateOutput(Path outputFile, TrainingSequence<String>... sequences) throws IOException {
        XmlTrainingData<String> xml = new XmlTrainingData<>(TAG_PROVIDER);
        try (var writer = xml.appendingWriter(outputFile)) {
            for (TrainingSequence<String> sequence : sequences) {
                writer.write(sequence);
            }
        }
    }

    private static Terminal quietTerminal() throws IOException {
        return new DumbTerminal(
                "test",
                "ansi",
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(),
                StandardCharsets.UTF_8
        );
    }

    private static List<TrainingSequence<String>> readOutput(Path outputFile) throws IOException {
        XmlTrainingData<String> xml = new XmlTrainingData<>(TAG_PROVIDER);
        try (Stream<TrainingSequence<String>> stream = xml.read(outputFile)) {
            return stream.toList();
        }
    }

    private static TaggingResult<String> skip() {
        return taggingResult(SKIP, List.of());
    }

    private static List<String> tagsOf(TrainingSequence<String> sequence) {
        return sequence.stream().map(TrainingPositionedToken::tag).toList();
    }

    private static List<String> tokensOf(TrainingSequence<String> sequence) {
        return sequence.stream().map(TrainingPositionedToken::token).toList();
    }

    private static <F, T extends Comparable<T>> List<String> tokensOf(AnnotatorSequence<F, T> sequence) {
        return sequence.tokens().stream().map(AnnotatorToken::token).toList();
    }

    private static Path writeInput(Path tempDirectory, List<String> lines) throws IOException {
        Path inputFile = tempDirectory.resolve("input.txt");
        Files.writeString(inputFile, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        return inputFile;
    }

    @NullMarked
    private static final class FixedTagger implements CrfTagger<String, String> {
        private final Map<String, Sequence<TaggedPositionedToken<String, String>>> responses;

        FixedTagger(Map<String, Sequence<TaggedPositionedToken<String, String>>> responses) {
            this.responses = responses;
        }

        @Override
        public Sequence<TaggedPositionedToken<String, String>> tag(String input) {
            Sequence<TaggedPositionedToken<String, String>> response = responses.get(input);
            if (response == null) {
                throw new AssertionError("FixedTagger has no scripted response for input: " + input);
            }
            return response;
        }
    }

    @NullMarked
    private static final class ScriptedTaggingInterface<F, T extends Comparable<T>> implements TaggingInterface<F, T> {
        final List<AnnotatorSequence<F, T>> presented = new ArrayList<>();
        final Deque<TaggingResult<T>> results = new ArrayDeque<>();

        /** Throws after scripted results are exhausted, not on the next call. */
        @Nullable
        RuntimeException throwAfterScriptedResults;

        @Override
        public TaggingResult<T> present(AnnotatorSequence<F, T> sequence) {
            presented.add(sequence);
            if (!results.isEmpty()) {
                return results.removeFirst();
            }
            RuntimeException toThrow = throwAfterScriptedResults;
            if (toThrow != null) {
                throwAfterScriptedResults = null;
                throw toThrow;
            }
            throw new AssertionError(
                    "ScriptedTaggingInterface exhausted: no scripted result for presentation " + presented.size()
            );
        }
    }
}
