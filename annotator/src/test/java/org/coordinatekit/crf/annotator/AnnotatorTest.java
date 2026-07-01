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

import static org.coordinatekit.crf.core.preprocessing.Feature.createFeature;
import org.coordinatekit.crf.core.Sequence;
import org.coordinatekit.crf.core.StringTagProvider;
import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.io.XmlTrainingData;
import org.coordinatekit.crf.core.preprocessing.Feature;
import org.coordinatekit.crf.core.preprocessing.FeatureExtractor;
import org.coordinatekit.crf.core.preprocessing.TrainingPositionedToken;
import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.coordinatekit.crf.core.preprocessing.Tokenizer;
import org.coordinatekit.crf.core.preprocessing.WhitespaceTokenizer;
import org.coordinatekit.crf.core.tag.CrfTagger;
import org.coordinatekit.crf.core.tag.TaggedPositionedToken;
import org.coordinatekit.crf.core.tag.TaggedSequence;
import org.coordinatekit.crf.core.tag.TaggedTokenization;
import org.coordinatekit.crf.core.tag.TaggedTokenizations;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.coordinatekit.crf.annotator.AnnotatorModels.taggingResult;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.assertUntokenizableWarning;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.quietTerminal;
import static org.coordinatekit.crf.annotator.TaggingAction.ACCEPT;
import static org.coordinatekit.crf.annotator.TaggingAction.EXIT;
import static org.coordinatekit.crf.annotator.TaggingAction.SKIP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotatorTest {
    private static final List<String> INPUT_LINES = List
            .of("the quick brown", "fox jumps over", "the lazy dog", "a second sentence", "one more line");

    private static final List<List<String>> INPUT_TOKENS = INPUT_LINES.stream().map(line -> List.of(line.split(" ")))
            .toList();

    private static final TagProvider<String> TAG_PROVIDER = new StringTagProvider(Set.of("DT", "NN", "VB"), "NN");

    record BuilderExceptionParameters(
            String name,
            Executable action,
            Class<? extends Exception> expectedClass,
            String expectedMessage
    ) {}

    record FeatureWiringParameters(
            String name,
            UnaryOperator<Annotator.Builder<String>> configure,
            boolean expectedFeaturesAvailable,
            List<Set<Feature>> expectedFeatures,
            boolean expectedVerboseFeaturesAvailable,
            List<Set<Feature>> expectedVerboseFeatures
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
                        () -> Annotator.<String>builder().taggingInterface(new ScriptedTaggingInterface<>())
                                .terminal(quietTerminal()).tokenizer(new WhitespaceTokenizer()).build(),
                        IllegalStateException.class,
                        "tagProvider must be set"
                ),
                new BuilderExceptionParameters(
                        "no_taggingInterface",
                        () -> Annotator.<String>builder().tagProvider(TAG_PROVIDER).terminal(quietTerminal())
                                .tokenizer(new WhitespaceTokenizer()).build(),
                        IllegalStateException.class,
                        "taggingInterface must be set"
                ),
                new BuilderExceptionParameters(
                        "no_terminal",
                        () -> Annotator.<String>builder().tagProvider(TAG_PROVIDER)
                                .taggingInterface(new ScriptedTaggingInterface<>()).tokenizer(new WhitespaceTokenizer())
                                .build(),
                        IllegalStateException.class,
                        "terminal must be set"
                ),
                new BuilderExceptionParameters(
                        "empty_tags",
                        () -> Annotator.<String>builder().tagProvider(new StringTagProvider("NN"))
                                .taggingInterface(new ScriptedTaggingInterface<>()).terminal(quietTerminal())
                                .tokenizer(new WhitespaceTokenizer()).build(),
                        IllegalStateException.class,
                        "tagProvider.tags() must not be empty"
                ),
                new BuilderExceptionParameters(
                        "no_tokenizer_no_tagger",
                        () -> Annotator.<String>builder().tagProvider(TAG_PROVIDER)
                                .taggingInterface(new ScriptedTaggingInterface<>()).terminal(quietTerminal()).build(),
                        IllegalStateException.class,
                        "at least one of tokenizer or tagger must be set"
                )
        );
    }

    static Stream<FeatureWiringParameters> featureWiring() {
        List<String> lowercaseTokens = List.of("the", "quick", "brown");
        List<Set<Feature>> embeddedFeatures = List.of(
                Set.of(createFeature("embedded1")),
                Set.of(createFeature("embedded2")),
                Set.of(createFeature("embedded3"))
        );
        List<Set<Feature>> noFeatures = List.of(Set.of(), Set.of(), Set.of());
        return Stream
                .of(
                        new FeatureWiringParameters(
                                "no_feature_sources_baseline",
                                builder -> builder.tokenizer(new WhitespaceTokenizer()),
                                false,
                                noFeatures,
                                false,
                                noFeatures
                        ),
                        new FeatureWiringParameters(
                                "tagger_alone_enables_verbose_fallback",
                                builder -> builder.tagger(fixedTagger(lowercaseTokens, embeddedFeatures)),
                                false,
                                noFeatures,
                                true,
                                embeddedFeatures
                        ),
                        new FeatureWiringParameters(
                                "extractor_runs_on_tagger_tokens",
                                builder -> builder.featureExtractor(prefixExtractor("TOKEN_"))
                                        .tagger(fixedTagger(List.of("THE", "QUICK", "BROWN"), noFeatures)),
                                true,
                                List.of(
                                        Set.of(createFeature("TOKEN_THE")),
                                        Set.of(createFeature("TOKEN_QUICK")),
                                        Set.of(createFeature("TOKEN_BROWN"))
                                ),
                                true,
                                noFeatures
                        ),
                        new FeatureWiringParameters(
                                "extractor_runs_on_tokenizer_tokens",
                                builder -> builder
                                        .featureExtractor(
                                                (sequence, position) -> Set
                                                        .of(
                                                                createFeature(
                                                                        "LENGTH_" + sequence.get(position).token()
                                                                                .length()
                                                                )
                                                        )
                                        ).tokenizer(new WhitespaceTokenizer()),
                                true,
                                List.of(
                                        Set.of(createFeature("LENGTH_3")),
                                        Set.of(createFeature("LENGTH_5")),
                                        Set.of(createFeature("LENGTH_5"))
                                ),
                                false,
                                noFeatures
                        ),
                        new FeatureWiringParameters(
                                "extractor_sees_whole_sequence_and_positions",
                                builder -> builder.featureExtractor(positionAndNextTokenExtractor())
                                        .tokenizer(new WhitespaceTokenizer()),
                                true,
                                List.of(
                                        Set.of(createFeature("0:NEXT_quick")),
                                        Set.of(createFeature("1:NEXT_brown")),
                                        Set.of(createFeature("2:END"))
                                ),
                                false,
                                noFeatures
                        ),
                        new FeatureWiringParameters(
                                "verbose_extractor_populates_verbose_features",
                                builder -> builder.featureExtractor(prefixExtractor("KEY_"))
                                        .tokenizer(new WhitespaceTokenizer())
                                        .verboseFeatureExtractor(prefixExtractor("VERBOSE_")),
                                true,
                                List.of(
                                        Set.of(createFeature("KEY_the")),
                                        Set.of(createFeature("KEY_quick")),
                                        Set.of(createFeature("KEY_brown"))
                                ),
                                true,
                                List.of(
                                        Set.of(createFeature("VERBOSE_the")),
                                        Set.of(createFeature("VERBOSE_quick")),
                                        Set.of(createFeature("VERBOSE_brown"))
                                )
                        ),
                        new FeatureWiringParameters(
                                "verbose_extractor_overrides_tagger_fallback",
                                builder -> builder.featureExtractor(prefixExtractor("KEY_"))
                                        .tagger(fixedTagger(lowercaseTokens, embeddedFeatures))
                                        .verboseFeatureExtractor(prefixExtractor("VERBOSE_")),
                                true,
                                List.of(
                                        Set.of(createFeature("KEY_the")),
                                        Set.of(createFeature("KEY_quick")),
                                        Set.of(createFeature("KEY_brown"))
                                ),
                                true,
                                List.of(
                                        Set.of(createFeature("VERBOSE_the")),
                                        Set.of(createFeature("VERBOSE_quick")),
                                        Set.of(createFeature("VERBOSE_brown"))
                                )
                        ),
                        new FeatureWiringParameters(
                                "verbose_extractor_without_key_extractor",
                                builder -> builder.tokenizer(new WhitespaceTokenizer())
                                        .verboseFeatureExtractor(prefixExtractor("VERBOSE_")),
                                false,
                                noFeatures,
                                true,
                                List.of(
                                        Set.of(createFeature("VERBOSE_the")),
                                        Set.of(createFeature("VERBOSE_quick")),
                                        Set.of(createFeature("VERBOSE_brown"))
                                )
                        ),
                        new FeatureWiringParameters(
                                "verbose_unavailable_without_tagger_or_verbose_extractor",
                                builder -> builder.featureExtractor(prefixExtractor("KEY_"))
                                        .tokenizer(new WhitespaceTokenizer()),
                                true,
                                List.of(
                                        Set.of(createFeature("KEY_the")),
                                        Set.of(createFeature("KEY_quick")),
                                        Set.of(createFeature("KEY_brown"))
                                ),
                                false,
                                noFeatures
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
                        INPUT_TOKENS,
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

    @Test
    void accept__preservesExcludedRunsLosslessly(@TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        String line = "Smith,  Jones .";
        Path inputFile = writeInput(tempDirectory, List.of(line));
        Path outputFile = tempDirectory.resolve("output.xml");
        ScriptedTaggingInterface<String> tagging = new ScriptedTaggingInterface<>();
        tagging.results.add(accept("NN", "NN", "NN"));

        // ACT //
        try (Terminal terminal = quietTerminal()) {
            annotatorWith(tagging, terminal).annotate(inputFile, outputFile);
        }

        // ASSERT //
        List<TrainingSequence<String>> written = readOutput(outputFile);
        assertEquals(1, written.size());
        assertEquals(List.of("Smith,", "Jones", "."), tokensOf(written.getFirst()));
        assertEquals(
                line,
                written.getFirst().surface(),
                "the original surface, including awkward spacing, must round-trip exactly"
        );
    }

    @Test
    void accept__wrongTagCountRejected(@TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        Path inputFile = writeInput(tempDirectory, List.of("the quick brown"));
        Path outputFile = tempDirectory.resolve("output.xml");
        ScriptedTaggingInterface<String> tagging = new ScriptedTaggingInterface<>();
        tagging.results.add(accept("DT", "NN")); // two tags for a three-token line

        // ACT //
        try (Terminal terminal = quietTerminal()) {
            Annotator<String> annotator = annotatorWith(tagging, terminal);
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> annotator.annotate(inputFile, outputFile)
            );

            // ASSERT //
            assertEquals(
                    "Expected one tag per token segment: got 2 tags for 3 token segments.",
                    exception.getMessage()
            );
        }
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
        ScriptedTaggingInterface<String> tagging = new ScriptedTaggingInterface<>();
        tagging.results.add(accept("DT", "NN", "NN"));
        tagging.throwAfterScriptedResults = new RuntimeException("simulated tagging interface crash");

        // ACT //
        try (Terminal terminal = quietTerminal()) {
            Annotator<String> annotator = annotatorWith(tagging, terminal);
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

    @MethodSource
    @ParameterizedTest
    void featureWiring(FeatureWiringParameters parameters, @TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        Path inputFile = writeInput(tempDirectory, List.of("the quick brown"));
        Path outputFile = tempDirectory.resolve("output.xml");
        ScriptedTaggingInterface<String> tagging = new ScriptedTaggingInterface<>();
        tagging.results.add(accept("DT", "NN", "NN"));

        // ACT //
        try (Terminal terminal = quietTerminal()) {
            parameters.configure()
                    .apply(
                            Annotator.<String>builder().tagProvider(TAG_PROVIDER).taggingInterface(tagging)
                                    .terminal(terminal)
                    ).build().annotate(inputFile, outputFile);
        }

        // ASSERT //
        AnnotatorSequence<String> presented = tagging.presented.getFirst();
        assertEquals(parameters.expectedFeaturesAvailable(), presented.featureAvailability().keyAvailable());
        assertEquals(parameters.expectedFeatures(), presented.tokens().stream().map(AnnotatorToken::features).toList());
        assertEquals(parameters.expectedVerboseFeaturesAvailable(), presented.featureAvailability().verboseAvailable());
        assertEquals(
                parameters.expectedVerboseFeatures(),
                presented.tokens().stream().map(AnnotatorToken::verboseFeatures).toList()
        );
        List<TrainingSequence<String>> written = readOutput(outputFile);
        assertEquals(1, written.size());
        assertEquals(
                List.of("DT", "NN", "NN"),
                tagsOf(written.getFirst()),
                "display features must not affect the written training data"
        );
    }

    @Test
    void resume__skipsLinesAlreadyInOutput(@TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        Path inputFile = writeInput(tempDirectory, INPUT_LINES);
        Path outputFile = tempDirectory.resolve("output.xml");
        ScriptedTaggingInterface<String> tagging = prepopulateTwoLinesAndScriptThreeAccepts(outputFile);

        // ACT //
        try (Terminal terminal = quietTerminal()) {
            annotatorWith(tagging, terminal).annotate(inputFile, outputFile);
        }

        // ASSERT //
        assertEquals(3, tagging.presented.size(), "the first two pre-existing lines should be skipped");
        AnnotatorSequence<String> firstPresented = tagging.presented.getFirst();
        assertEquals(List.of("the", "lazy", "dog"), tokensOf(firstPresented));
        assertEquals(
                3,
                firstPresented.sequenceNumber(),
                "sequence numbering reflects input position: two auto-skipped lines precede the first shown"
        );
        assertEquals(5, firstPresented.totalSequences(), "total sequence count includes auto-skipped lines");

        List<TrainingSequence<String>> written = readOutput(outputFile);
        List<List<String>> expectedTags = List.of(
                List.of("DT", "NN", "NN"),
                List.of("NN", "VB", "DT"),
                List.of("DT", "NN", "NN"),
                List.of("DT", "NN", "NN"),
                List.of("DT", "NN", "NN")
        );
        assertEquals(INPUT_TOKENS, written.stream().map(AnnotatorTest::tokensOf).toList());
        assertEquals(expectedTags, written.stream().map(AnnotatorTest::tagsOf).toList());
    }

    @Test
    void resume__startupMessageEmitted(@TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        Path inputFile = writeInput(tempDirectory, INPUT_LINES);
        Path outputFile = tempDirectory.resolve("output.xml");
        ScriptedTaggingInterface<String> tagging = prepopulateTwoLinesAndScriptThreeAccepts(outputFile);
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // ACT //
        try (Terminal terminal = new DumbTerminal("test", "ansi", in, out, StandardCharsets.UTF_8)) {
            annotatorWith(tagging, terminal).annotate(inputFile, outputFile);
            terminal.flush();
        }

        // ASSERT //
        String captured = out.toString(StandardCharsets.UTF_8);
        assertTrue(
                captured.contains("skipped 2 of 5"),
                "expected resume message to report 'skipped 2 of 5': " + captured
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
                TrainingSequence.ofTokens(List.of("the", "quick", "brown"), List.of("DT", "NN", "NN"))
        );
        ScriptedTaggingInterface<String> tagging = new ScriptedTaggingInterface<>();
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
        ScriptedTaggingInterface<String> tagging = new ScriptedTaggingInterface<>();
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

        ScriptedTaggingInterface<String> firstRun = new ScriptedTaggingInterface<>();
        INPUT_LINES.forEach(line -> firstRun.results.add(skip()));

        ScriptedTaggingInterface<String> secondRun = new ScriptedTaggingInterface<>();
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
        assertEquals(
                INPUT_TOKENS,
                secondRun.presented.stream().map(AnnotatorTest::tokensOf).toList(),
                "the second run must re-present the 5 input lines in order"
        );
        List<TrainingSequence<String>> written = readOutput(outputFile);
        assertEquals(INPUT_TOKENS, written.stream().map(AnnotatorTest::tokensOf).toList());
        List<List<String>> expectedTags = INPUT_LINES.stream().map(line -> List.of("DT", "NN", "NN")).toList();
        assertEquals(expectedTags, written.stream().map(AnnotatorTest::tagsOf).toList());
    }

    @ParameterizedTest(name = "withTokenizer={0}")
    @ValueSource(booleans = {true, false})
    void tagger__usesTaggerSuggestions(boolean withTokenizer, @TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        Path inputFile = writeInput(tempDirectory, List.of("the quick brown"));
        Path outputFile = tempDirectory.resolve("output.xml");
        FixedTagger tagger = theQuickBrownTagger();
        ScriptedTaggingInterface<String> tagging = new ScriptedTaggingInterface<>();
        tagging.results.add(accept("DT", "NN", "NN"));

        // ACT //
        try (Terminal terminal = quietTerminal()) {
            var builder = Annotator.<String>builder().tagger(tagger).tagProvider(TAG_PROVIDER).taggingInterface(tagging)
                    .terminal(terminal);
            if (withTokenizer) {
                builder.tokenizer(new WhitespaceTokenizer());
            }
            builder.build().annotate(inputFile, outputFile);
        }

        // ASSERT //
        assertEquals(1, tagging.presented.size());
        assertEquals(List.of("the", "quick", "brown"), tokensOf(tagging.presented.getFirst()));
        assertEquals(
                "DT",
                tagging.presented.getFirst().tokens().getFirst().initialTag(),
                "the tagger's suggested tag should surface to the tagging interface"
        );
        List<TrainingSequence<String>> written = readOutput(outputFile);
        assertEquals(1, written.size());
        assertEquals(List.of("the", "quick", "brown"), tokensOf(written.getFirst()));
        assertEquals(List.of("DT", "NN", "NN"), tagsOf(written.getFirst()));
        assertEquals("the quick brown", written.getFirst().surface(), "captured excluded runs reproduce the surface");
    }

    @Test
    void untokenizable__lineSkippedWithWarningWhileSessionContinues(@TempDir Path tempDirectory) throws Exception {
        // ARRANGE //
        Path inputFile = writeInput(tempDirectory, List.of("the quick brown", "bad ? line", "one more line"));
        Path outputFile = tempDirectory.resolve("output.xml");
        ScriptedTaggingInterface<String> tagging = new ScriptedTaggingInterface<>();
        tagging.results.add(accept("DT", "NN", "NN"));
        tagging.results.add(accept("DT", "NN", "NN"));
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // ACT //
        try (Terminal terminal = new DumbTerminal("test", "ansi", in, out, StandardCharsets.UTF_8)) {
            Annotator.<String>builder().tagProvider(TAG_PROVIDER).taggingInterface(tagging).terminal(terminal)
                    .tokenizer(new AnnotatorTestSupport.PunctuationTokenizer()).build().annotate(inputFile, outputFile);
            terminal.flush();
        }

        // ASSERT //
        assertEquals(2, tagging.presented.size(), "only the two tokenizable lines are presented");
        assertEquals(List.of("the", "quick", "brown"), tokensOf(tagging.presented.get(0)));
        assertEquals(List.of("one", "more", "line"), tokensOf(tagging.presented.get(1)));
        assertEquals(1, tagging.presented.get(0).sequenceNumber(), "first survivor is 1 of 3");
        assertEquals(
                3,
                tagging.presented.get(1).sequenceNumber(),
                "the surviving sequence numbers stay gap-free against the untokenizable line: 3 of 3"
        );
        assertEquals(3, tagging.presented.get(0).totalSequences(), "M counts the untokenizable line too");

        List<TrainingSequence<String>> written = readOutput(outputFile);
        assertEquals(
                List.of(List.of("the", "quick", "brown"), List.of("one", "more", "line")),
                written.stream().map(AnnotatorTest::tokensOf).toList(),
                "only the two tokenizable lines are written"
        );

        String captured = out.toString(StandardCharsets.UTF_8);
        assertUntokenizableWarning(
                captured,
                2,
                "was skipped",
                "The input string contains an unsupported '?' character"
        );
    }

    @Test
    void untokenizable__numberingStaysGapFreeAcrossAutoSkipAndUntokenizable(@TempDir Path tempDirectory)
            throws Exception {
        // ARRANGE //
        Path inputFile = writeInput(tempDirectory, List.of("the quick brown", "bad ? line", "one more line"));
        Path outputFile = tempDirectory.resolve("output.xml");
        // Line 1 auto-skips (its tokenization is already present); line 2 is untokenizable; line 3 is
        // presented.
        prepopulateOutput(
                outputFile,
                TrainingSequence.ofTokens(List.of("the", "quick", "brown"), List.of("DT", "NN", "NN"))
        );
        ScriptedTaggingInterface<String> tagging = new ScriptedTaggingInterface<>();
        tagging.results.add(accept("DT", "NN", "NN"));
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // ACT //
        try (Terminal terminal = new DumbTerminal("test", "ansi", in, out, StandardCharsets.UTF_8)) {
            Annotator.<String>builder().tagProvider(TAG_PROVIDER).taggingInterface(tagging).terminal(terminal)
                    .tokenizer(new AnnotatorTestSupport.PunctuationTokenizer()).build().annotate(inputFile, outputFile);
            terminal.flush();
        }

        // ASSERT //
        assertEquals(1, tagging.presented.size(), "only the surviving third line is presented");
        assertEquals(List.of("one", "more", "line"), tokensOf(tagging.presented.getFirst()));
        assertEquals(
                3,
                tagging.presented.getFirst().sequenceNumber(),
                "the survivor's number includes the auto-skipped and untokenizable lines: 3 of 3"
        );
        assertEquals(3, tagging.presented.getFirst().totalSequences(), "M counts all three non-blank input lines");

        List<TrainingSequence<String>> written = readOutput(outputFile);
        assertEquals(2, written.size(), "the prepopulated line plus the accepted survivor are written");

        String captured = out.toString(StandardCharsets.UTF_8);
        assertUntokenizableWarning(
                captured,
                2,
                "was skipped",
                "The input string contains an unsupported '?' character"
        );
    }

    private static TaggingResult<String> accept(String... tags) {
        return taggingResult(ACCEPT, List.of(tags));
    }

    private static Annotator<String> annotatorWith(TaggingInterface<String> tagging, Terminal terminal) {
        return Annotator.<String>builder().tagProvider(TAG_PROVIDER).taggingInterface(tagging).terminal(terminal)
                .tokenizer(new WhitespaceTokenizer()).build();
    }

    private static TaggingResult<String> exit() {
        return taggingResult(EXIT, List.of());
    }

    private static FixedTagger fixedTagger(List<String> tokens, List<Set<Feature>> embeddedFeatures) {
        List<Map<String, Double>> scores = new ArrayList<>();
        for (int index = 0; index < tokens.size(); index++) {
            scores.add(Map.of(index == 0 ? "DT" : "NN", 1.0));
        }
        return new FixedTagger(Map.of("the quick brown", new TaggedSequence<>(tokens, embeddedFeatures, scores)));
    }

    private static FeatureExtractor positionAndNextTokenExtractor() {
        return (sequence, position) -> {
            String next = position + 1 < sequence.size() ? "NEXT_" + sequence.get(position + 1).token() : "END";
            return Set.of(createFeature(position + ":" + next));
        };
    }

    private static FeatureExtractor prefixExtractor(String prefix) {
        return (sequence, position) -> Set.of(createFeature(prefix + sequence.get(position).token()));
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

    private static ScriptedTaggingInterface<String> prepopulateTwoLinesAndScriptThreeAccepts(Path outputFile)
            throws IOException {
        prepopulateOutput(
                outputFile,
                TrainingSequence.ofTokens(List.of("the", "quick", "brown"), List.of("DT", "NN", "NN")),
                TrainingSequence.ofTokens(List.of("fox", "jumps", "over"), List.of("NN", "VB", "DT"))
        );
        ScriptedTaggingInterface<String> tagging = new ScriptedTaggingInterface<>();
        tagging.results.add(accept("DT", "NN", "NN"));
        tagging.results.add(accept("DT", "NN", "NN"));
        tagging.results.add(accept("DT", "NN", "NN"));
        return tagging;
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

    private static FixedTagger theQuickBrownTagger() {
        return new FixedTagger(
                Map.of(
                        "the quick brown",
                        new TaggedSequence<>(
                                List.of("the", "quick", "brown"),
                                List.of(Set.of(), Set.of(), Set.of()),
                                List.of(Map.of("DT", 1.0), Map.of("NN", 1.0), Map.of("NN", 1.0))
                        )
                )
        );
    }

    private static List<String> tokensOf(TrainingSequence<String> sequence) {
        return sequence.stream().map(TrainingPositionedToken::token).toList();
    }

    private static <T extends Comparable<T>> List<String> tokensOf(AnnotatorSequence<T> sequence) {
        return sequence.tokens().stream().map(AnnotatorToken::token).toList();
    }

    private static Path writeInput(Path tempDirectory, List<String> lines) throws IOException {
        Path inputFile = tempDirectory.resolve("input.txt");
        Files.writeString(inputFile, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        return inputFile;
    }

    private static final class FixedTagger implements CrfTagger<String> {
        private final Map<String, Sequence<TaggedPositionedToken<String>>> responses;
        private final Tokenizer tokenizer = new WhitespaceTokenizer();

        FixedTagger(Map<String, Sequence<TaggedPositionedToken<String>>> responses) {
            this.responses = responses;
        }

        @Override
        public TaggedTokenization<String> tag(String input) {
            Sequence<TaggedPositionedToken<String>> response = responses.get(input);
            if (response == null) {
                throw new AssertionError("FixedTagger has no scripted response for input: " + input);
            }
            return TaggedTokenizations.of(response, tokenizer.tokenize(input), tags -> 0.0);
        }
    }

    private static final class ScriptedTaggingInterface<T extends Comparable<T>> implements TaggingInterface<T> {
        final List<AnnotatorSequence<T>> presented = new ArrayList<>();
        final Deque<TaggingResult<T>> results = new ArrayDeque<>();

        /** Throws after scripted results are exhausted, not on the next call. */
        @Nullable
        RuntimeException throwAfterScriptedResults;

        @Override
        public TaggingResult<T> present(AnnotatorSequence<T> sequence) {
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
