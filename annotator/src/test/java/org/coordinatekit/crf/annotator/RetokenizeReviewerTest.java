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

import org.coordinatekit.crf.annotator.AnnotatorTestSupport.PunctuationTokenizer;
import org.coordinatekit.crf.core.PositionedToken;
import org.coordinatekit.crf.core.StringTagProvider;
import org.coordinatekit.crf.core.preprocessing.Segments;
import org.coordinatekit.crf.core.preprocessing.Tokenization;
import org.coordinatekit.crf.core.preprocessing.Tokenizer;
import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.coordinatekit.crf.core.tag.CrfTagger;
import org.coordinatekit.crf.core.tag.TaggedSequence;
import org.coordinatekit.crf.core.tag.TaggedTokenization;
import org.coordinatekit.crf.core.tag.TaggedTokenizations;
import org.jline.terminal.Terminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.coordinatekit.crf.annotator.AnnotatorModels.taggingResult;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.TAG_PROVIDER;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.assertMessageContains;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.capturingTerminal;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.initialTagsOf;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.quietTerminal;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.readOutput;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.tagsOf;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.tokensOf;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.words;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.writeInput;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.writeSequences;
import static org.coordinatekit.crf.annotator.TaggingAction.ACCEPT;
import static org.coordinatekit.crf.annotator.TaggingAction.EXIT;
import static org.coordinatekit.crf.annotator.TaggingAction.SKIP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetokenizeReviewerTest {
    record BuilderExceptionParameters(
            String name,
            Executable action,
            Class<? extends Exception> expectedClass,
            String expectedMessage
    ) {}

    record PreconditionParameters(
            String name,
            boolean outputEqualsInput,
            boolean preexistingOutput,
            String expectedSubstring
    ) {}

    static Stream<BuilderExceptionParameters> builder__exception() {
        return Stream.of(
                new BuilderExceptionParameters(
                        "no_tagProvider",
                        () -> RetokenizeReviewer.<String, String>builder()
                                .taggingInterface(new ScriptedTaggingInterface<>()).terminal(quietTerminal())
                                .tokenizer(new PunctuationTokenizer()).build(),
                        IllegalStateException.class,
                        "tagProvider must be set"
                ),
                new BuilderExceptionParameters(
                        "no_taggingInterface",
                        () -> RetokenizeReviewer.<String, String>builder().tagProvider(TAG_PROVIDER)
                                .terminal(quietTerminal()).tokenizer(new PunctuationTokenizer()).build(),
                        IllegalStateException.class,
                        "taggingInterface must be set"
                ),
                new BuilderExceptionParameters(
                        "no_terminal",
                        () -> RetokenizeReviewer.<String, String>builder().tagProvider(TAG_PROVIDER)
                                .taggingInterface(new ScriptedTaggingInterface<>())
                                .tokenizer(new PunctuationTokenizer()).build(),
                        IllegalStateException.class,
                        "terminal must be set"
                ),
                new BuilderExceptionParameters(
                        "no_tokenizer",
                        () -> RetokenizeReviewer.<String, String>builder().tagProvider(TAG_PROVIDER)
                                .taggingInterface(new ScriptedTaggingInterface<>()).terminal(quietTerminal()).build(),
                        IllegalStateException.class,
                        "tokenizer must be set"
                ),
                new BuilderExceptionParameters(
                        "empty_tags",
                        () -> RetokenizeReviewer.<String, String>builder().tagProvider(new StringTagProvider("NN"))
                                .taggingInterface(new ScriptedTaggingInterface<>()).terminal(quietTerminal())
                                .tokenizer(new PunctuationTokenizer()).build(),
                        IllegalStateException.class,
                        "tagProvider.tags() must not be empty"
                )
        );
    }

    static Stream<PreconditionParameters> review__preconditionException() {
        return Stream.of(
                new PreconditionParameters("existing_non_empty_output", false, true, "must be absent or empty"),
                new PreconditionParameters("input_equals_output", true, false, "must be different")
        );
    }

    @SuppressWarnings("SequencedCollectionMethodCanBeUsed")
    @Test
    void aligned__copiedThroughUnchanged(@TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        Path inputFile = writeInput(
                tempDirectory,
                words(List.of("the", "quick", "brown"), List.of("DT", "NN", "NN")),
                words(List.of("a", "lazy", "dog"), List.of("DT", "NN", "NN"))
        );
        Path outputFile = tempDirectory.resolve("output.xml");
        ScriptedTaggingInterface<String, String> tagging = new ScriptedTaggingInterface<>();

        // ACT //
        try (Terminal terminal = quietTerminal()) {
            reviewerWith(tagging, terminal).review(inputFile, outputFile);
        }

        // ASSERT //
        assertEquals(0, tagging.presented.size(), "aligned sequences must never be presented");
        List<TrainingSequence<String>> written = readOutput(outputFile);
        assertEquals(2, written.size());
        assertEquals(List.of("the", "quick", "brown"), tokensOf(written.get(0)));
        assertEquals(List.of("DT", "NN", "NN"), tagsOf(written.get(0)));
        assertEquals("the quick brown", written.get(0).surface());
        assertEquals(List.of("a", "lazy", "dog"), tokensOf(written.get(1)));
        assertEquals("a lazy dog", written.get(1).surface());
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
    void exit__copiesCurrentAndRemainingThrough(@TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        Path inputFile = writeThreeMisaligned(tempDirectory);
        Path outputFile = tempDirectory.resolve("output.xml");
        ScriptedTaggingInterface<String, String> tagging = new ScriptedTaggingInterface<>();
        tagging.results.add(exit());

        // ACT //
        try (Terminal terminal = quietTerminal()) {
            reviewerWith(tagging, terminal).review(inputFile, outputFile);
        }

        // ASSERT //
        assertEquals(1, tagging.presented.size(), "EXIT must stop presenting after the first misaligned sequence");
        List<TrainingSequence<String>> written = readOutput(outputFile);
        assertEquals(3, written.size(), "the exited sequence and every remaining sequence are copied through");
        assertEquals(List.of("Smith,", "Jones"), tokensOf(written.get(0)), "exited sequence kept as stored");
        assertEquals(List.of("Brown,", "Lee"), tokensOf(written.get(1)), "remaining sequences not re-tokenized");
        assertEquals(List.of("Young,", "Diaz"), tokensOf(written.get(2)));
    }

    @Test
    void freshPass__emptyOutputAllowed(@TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        Path inputFile = writeInput(tempDirectory, words(List.of("the", "quick", "brown"), List.of("DT", "NN", "NN")));
        Path outputFile = tempDirectory.resolve("output.xml");
        Files.createFile(outputFile);
        assertEquals(0, Files.size(outputFile), "precondition: the output file starts empty");
        ScriptedTaggingInterface<String, String> tagging = new ScriptedTaggingInterface<>();

        // ACT //
        try (Terminal terminal = quietTerminal()) {
            reviewerWith(tagging, terminal).review(inputFile, outputFile);
        }

        // ASSERT //
        List<TrainingSequence<String>> written = readOutput(outputFile);
        assertEquals(1, written.size(), "an existing empty output file is accepted and overwritten");
        assertEquals(List.of("the", "quick", "brown"), tokensOf(written.getFirst()));
    }

    @Test
    void invariant__outputCountAndOrderPreservedForMixedInput(@TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        Path inputFile = writeMixedInput(tempDirectory);
        Path outputFile = tempDirectory.resolve("output.xml");
        ScriptedTaggingInterface<String, String> tagging = new ScriptedTaggingInterface<>();
        tagging.results.add(accept("NN", "VB", "NN"));

        // ACT //
        try (Terminal terminal = quietTerminal()) {
            reviewerWith(tagging, terminal).review(inputFile, outputFile);
        }

        // ASSERT //
        assertEquals(1, tagging.presented.size(), "only the single misaligned sequence is presented");
        assertEquals(2, tagging.presented.getFirst().sequenceNumber(), "N reflects document-order position");
        assertEquals(4, tagging.presented.getFirst().totalSequences(), "M counts every input sequence");

        List<TrainingSequence<String>> written = readOutput(outputFile);
        assertEquals(4, written.size(), "output holds exactly one sequence per input sequence");
        assertEquals(List.of("the", "quick", "brown"), tokensOf(written.get(0)));
        assertEquals(List.of("Smith", ",", "Jones"), tokensOf(written.get(1)), "accepted sequence re-tokenized");
        assertEquals(List.of("what?"), tokensOf(written.get(2)), "untokenizable sequence kept as stored");
        assertEquals(List.of("a", "lazy", "dog"), tokensOf(written.get(3)));
    }

    @Test
    void misaligned__acceptWritesRetokenizedWithNewExcludedRuns(@TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        Path inputFile = writeInput(tempDirectory, words(List.of("Smith,", "Jones"), List.of("NN", "NN")));
        Path outputFile = tempDirectory.resolve("output.xml");
        ScriptedTaggingInterface<String, String> tagging = new ScriptedTaggingInterface<>();
        tagging.results.add(accept("NN", "VB", "NN"));

        // ACT //
        try (Terminal terminal = quietTerminal()) {
            reviewerWith(tagging, terminal).review(inputFile, outputFile);
        }

        // ASSERT //
        assertEquals(1, tagging.presented.size());
        assertEquals(List.of("Smith", ",", "Jones"), presentedTokens(tagging.presented.getFirst()));

        List<TrainingSequence<String>> written = readOutput(outputFile);
        assertEquals(1, written.size());
        assertEquals(
                List.of("Smith", ",", "Jones"),
                tokensOf(written.getFirst()),
                "tokens come from the new tokenizer"
        );
        assertEquals(List.of("NN", "VB", "NN"), tagsOf(written.getFirst()), "tags come from the scripted result");
        assertEquals(
                "Smith, Jones",
                written.getFirst().surface(),
                "the new tokenizer's excluded runs must reproduce the original surface"
        );
    }

    @Test
    void misaligned__skipKeepsOriginal(@TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        Path inputFile = writeInput(tempDirectory, words(List.of("Smith,", "Jones"), List.of("NN", "NN")));
        Path outputFile = tempDirectory.resolve("output.xml");
        ScriptedTaggingInterface<String, String> tagging = new ScriptedTaggingInterface<>();
        tagging.results.add(skip());

        // ACT //
        try (Terminal terminal = quietTerminal()) {
            reviewerWith(tagging, terminal).review(inputFile, outputFile);
        }

        // ASSERT //
        assertEquals(1, tagging.presented.size());
        List<TrainingSequence<String>> written = readOutput(outputFile);
        assertEquals(1, written.size());
        assertEquals(List.of("Smith,", "Jones"), tokensOf(written.getFirst()), "SKIP keeps the original tokens");
        assertEquals(List.of("NN", "NN"), tagsOf(written.getFirst()), "SKIP keeps the original tags");
    }

    @Test
    void noTagger__startsAtStartingTag(@TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        Path inputFile = writeInput(tempDirectory, words(List.of("Smith,", "Jones"), List.of("NN", "NN")));
        Path outputFile = tempDirectory.resolve("output.xml");
        ScriptedTaggingInterface<String, String> tagging = new ScriptedTaggingInterface<>();
        tagging.results.add(accept("NN", "NN", "NN"));

        // ACT //
        try (Terminal terminal = quietTerminal()) {
            reviewerWith(tagging, terminal).review(inputFile, outputFile);
        }

        // ASSERT //
        AnnotatorSequence<String, String> presented = tagging.presented.getFirst();
        assertEquals(
                List.of("NN", "NN", "NN"),
                initialTagsOf(presented),
                "without a tagger every token starts at the provider's starting tag"
        );
    }

    @MethodSource
    @ParameterizedTest
    void review__preconditionException(PreconditionParameters parameters, @TempDir Path tempDirectory)
            throws Exception {
        // ARRANGE //
        Path inputFile = writeInput(tempDirectory, words(List.of("the", "quick", "brown"), List.of("DT", "NN", "NN")));
        Path outputFile;
        if (parameters.outputEqualsInput()) {
            outputFile = inputFile;
        } else if (parameters.preexistingOutput()) {
            outputFile = writeSequences(
                    tempDirectory.resolve("existing.xml"),
                    words(List.of("already", "here"), List.of("NN", "NN"))
            );
        } else {
            outputFile = tempDirectory.resolve("output.xml");
        }

        // ACT & ASSERT //
        try (Terminal terminal = quietTerminal()) {
            RetokenizeReviewer<String, String> reviewer = reviewerWith(new ScriptedTaggingInterface<>(), terminal);
            ReviewPreconditionException exception = assertThrows(
                    ReviewPreconditionException.class,
                    () -> reviewer.review(inputFile, outputFile)
            );
            assertMessageContains(exception, parameters.expectedSubstring());
        }
    }

    @Test
    void summary__reportsExitedEarlyCopy(@TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        Path inputFile = writeThreeMisaligned(tempDirectory);
        Path outputFile = tempDirectory.resolve("output.xml");
        ScriptedTaggingInterface<String, String> tagging = new ScriptedTaggingInterface<>();
        tagging.results.add(exit());
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        // ACT //
        try (Terminal terminal = capturingTerminal(captured)) {
            reviewerWith(tagging, terminal).review(inputFile, outputFile);
            terminal.flush();
        }

        // ASSERT //
        String output = captured.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Reviewed 3 sequence(s):"), "summary header should report the total: " + output);
        assertTrue(
                output.contains("Exited early; copied 3 sequence(s) through unchanged."),
                "EXIT summary should report the copied count without an off-by-one: " + output
        );
    }

    @Test
    void summary__reportsPerStatusCounts(@TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        Path inputFile = writeMixedInput(tempDirectory);
        Path outputFile = tempDirectory.resolve("output.xml");
        ScriptedTaggingInterface<String, String> tagging = new ScriptedTaggingInterface<>();
        tagging.results.add(accept("NN", "VB", "NN"));
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        // ACT //
        try (Terminal terminal = capturingTerminal(captured)) {
            reviewerWith(tagging, terminal).review(inputFile, outputFile);
            terminal.flush();
        }

        // ASSERT //
        String output = captured.toString(StandardCharsets.UTF_8);
        assertTrue(
                output.contains("Reviewed 4 sequence(s): 2 aligned, 1 re-tagged, 0 skipped, 1 untokenizable."),
                "summary should report each per-status count: " + output
        );
    }

    @Test
    void tagger__suggestsInitialTags(@TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        Path inputFile = writeInput(tempDirectory, words(List.of("Smith,", "Jones"), List.of("NN", "NN")));
        Path outputFile = tempDirectory.resolve("output.xml");
        FixedTagger tagger = new FixedTagger(
                Map.of("Smith, Jones", List.of(Map.of("NN", 1.0), Map.of("VB", 1.0), Map.of("DT", 1.0)))
        );
        ScriptedTaggingInterface<String, String> tagging = new ScriptedTaggingInterface<>();
        tagging.results.add(accept("NN", "VB", "DT"));

        // ACT //
        try (Terminal terminal = quietTerminal()) {
            RetokenizeReviewer.<String, String>builder().tagProvider(TAG_PROVIDER).tagger(tagger)
                    .taggingInterface(tagging).terminal(terminal).tokenizer(new PunctuationTokenizer()).build()
                    .review(inputFile, outputFile);
        }

        // ASSERT //
        AnnotatorSequence<String, String> presented = tagging.presented.getFirst();
        assertEquals(
                List.of("Smith", ",", "Jones"),
                presentedTokens(presented),
                "tagger's tokenization drives presentation"
        );
        assertEquals(
                List.of("NN", "VB", "DT"),
                initialTagsOf(presented),
                "the tagger's suggested tags should surface to the tagging interface"
        );
    }

    @Test
    void tagger__tokenizationDisagreementFailsLoud(@TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        // The tagger tokenizes the surface whole (one token); the authority PunctuationTokenizer splits
        // it into three. Persisting must use the authority, so an ACCEPT carrying the tagger's one-token
        // count must fail loudly in toSegments rather than emit wrong-but-valid XML.
        Path inputFile = writeInput(tempDirectory, words(List.of("Smith,", "Jones"), List.of("NN", "NN")));
        Path outputFile = tempDirectory.resolve("output.xml");
        FixedTagger tagger = new FixedTagger(
                Map.of("Smith, Jones", List.of(Map.of("NN", 1.0))),
                wholeSurfaceTokenizer()
        );
        ScriptedTaggingInterface<String, String> tagging = new ScriptedTaggingInterface<>();
        tagging.results.add(accept("NN"));

        // ACT & ASSERT //
        try (Terminal terminal = quietTerminal()) {
            RetokenizeReviewer<String, String> reviewer = RetokenizeReviewer.<String, String>builder()
                    .tagProvider(TAG_PROVIDER).tagger(tagger).taggingInterface(tagging).terminal(terminal)
                    .tokenizer(new PunctuationTokenizer()).build();
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> reviewer.review(inputFile, outputFile)
            );
            assertMessageContains(exception, "one tag per token segment");
        }
    }

    @Test
    void untokenizable__copiedThroughWithWarning(@TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        Path inputFile = writeInput(tempDirectory, words(List.of("what?"), List.of("NN")));
        Path outputFile = tempDirectory.resolve("output.xml");
        ScriptedTaggingInterface<String, String> tagging = new ScriptedTaggingInterface<>();
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        // ACT //
        try (Terminal terminal = capturingTerminal(captured)) {
            reviewerWith(tagging, terminal).review(inputFile, outputFile);
            terminal.flush();
        }

        // ASSERT //
        assertEquals(0, tagging.presented.size(), "untokenizable sequences are never presented");
        List<TrainingSequence<String>> written = readOutput(outputFile);
        assertEquals(1, written.size());
        assertEquals(List.of("what?"), tokensOf(written.getFirst()), "untokenizable sequence copied through unchanged");

        String output = captured.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("untokenizable"), "a warning must be emitted: " + output);
    }

    private static TaggingResult<String> accept(String... tags) {
        return taggingResult(ACCEPT, List.of(tags));
    }

    private static TaggingResult<String> exit() {
        return taggingResult(EXIT, List.of());
    }

    private static List<String> presentedTokens(AnnotatorSequence<String, String> sequence) {
        return sequence.tokens().stream().map(AnnotatorToken::token).toList();
    }

    private static RetokenizeReviewer<String, String> reviewerWith(
            TaggingInterface<String, String> tagging,
            Terminal terminal
    ) {
        return RetokenizeReviewer.<String, String>builder().tagProvider(TAG_PROVIDER).taggingInterface(tagging)
                .terminal(terminal).tokenizer(new PunctuationTokenizer()).build();
    }

    private static TaggingResult<String> skip() {
        return taggingResult(SKIP, List.of());
    }

    /**
     * A tokenizer that emits the whole surface as a single token, to force a token-count disagreement.
     */
    private static Tokenizer wholeSurfaceTokenizer() {
        return input -> new Tokenization(List.of(Segments.token(input)));
    }

    /** Writes the four-sequence aligned / misaligned->accept / untokenizable / aligned fixture. */
    private static Path writeMixedInput(Path tempDirectory) throws IOException {
        return writeInput(
                tempDirectory,
                words(List.of("the", "quick", "brown"), List.of("DT", "NN", "NN")), // aligned
                words(List.of("Smith,", "Jones"), List.of("NN", "NN")), // misaligned -> accept
                words(List.of("what?"), List.of("NN")), // untokenizable
                words(List.of("a", "lazy", "dog"), List.of("DT", "NN", "NN")) // aligned
        );
    }

    /** Writes three sequences that all misalign under {@link PunctuationTokenizer}. */
    private static Path writeThreeMisaligned(Path tempDirectory) throws IOException {
        return writeInput(
                tempDirectory,
                words(List.of("Smith,", "Jones"), List.of("NN", "NN")),
                words(List.of("Brown,", "Lee"), List.of("NN", "NN")),
                words(List.of("Young,", "Diaz"), List.of("NN", "NN"))
        );
    }

    /**
     * A tagger with canned per-token tag scores keyed by surface, tokenized by
     * {@link PunctuationTokenizer}.
     */
    private static final class FixedTagger implements CrfTagger<String, String> {
        private final Map<String, List<Map<String, Double>>> tagScoresBySurface;
        private final Tokenizer tokenizer;

        FixedTagger(Map<String, List<Map<String, Double>>> tagScoresBySurface) {
            this(tagScoresBySurface, new PunctuationTokenizer());
        }

        FixedTagger(Map<String, List<Map<String, Double>>> tagScoresBySurface, Tokenizer tokenizer) {
            this.tagScoresBySurface = tagScoresBySurface;
            this.tokenizer = tokenizer;
        }

        @Override
        public TaggedTokenization<String, String> tag(String input) {
            List<Map<String, Double>> tagScores = tagScoresBySurface.get(input);
            if (tagScores == null) {
                throw new AssertionError("FixedTagger has no scripted response for input: " + input);
            }
            Tokenization tokenization = tokenizer.tokenize(input);
            List<String> tokens = tokenization.sequence().stream().map(PositionedToken::token).toList();
            List<Set<String>> features = tokens.stream().map(unused -> Set.<String>of()).toList();
            return TaggedTokenizations.of(new TaggedSequence<>(tokens, features, tagScores), tokenization, tags -> 0.0);
        }
    }

    private static final class ScriptedTaggingInterface<F, T extends Comparable<T>> implements TaggingInterface<F, T> {
        final List<AnnotatorSequence<F, T>> presented = new ArrayList<>();
        final Deque<TaggingResult<T>> results = new ArrayDeque<>();

        @Override
        public TaggingResult<T> present(AnnotatorSequence<F, T> sequence) {
            presented.add(sequence);
            if (results.isEmpty()) {
                throw new AssertionError(
                        "ScriptedTaggingInterface exhausted: no scripted result for presentation " + presented.size()
                );
            }
            return results.removeFirst();
        }
    }
}
