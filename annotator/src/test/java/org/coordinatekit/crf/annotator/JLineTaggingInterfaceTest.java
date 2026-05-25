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

import static org.coordinatekit.crf.annotator.AnnotatorModels.annotatorSequence;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.tag.TaggedSequence;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

class JLineTaggingInterfaceTest {
    enum PartOfSpeech {
        Adjective, Adverb, Determiner, Noun, Preposition, Verb
    }

    @NullMarked
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

    record InteractionResult(TaggingResult<PartOfSpeech> result, String output) {}

    record ThresholdParameters(String name, double threshold, long expectedStyledRowCount) {}

    private static final String BOLD_YELLOW = boldYellowEscape();
    private static final String EDIT_PROMPT = "Enter the number to select the correct tag or C to cancel.";
    private static final String SEQUENCE_PROMPT = "Enter A to accept, the number to edit the token, S to skip, U to undo, or X to exit.";
    private static final List<Double> WITH_MODEL_CONFIDENCES = List
            .of(0.99, 0.55, 0.92, 0.97, 0.95, 0.88, 0.99, 0.62, 0.95, 0.91);

    static Stream<ActionParameters> action() {
        return Stream.of(
                new ActionParameters(
                        "accept",
                        JLineTaggingInterfaceTest::simpleAnnotatorSequence,
                        "A\n",
                        TaggingAction.ACCEPT,
                        JLineTaggingInterfaceTest::initialTagsOf
                ),
                new ActionParameters(
                        "skip",
                        JLineTaggingInterfaceTest::simpleAnnotatorSequence,
                        "S\n",
                        TaggingAction.SKIP,
                        sequence -> List.of()
                ),
                new ActionParameters(
                        "exit",
                        JLineTaggingInterfaceTest::simpleAnnotatorSequence,
                        "X\n",
                        TaggingAction.EXIT,
                        sequence -> List.of()
                ),
                new ActionParameters(
                        "eof",
                        JLineTaggingInterfaceTest::simpleAnnotatorSequence,
                        "",
                        TaggingAction.EXIT,
                        sequence -> List.of()
                ),
                new ActionParameters(
                        "edit_cancel_then_accept",
                        JLineTaggingInterfaceTest::simpleAnnotatorSequence,
                        "2\nC\nA\n",
                        TaggingAction.ACCEPT,
                        JLineTaggingInterfaceTest::initialTagsOf
                ),
                new ActionParameters(
                        "edit_then_accept",
                        JLineTaggingInterfaceTest::withModelAnnotatorSequence,
                        "2\n2\nA\n",
                        TaggingAction.ACCEPT,
                        JLineTaggingInterfaceTest::initialTagsWithSecondTokenSwapped
                ),
                new ActionParameters(
                        "undo_reverts_last_edit",
                        JLineTaggingInterfaceTest::withModelAnnotatorSequence,
                        "2\n2\nU\nA\n",
                        TaggingAction.ACCEPT,
                        JLineTaggingInterfaceTest::initialTagsOf
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

    static Stream<BuilderExceptionParameters> builder__exception() {
        return Stream.of(
                new BuilderExceptionParameters(
                        "negativeThreshold",
                        () -> JLineTaggingInterface.builder().threshold(-0.1),
                        IllegalArgumentException.class,
                        "threshold must be in [0.0, 1.0], got: -0.1"
                ),
                new BuilderExceptionParameters(
                        "thresholdAboveOne",
                        () -> JLineTaggingInterface.builder().threshold(1.1),
                        IllegalArgumentException.class,
                        "threshold must be in [0.0, 1.0], got: 1.1"
                ),
                new BuilderExceptionParameters(
                        "thresholdNaN",
                        () -> JLineTaggingInterface.builder().threshold(Double.NaN),
                        IllegalArgumentException.class,
                        "threshold must be in [0.0, 1.0], got: NaN"
                ),
                new BuilderExceptionParameters(
                        "maxTokenDisplayWidthZero",
                        () -> JLineTaggingInterface.builder().maxTokenDisplayWidth(0),
                        IllegalArgumentException.class,
                        "maxTokenDisplayWidth must be positive, got: 0"
                ),
                new BuilderExceptionParameters(
                        "maxTokenDisplayWidthNegative",
                        () -> JLineTaggingInterface.builder().maxTokenDisplayWidth(-5),
                        IllegalArgumentException.class,
                        "maxTokenDisplayWidth must be positive, got: -5"
                ),
                new BuilderExceptionParameters(
                        "buildWithoutTagProvider",
                        () -> JLineTaggingInterface.builder().build(),
                        IllegalStateException.class,
                        "tagProvider must be set"
                ),
                new BuilderExceptionParameters(
                        "buildWithoutTerminal",
                        () -> JLineTaggingInterface.<String, PartOfSpeech>builder()
                                .tagProvider(new PartOfSpeechTagProvider()).build(),
                        IllegalStateException.class,
                        "terminal must be set"
                ),
                new BuilderExceptionParameters("buildWithEmptyTags", () -> {
                    try (Terminal terminal = quietTerminal()) {
                        JLineTaggingInterface.<String, PartOfSpeech>builder().tagProvider(emptyTagProvider())
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

    static Stream<ThresholdParameters> thresholdAffectsStyledRowCount() {
        return Stream.of(
                new ThresholdParameters("zero_styles_none", 0.00, 0),
                new ThresholdParameters("default_styles_below_080", 0.80, 2),
                new ThresholdParameters("ninety_styles_below_090", 0.90, 3),
                new ThresholdParameters("one_styles_everything", 1.00, 10)
        );
    }

    @MethodSource
    @ParameterizedTest
    void thresholdAffectsStyledRowCount(ThresholdParameters parameters) throws Exception {
        // ARRANGE //
        var sequence = withModelAnnotatorSequence();

        // ACT //
        var interaction = run("A\n", sequence, builder -> builder.threshold(parameters.threshold()));

        // ASSERT //
        long styledLineCount = interaction.output().lines().filter(line -> line.contains(BOLD_YELLOW)).count();
        assertEquals(parameters.expectedStyledRowCount(), styledLineCount);
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

    private static String boldYellowEscape() {
        AttributedStyle style = AttributedStyle.BOLD.foreground(AttributedStyle.YELLOW);
        String ansi = new AttributedString("X", style).toAnsi();
        return ansi.substring(0, ansi.indexOf('X'));
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

    private static List<PartOfSpeech> initialTagsOf(AnnotatorSequence<String, PartOfSpeech> sequence) {
        return sequence.tokens().stream().map(AnnotatorToken::initialTag).toList();
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

    private static Terminal quietTerminal() throws IOException {
        return new DumbTerminal(
                "test",
                "ansi",
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(),
                StandardCharsets.UTF_8
        );
    }

    private InteractionResult run(String input, AnnotatorSequence<String, PartOfSpeech> sequence) throws Exception {
        return run(input, sequence, builder -> builder.maxTokenDisplayWidth(30));
    }

    private InteractionResult run(
            String input,
            AnnotatorSequence<String, PartOfSpeech> sequence,
            Consumer<JLineTaggingInterface.Builder<String, PartOfSpeech>> customize
    ) throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Terminal terminal = new DumbTerminal("test", "ansi", in, out, StandardCharsets.UTF_8)) {
            JLineTaggingInterface.Builder<String, PartOfSpeech> builder = JLineTaggingInterface
                    .<String, PartOfSpeech>builder().tagProvider(new PartOfSpeechTagProvider()).terminal(terminal);
            customize.accept(builder);
            JLineTaggingInterface<String, PartOfSpeech> ui = builder.build();
            TaggingResult<PartOfSpeech> result = ui.present(sequence);
            terminal.flush();
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
        Map<PartOfSpeech, Double> firstScores = new LinkedHashMap<>();
        firstScores.put(PartOfSpeech.Determiner, 0.9);
        firstScores.put(PartOfSpeech.Adjective, 0.1);
        Map<PartOfSpeech, Double> secondScores = new LinkedHashMap<>();
        secondScores.put(PartOfSpeech.Noun, 0.95);
        secondScores.put(PartOfSpeech.Verb, 0.05);
        var tagged = new TaggedSequence<>(tokens, features, List.of(firstScores, secondScores));
        return annotatorSequence(1, 1, tagged);
    }

    private static AnnotatorSequence<String, PartOfSpeech> withModelAnnotatorSequence() {
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
        var tagged = new TaggedSequence<>(tokens, features, tagScores);
        return annotatorSequence(1, 1, tagged);
    }
}
