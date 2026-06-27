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
package org.coordinatekit.crf.annotator.terminal;

import static org.coordinatekit.crf.annotator.AnnotatorModels.annotatorSequence;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.initialTagsOf;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.quietTerminal;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.scoreMap;
import static org.coordinatekit.crf.annotator.terminal.TaggingPrompts.ALL_VIEW_PROMPT;
import static org.coordinatekit.crf.annotator.terminal.TaggingPrompts.KEY_FEATURES_PROMPT;
import static org.coordinatekit.crf.annotator.terminal.TaggingPrompts.KEY_ONLY_VIEW_PROMPT;
import static org.coordinatekit.crf.annotator.terminal.TaggingPrompts.KEY_VIEW_PROMPT;
import static org.coordinatekit.crf.annotator.terminal.TaggingPrompts.SEQUENCE_PROMPT;
import static org.coordinatekit.crf.annotator.terminal.TaggingPrompts.VERBOSE_FEATURES_PROMPT;
import static org.coordinatekit.crf.annotator.terminal.TaggingPrompts.VERBOSE_ONLY_VIEW_PROMPT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.coordinatekit.crf.annotator.AnnotatorSequence;
import org.coordinatekit.crf.annotator.AnnotatorTestSupport;
import org.coordinatekit.crf.annotator.TaggingAction;
import org.coordinatekit.crf.annotator.TaggingResult;
import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.tag.TaggedSequence;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

class TerminalTaggingInterfaceTest {
    enum PartOfSpeech {
        Adjective, Adverb, Determiner, Noun, Preposition, Verb
    }

    static final class PartOfSpeechTagProvider implements TagProvider<PartOfSpeech> {
        @Override
        public PartOfSpeech decode(@Nullable String tag) {
            return tag == null ? PartOfSpeech.Noun : PartOfSpeech.valueOf(tag);
        }

        @Override
        public String encode(PartOfSpeech rawTag) {
            return rawTag.name();
        }

        @Override
        public PartOfSpeech startingTag() {
            return PartOfSpeech.Noun;
        }

        @Override
        public SortedSet<PartOfSpeech> tags() {
            SortedSet<PartOfSpeech> sorted = new TreeSet<>();
            Collections.addAll(sorted, PartOfSpeech.values());
            return Collections.unmodifiableSortedSet(sorted);
        }
    }

    record ActionParameters(
            String name,
            Supplier<AnnotatorSequence<String, PartOfSpeech>> sequenceSupplier,
            String input,
            TaggingAction expectedAction,
            Function<AnnotatorSequence<String, PartOfSpeech>, List<PartOfSpeech>> expectedFinalTags
    ) {}

    record BuilderExceptionParameters(
            String name,
            Executable action,
            Class<? extends Exception> expectedClass,
            String expectedMessage
    ) {}

    record FeaturesViewContentParameters(
            String name,
            Supplier<AnnotatorSequence<String, PartOfSpeech>> sequenceSupplier,
            String input,
            List<String> expectedRowPatterns,
            List<String> forbiddenSubstrings
    ) {}

    record FeaturesViewHeadingParameters(
            String name,
            Supplier<AnnotatorSequence<String, PartOfSpeech>> sequenceSupplier,
            String input,
            long expectedHeadingCount
    ) {}

    record FooterPromptParameters(
            String name,
            Supplier<AnnotatorSequence<String, PartOfSpeech>> sequenceSupplier,
            String input,
            String expectedPrompt
    ) {}

    record InteractionResult(TaggingResult<PartOfSpeech> result, String output) {}

    record ThresholdParameters(String name, double threshold) {}

    private static final String BOLD_YELLOW = AnnotatorTestSupport.boldYellowEscape();
    private static final String EDIT_PROMPT = "Enter the number to select the correct tag or C to cancel.";
    private static final String FEATURES_HEADING = "Features";
    // Common prefix of every footer prompt; used to locate where a prompt begins in the output.
    private static final String FOOTER_PROMPT_PREFIX = "Enter A to accept";
    private static final List<Double> WITH_MODEL_CONFIDENCES = List
            .of(0.99, 0.55, 0.92, 0.97, 0.95, 0.88, 0.99, 0.62, 0.95, 0.91);

    static Stream<ActionParameters> action() {
        return Stream.of(
                new ActionParameters(
                        "accept",
                        TerminalTaggingInterfaceTest::simpleAnnotatorSequence,
                        "A\n",
                        TaggingAction.ACCEPT,
                        AnnotatorTestSupport::initialTagsOf
                ),
                new ActionParameters(
                        "skip",
                        TerminalTaggingInterfaceTest::simpleAnnotatorSequence,
                        "S\n",
                        TaggingAction.SKIP,
                        sequence -> List.of()
                ),
                new ActionParameters(
                        "exit",
                        TerminalTaggingInterfaceTest::simpleAnnotatorSequence,
                        "X\n",
                        TaggingAction.EXIT,
                        sequence -> List.of()
                ),
                new ActionParameters(
                        "eof",
                        TerminalTaggingInterfaceTest::simpleAnnotatorSequence,
                        "",
                        TaggingAction.EXIT,
                        sequence -> List.of()
                ),
                new ActionParameters(
                        "edit_cancel_then_accept",
                        TerminalTaggingInterfaceTest::simpleAnnotatorSequence,
                        "2\nC\nA\n",
                        TaggingAction.ACCEPT,
                        AnnotatorTestSupport::initialTagsOf
                ),
                new ActionParameters(
                        "edit_then_accept",
                        TerminalTaggingInterfaceTest::withModelAnnotatorSequence,
                        "2\n2\nA\n",
                        TaggingAction.ACCEPT,
                        TerminalTaggingInterfaceTest::initialTagsWithSecondTokenSwapped
                ),
                new ActionParameters(
                        "undo_reverts_last_edit",
                        TerminalTaggingInterfaceTest::withModelAnnotatorSequence,
                        "2\n2\nU\nA\n",
                        TaggingAction.ACCEPT,
                        AnnotatorTestSupport::initialTagsOf
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void action(ActionParameters parameters) throws Exception {
        // ARRANGE //
        var sequence = parameters.sequenceSupplier().get();

        // ACT //
        var interaction = run(parameters.input(), sequence);

        // ASSERT //
        assertEquals(parameters.expectedAction(), interaction.result().action());
        assertEquals(parameters.expectedFinalTags().apply(sequence), interaction.result().finalTags());
    }

    // Builders are intentionally write-only: each case asserts the setter throws before build() is
    // reached.
    @SuppressWarnings("WriteOnlyObject")
    static Stream<BuilderExceptionParameters> builder__exception() {
        return Stream.of(
                new BuilderExceptionParameters(
                        "negativeThreshold",
                        () -> TerminalTaggingInterface.builder().threshold(-0.1),
                        IllegalArgumentException.class,
                        "threshold must be in [0.0, 1.0], got: -0.1"
                ),
                new BuilderExceptionParameters(
                        "thresholdAboveOne",
                        () -> TerminalTaggingInterface.builder().threshold(1.1),
                        IllegalArgumentException.class,
                        "threshold must be in [0.0, 1.0], got: 1.1"
                ),
                new BuilderExceptionParameters(
                        "thresholdNaN",
                        () -> TerminalTaggingInterface.builder().threshold(Double.NaN),
                        IllegalArgumentException.class,
                        "threshold must be in [0.0, 1.0], got: NaN"
                ),
                new BuilderExceptionParameters(
                        "maxTokenDisplayWidthZero",
                        () -> TerminalTaggingInterface.builder().maxTokenDisplayWidth(0),
                        IllegalArgumentException.class,
                        "maxTokenDisplayWidth must be positive, got: 0"
                ),
                new BuilderExceptionParameters(
                        "maxTokenDisplayWidthNegative",
                        () -> TerminalTaggingInterface.builder().maxTokenDisplayWidth(-5),
                        IllegalArgumentException.class,
                        "maxTokenDisplayWidth must be positive, got: -5"
                ),
                new BuilderExceptionParameters(
                        "buildWithoutTagProvider",
                        () -> TerminalTaggingInterface.builder().build(),
                        IllegalStateException.class,
                        "tagProvider must be set"
                ),
                new BuilderExceptionParameters(
                        "buildWithoutTerminal",
                        () -> TerminalTaggingInterface.<String, PartOfSpeech>builder()
                                .tagProvider(new PartOfSpeechTagProvider()).build(),
                        IllegalStateException.class,
                        "terminal must be set"
                ),
                new BuilderExceptionParameters("buildWithEmptyTags", () -> {
                    try (Terminal terminal = quietTerminal()) {
                        TerminalTaggingInterface.<String, PartOfSpeech>builder().tagProvider(emptyTagProvider())
                                .terminal(terminal).build();
                    }
                }, IllegalStateException.class, "tagProvider.tags() must not be empty")
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

    static Stream<FeaturesViewContentParameters> featuresView__content() {
        return Stream.of(
                new FeaturesViewContentParameters(
                        "key_view_shows_sorted_features",
                        () -> featureAnnotatorSequence(true, true),
                        "F\nA\n",
                        List.of("1\\s+The\\s+CAP", "2\\s+fox\\s+ANIMAL, LOWER", "3\\s+\\.\\s+—"),
                        List.of("WINDOW_NEXT_fox", "PUNCT")
                ),
                new FeaturesViewContentParameters(
                        "all_view_shows_key_and_verbose_union",
                        () -> featureAnnotatorSequence(true, true),
                        "FA\nA\n",
                        List.of("1\\s+The\\s+CAP, WINDOW_NEXT_fox", "2\\s+fox\\s+ANIMAL, LOWER", "3\\s+\\.\\s+PUNCT"),
                        List.of()
                ),
                new FeaturesViewContentParameters(
                        "empty_feature_set_shows_placeholder",
                        () -> featureAnnotatorSequence(true, false),
                        "F\nA\n",
                        List.of("3\\s+\\.\\s+—"),
                        List.of()
                ),
                new FeaturesViewContentParameters(
                        "F_switches_from_all_view_to_key_view",
                        () -> featureAnnotatorSequence(true, true),
                        "FA\nF\nA\n",
                        List.of("1\\s+The\\s+CAP", "2\\s+fox\\s+ANIMAL, LOWER"),
                        List.of("WINDOW_NEXT_fox", "PUNCT")
                ),
                new FeaturesViewContentParameters(
                        "FA_switches_from_key_view_to_all_view",
                        () -> featureAnnotatorSequence(true, true),
                        "F\nFA\nA\n",
                        List.of("1\\s+The\\s+CAP, WINDOW_NEXT_fox", "3\\s+\\.\\s+PUNCT"),
                        List.of()
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void featuresView__content(FeaturesViewContentParameters parameters) throws Exception {
        // ARRANGE //
        var sequence = parameters.sequenceSupplier().get();

        // ACT //
        var interaction = run(parameters.input(), sequence);

        // ASSERT //
        assertEquals(TaggingAction.ACCEPT, interaction.result().action());
        String region = featuresRegion(interaction.output());
        assertRowsInOrder(region.lines().map(String::trim).toList(), parameters.expectedRowPatterns());
        for (String forbidden : parameters.forbiddenSubstrings()) {
            assertFalse(region.contains(forbidden), "expected the final features section not to contain: " + forbidden);
        }
    }

    static Stream<FeaturesViewHeadingParameters> featuresView__headingCount() {
        return Stream.of(
                new FeaturesViewHeadingParameters(
                        "F_ignored_without_features",
                        TerminalTaggingInterfaceTest::simpleAnnotatorSequence,
                        "F\nA\n",
                        0
                ),
                new FeaturesViewHeadingParameters(
                        "FA_ignored_with_key_features_only",
                        () -> featureAnnotatorSequence(true, false),
                        "FA\nA\n",
                        0
                ),
                new FeaturesViewHeadingParameters(
                        "F_ignored_with_verbose_features_only",
                        () -> featureAnnotatorSequence(false, true),
                        "F\nA\n",
                        0
                ),
                new FeaturesViewHeadingParameters(
                        "FA_shows_section_with_verbose_features_only",
                        () -> featureAnnotatorSequence(false, true),
                        "FA\nA\n",
                        1
                ),
                new FeaturesViewHeadingParameters(
                        "second_F_hides_section",
                        () -> featureAnnotatorSequence(true, false),
                        "F\nF\nA\n",
                        1
                ),
                new FeaturesViewHeadingParameters(
                        "second_FA_hides_section",
                        () -> featureAnnotatorSequence(true, true),
                        "FA\nFA\nA\n",
                        1
                ),
                new FeaturesViewHeadingParameters(
                        "view_persists_across_edit_screen_round_trip",
                        () -> featureAnnotatorSequence(true, false),
                        "F\n1\nC\nA\n",
                        2
                ),
                new FeaturesViewHeadingParameters(
                        "lowercase_fa_recognized",
                        () -> featureAnnotatorSequence(true, true),
                        "fa\nA\n",
                        1
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void featuresView__headingCount(FeaturesViewHeadingParameters parameters) throws Exception {
        // ARRANGE //
        var sequence = parameters.sequenceSupplier().get();

        // ACT //
        var interaction = run(parameters.input(), sequence);

        // ASSERT //
        assertEquals(TaggingAction.ACCEPT, interaction.result().action());
        assertEquals(parameters.expectedHeadingCount(), featuresHeadingCount(interaction.output()));
    }

    // Distinct from the featuresView__headingCount cases: runSequences uses one interface across
    // multiple present() calls, so this verifies feature-view state persists between sequences.
    @Test
    void featureViewPersistsAcrossSequences() throws Exception {
        // ARRANGE //
        var sequences = List.of(featureAnnotatorSequence(true, false), featureAnnotatorSequence(true, false));

        // ACT //
        var interaction = runSequences("F\nA\nA\n", sequences);

        // ASSERT //
        assertEquals(TaggingAction.ACCEPT, interaction.result().action());
        assertEquals(2, featuresHeadingCount(interaction.output()));
    }

    // When a stored ALL view meets a key-only sequence, the view clamps to KEY on screen, so a single
    // F must hide it. Toggling off the clamped effective view (not the raw stored view) makes the
    // first F a real hide rather than a dead ALL -> KEY step.
    @Test
    void featureViewToggleHonorsClampAcrossSequences() throws Exception {
        // ARRANGE //
        var sequences = List.of(featureAnnotatorSequence(true, true), featureAnnotatorSequence(true, false));

        // ACT //
        var interaction = runSequences("FA\nA\nF\nA\n", sequences);

        // ASSERT //
        assertEquals(TaggingAction.ACCEPT, interaction.result().action());
        assertEquals(2, featuresHeadingCount(interaction.output()));
    }

    static Stream<FooterPromptParameters> footerPrompt() {
        return Stream.of(
                new FooterPromptParameters(
                        "no_features_legacy_prompt",
                        TerminalTaggingInterfaceTest::simpleAnnotatorSequence,
                        "A\n",
                        SEQUENCE_PROMPT
                ),
                new FooterPromptParameters(
                        "key_features_only",
                        () -> featureAnnotatorSequence(true, false),
                        "A\n",
                        KEY_FEATURES_PROMPT
                ),
                new FooterPromptParameters(
                        "key_and_verbose_features",
                        () -> featureAnnotatorSequence(true, true),
                        "A\n",
                        KEY_FEATURES_PROMPT
                ),
                new FooterPromptParameters(
                        "verbose_features_only",
                        () -> featureAnnotatorSequence(false, true),
                        "A\n",
                        VERBOSE_FEATURES_PROMPT
                ),
                new FooterPromptParameters(
                        "key_view_offers_hide_and_show_all",
                        () -> featureAnnotatorSequence(true, true),
                        "F\nA\n",
                        KEY_VIEW_PROMPT
                ),
                new FooterPromptParameters(
                        "all_view_offers_show_key_only",
                        () -> featureAnnotatorSequence(true, true),
                        "FA\nA\n",
                        ALL_VIEW_PROMPT
                ),
                new FooterPromptParameters(
                        "key_only_view_offers_hide",
                        () -> featureAnnotatorSequence(true, false),
                        "F\nA\n",
                        KEY_ONLY_VIEW_PROMPT
                ),
                new FooterPromptParameters(
                        "verbose_only_view_offers_hide",
                        () -> featureAnnotatorSequence(false, true),
                        "FA\nA\n",
                        VERBOSE_ONLY_VIEW_PROMPT
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void footerPrompt(FooterPromptParameters parameters) throws Exception {
        // ARRANGE //
        var sequence = parameters.sequenceSupplier().get();

        // ACT //
        var interaction = run(parameters.input(), sequence);

        // ASSERT //
        assertEquals(TaggingAction.ACCEPT, interaction.result().action());
        boolean promptLineFound = interaction.output().lines()
                .anyMatch(line -> line.trim().equals(parameters.expectedPrompt()));
        assertTrue(promptLineFound, "expected footer prompt: " + parameters.expectedPrompt());
    }

    @Test
    void noModelEditScreenListsEveryTagAtItsRowIndex() throws Exception {
        // ARRANGE //
        var sequence = noModelAnnotatorSequence();
        List<PartOfSpeech> tags = List.copyOf(new PartOfSpeechTagProvider().tags());

        // ACT //
        var interaction = run("1\nC\nA\n", sequence);

        // ASSERT //
        String editRegion = editScreenRegion(interaction.output());
        for (int index = 0; index < tags.size(); index++) {
            int rowNumber = index + 1;
            PartOfSpeech tag = tags.get(index);
            String rowPattern = rowNumber + "\\s+" + tag.name() + "(\\s.*)?";
            boolean found = editRegion.lines().map(String::trim).anyMatch(line -> line.matches(rowPattern));
            assertTrue(found, "expected edit-screen row " + rowNumber + " to list tag " + tag);
        }
    }

    @Test
    void present__totalLikelihoodTracksSessionAndOriginal() throws Exception {
        // ARRANGE //
        // A scorer that depends on its argument, so the original and current totals diverge after an edit.
        ToDoubleFunction<List<PartOfSpeech>> probabilityFunction = tags -> 1.0
                / (1.0 + tags.stream().mapToInt(PartOfSpeech::ordinal).sum());
        var sequence = withModelScorerSequence(probabilityFunction);
        List<PartOfSpeech> initialTags = initialTagsOf(sequence);
        List<PartOfSpeech> editedTags = initialTagsWithSecondTokenSwapped(sequence);
        String original = String.format(Locale.US, "%.4f", probabilityFunction.applyAsDouble(initialTags));
        String current = String.format(Locale.US, "%.4f", probabilityFunction.applyAsDouble(editedTags));

        // ACT //
        var interaction = run("2\n2\nA\n", sequence);

        // ASSERT //
        // The leading value tracks the edited (session) tags while (was X) stays bound to the initial
        // tags, proving originalTotal binds to the initial tagging and currentTotal follows the session.
        assertTrue(
                interaction.output().contains("Total likelihood: " + current + " (was " + original + ")"),
                "expected the post-edit total-likelihood line to track the session total and the original"
        );
    }

    static Stream<ThresholdParameters> thresholdAffectsStyledRowCount() {
        return Stream.of(
                new ThresholdParameters("zero_styles_none", 0.00),
                new ThresholdParameters("default_styles_below_080", 0.80),
                new ThresholdParameters("ninety_styles_below_090", 0.90),
                new ThresholdParameters("one_styles_everything", 1.00)
        );
    }

    @MethodSource
    @ParameterizedTest
    void thresholdAffectsStyledRowCount(ThresholdParameters parameters) throws Exception {
        // ARRANGE //
        var sequence = withModelAnnotatorSequence();
        // A row is styled exactly when its confidence falls below the threshold, so the expected count
        // is derived from the fixture's confidences rather than hard-coded.
        long expectedStyledRowCount = WITH_MODEL_CONFIDENCES.stream()
                .filter(confidence -> confidence < parameters.threshold()).count();

        // ACT //
        var interaction = run("A\n", sequence, builder -> builder.threshold(parameters.threshold()));

        // ASSERT //
        long styledLineCount = interaction.output().lines().filter(line -> line.contains(BOLD_YELLOW)).count();
        assertEquals(expectedStyledRowCount, styledLineCount);
    }

    @Test
    void tokenLongerThanMaxIsTruncatedInTableButFullInEditScreen() throws Exception {
        // ARRANGE //
        int maxWidth = 10;
        String longToken = "extraordinarily";
        var tokens = List.of(longToken, "normal");
        var features = List.<Set<String>>of(Set.of(), Set.of());
        Map<PartOfSpeech, Double> scores = new LinkedHashMap<>();
        scores.put(PartOfSpeech.Determiner, 0.95);
        scores.put(PartOfSpeech.Adjective, 0.03);
        scores.put(PartOfSpeech.Noun, 0.01);
        scores.put(PartOfSpeech.Verb, 0.005);
        scores.put(PartOfSpeech.Adverb, 0.003);
        scores.put(PartOfSpeech.Preposition, 0.002);
        var tagged = new TaggedSequence<>(tokens, features, List.of(scores, scores));
        var sequence = annotatorSequence(1, 1, tagged);

        // ACT //
        var interaction = run("1\nC\nA\n", sequence, builder -> builder.maxTokenDisplayWidth(maxWidth));

        // ASSERT //
        String sequenceRegion = sequenceScreenRegion(interaction.output());
        String editRegion = editScreenRegion(interaction.output());
        assertTrue(sequenceRegion.contains("extraordi…"), "expected truncated token in sequence-screen table");
        long longTokenInSequenceRegion = sequenceRegion.lines().filter(line -> line.contains(longToken)).count();
        assertEquals(
                1,
                longTokenInSequenceRegion,
                "expected full token only on sequence-header line, not in table cell"
        );
        assertTrue(editRegion.contains("Token 1 of 2: " + longToken), "expected full token in edit-screen header");
    }

    private static void assertRowsInOrder(List<String> lines, List<String> patterns) {
        int searchFrom = 0;
        for (String rowPattern : patterns) {
            int matchIndex = -1;
            for (int index = searchFrom; index < lines.size(); index++) {
                if (lines.get(index).matches(rowPattern)) {
                    matchIndex = index;
                    break;
                }
            }
            assertTrue(
                    matchIndex >= 0,
                    "expected the final features section to have a row matching (in order): " + rowPattern
            );
            searchFrom = matchIndex + 1;
        }
    }

    private static String editScreenRegion(String output) {
        int sequencePromptIndex = output.indexOf(SEQUENCE_PROMPT);
        if (sequencePromptIndex < 0) {
            throw new AssertionError("sequence-screen prompt not found in output");
        }
        int start = sequencePromptIndex + SEQUENCE_PROMPT.length();
        int end = output.indexOf(EDIT_PROMPT, start);
        if (end < 0) {
            throw new AssertionError("edit-screen prompt not found in output");
        }
        return output.substring(start, end + EDIT_PROMPT.length());
    }

    private static TagProvider<PartOfSpeech> emptyTagProvider() {
        return new TagProvider<>() {
            @Override
            public PartOfSpeech decode(@Nullable String tag) {
                return PartOfSpeech.Noun;
            }

            @Override
            public String encode(PartOfSpeech rawTag) {
                return rawTag.name();
            }

            @Override
            public PartOfSpeech startingTag() {
                return PartOfSpeech.Noun;
            }

            @Override
            public SortedSet<PartOfSpeech> tags() {
                return Collections.emptySortedSet();
            }
        };
    }

    /**
     * The verbose set for "fox" repeats the key feature {@code ANIMAL} so the all-features view
     * exercises key/verbose union deduplication.
     */
    private static AnnotatorSequence<String, PartOfSpeech> featureAnnotatorSequence(
            boolean includeKey,
            boolean includeVerbose
    ) {
        List<String> tokens = List.of("The", "fox", ".");
        List<Set<String>> embeddedFeatures = List.of(Set.of(), Set.of(), Set.of());
        Map<PartOfSpeech, Double> firstScores = scoreMap(PartOfSpeech.Determiner, 0.9, PartOfSpeech.Adjective, 0.1);
        Map<PartOfSpeech, Double> secondScores = scoreMap(PartOfSpeech.Noun, 0.95, PartOfSpeech.Verb, 0.05);
        Map<PartOfSpeech, Double> thirdScores = scoreMap(PartOfSpeech.Noun, 0.6, PartOfSpeech.Adverb, 0.4);
        var tagged = new TaggedSequence<>(tokens, embeddedFeatures, List.of(firstScores, secondScores, thirdScores));
        List<Set<String>> features = includeKey ? List.of(Set.of("CAP"), Set.of("LOWER", "ANIMAL"), Set.of()) : null;
        List<Set<String>> verboseFeatures = includeVerbose
                ? List.of(Set.of("WINDOW_NEXT_fox"), Set.of("ANIMAL"), Set.of("PUNCT"))
                : null;
        return annotatorSequence(1, 1, tagged, features, verboseFeatures);
    }

    private static long featuresHeadingCount(String output) {
        return output.lines().filter(line -> line.trim().endsWith(FEATURES_HEADING)).count();
    }

    /** Returns the last rendered features section, i.e. the final visible view. */
    private static String featuresRegion(String output) {
        int headingIndex = output.lastIndexOf(FEATURES_HEADING);
        if (headingIndex < 0) {
            throw new AssertionError("features heading not found in output");
        }
        int end = output.indexOf(FOOTER_PROMPT_PREFIX, headingIndex);
        if (end < 0) {
            throw new AssertionError("footer prompt not found after features heading");
        }
        return output.substring(headingIndex, end);
    }

    private static List<PartOfSpeech> initialTagsWithSecondTokenSwapped(
            AnnotatorSequence<String, PartOfSpeech> sequence
    ) {
        List<PartOfSpeech> tags = new ArrayList<>(initialTagsOf(sequence));
        List<PartOfSpeech> canonicalTags = List.copyOf(sequence.tokens().get(1).alternativeTagScores().keySet());
        tags.set(1, canonicalTags.get(1));
        return tags;
    }

    private static AnnotatorSequence<String, PartOfSpeech> noModelAnnotatorSequence() {
        List<String> tokens = List.of("The", "quick", "brown", "fox", "jumps", "over", "the", "lazy", "dog", ".");
        return annotatorSequence(1, 1, tokens, new PartOfSpeechTagProvider());
    }

    private InteractionResult run(String input, AnnotatorSequence<String, PartOfSpeech> sequence) throws Exception {
        return run(input, sequence, builder -> builder.maxTokenDisplayWidth(30));
    }

    private InteractionResult run(
            String input,
            AnnotatorSequence<String, PartOfSpeech> sequence,
            Consumer<TerminalTaggingInterface.Builder<String, PartOfSpeech>> customize
    ) throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Terminal terminal = new DumbTerminal("test", "ansi", in, out, StandardCharsets.UTF_8)) {
            TerminalTaggingInterface.Builder<String, PartOfSpeech> builder = TerminalTaggingInterface
                    .<String, PartOfSpeech>builder().tagProvider(new PartOfSpeechTagProvider()).terminal(terminal);
            customize.accept(builder);
            TerminalTaggingInterface<String, PartOfSpeech> ui = builder.build();
            TaggingResult<PartOfSpeech> result = ui.present(sequence);
            terminal.flush();
            return new InteractionResult(result, out.toString(StandardCharsets.UTF_8));
        }
    }

    /**
     * Presents every sequence against a single interface instance, so feature-view state carries from
     * one sequence to the next. Returns the last sequence's result alongside the combined output.
     */
    private InteractionResult runSequences(String input, List<AnnotatorSequence<String, PartOfSpeech>> sequences)
            throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Terminal terminal = new DumbTerminal("test", "ansi", in, out, StandardCharsets.UTF_8)) {
            TerminalTaggingInterface<String, PartOfSpeech> ui = TerminalTaggingInterface.<String, PartOfSpeech>builder()
                    .tagProvider(new PartOfSpeechTagProvider()).terminal(terminal).maxTokenDisplayWidth(30).build();
            TaggingResult<PartOfSpeech> result = null;
            for (AnnotatorSequence<String, PartOfSpeech> sequence : sequences) {
                result = ui.present(sequence);
            }
            terminal.flush();
            assertNotNull(result, "runSequences requires at least one sequence");
            return new InteractionResult(result, out.toString(StandardCharsets.UTF_8));
        }
    }

    private static String sequenceScreenRegion(String output) {
        int end = output.indexOf(SEQUENCE_PROMPT);
        if (end < 0) {
            throw new AssertionError("sequence-screen prompt not found in output");
        }
        return output.substring(0, end + SEQUENCE_PROMPT.length());
    }

    private static AnnotatorSequence<String, PartOfSpeech> simpleAnnotatorSequence() {
        List<String> tokens = List.of("The", "fox");
        List<Set<String>> features = List.of(Set.of(), Set.of());
        Map<PartOfSpeech, Double> firstScores = scoreMap(PartOfSpeech.Determiner, 0.9, PartOfSpeech.Adjective, 0.1);
        Map<PartOfSpeech, Double> secondScores = scoreMap(PartOfSpeech.Noun, 0.95, PartOfSpeech.Verb, 0.05);
        var tagged = new TaggedSequence<>(tokens, features, List.of(firstScores, secondScores));
        return annotatorSequence(1, 1, tagged);
    }

    private static AnnotatorSequence<String, PartOfSpeech> withModelAnnotatorSequence() {
        return annotatorSequence(1, 1, withModelTaggedSequence());
    }

    /**
     * The same fixture as {@link #withModelAnnotatorSequence()} but carrying
     * {@code probabilityFunction}, so {@link AnnotatorSequence#probabilityOf(List)} drives the
     * total-likelihood line.
     */
    private static AnnotatorSequence<String, PartOfSpeech> withModelScorerSequence(
            ToDoubleFunction<List<PartOfSpeech>> probabilityFunction
    ) {
        return annotatorSequence(1, 1, withModelTaggedSequence(), null, null, probabilityFunction);
    }

    private static TaggedSequence<String, PartOfSpeech> withModelTaggedSequence() {
        List<String> tokens = List.of("The", "quick", "brown", "fox", "jumps", "over", "the", "lazy", "dog", ".");
        List<PartOfSpeech> topTags = List.of(
                PartOfSpeech.Determiner,
                PartOfSpeech.Adjective,
                PartOfSpeech.Adjective,
                PartOfSpeech.Noun,
                PartOfSpeech.Verb,
                PartOfSpeech.Preposition,
                PartOfSpeech.Determiner,
                PartOfSpeech.Adjective,
                PartOfSpeech.Noun,
                PartOfSpeech.Noun
        );
        List<Set<String>> features = new ArrayList<>();
        List<Map<PartOfSpeech, Double>> tagScores = new ArrayList<>();
        for (int index = 0; index < tokens.size(); index++) {
            features.add(Set.of());
            Map<PartOfSpeech, Double> map = new LinkedHashMap<>();
            PartOfSpeech top = topTags.get(index);
            double topScore = WITH_MODEL_CONFIDENCES.get(index);
            map.put(top, topScore);
            double remaining = 1.0 - topScore;
            int otherCount = PartOfSpeech.values().length - 1;
            double per = remaining / otherCount;
            for (PartOfSpeech value : PartOfSpeech.values()) {
                if (value != top) {
                    map.put(value, per);
                }
            }
            tagScores.add(map);
        }
        return new TaggedSequence<>(tokens, features, tagScores);
    }
}
