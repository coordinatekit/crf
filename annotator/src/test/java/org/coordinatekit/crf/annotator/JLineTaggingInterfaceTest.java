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

import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.tag.TaggedSequence;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.coordinatekit.crf.annotator.AnnotatorModels.annotatorSequence;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            String input,
            TaggingAction expectedAction,
            boolean expectFinalTagsEqualToInitial
    ) {}

    record InteractionResult(TaggingResult<PartOfSpeech> result, String output) {}

    private static final String BOLD_YELLOW = "\u001b[33;1m";
    private static final String EDIT_PROMPT = "Enter the number to select the correct tag or C to cancel.";
    private static final String SEQUENCE_PROMPT = "Enter A to accept, the number to edit the token, S to skip, U to undo, or X to exit.";

    static Stream<ActionParameters> action() {
        return Stream.of(
                new ActionParameters("accept", "A\n", TaggingAction.ACCEPT, true),
                new ActionParameters("skip", "S\n", TaggingAction.SKIP, false),
                new ActionParameters("exit", "X\n", TaggingAction.EXIT, false),
                new ActionParameters("eof", "", TaggingAction.EXIT, false),
                new ActionParameters("edit_cancel_then_accept", "2\nC\nA\n", TaggingAction.ACCEPT, true)
        );
    }

    @MethodSource
    @ParameterizedTest
    void action(ActionParameters parameters) throws Exception {
        // ARRANGE //
        var sequence = simpleAnnotatorSequence();

        // ACT //
        var interaction = run(parameters.input(), sequence);

        // ASSERT //
        assertEquals(parameters.expectedAction(), interaction.result().action());
        if (parameters.expectFinalTagsEqualToInitial()) {
            assertEquals(initialTagsOf(sequence), interaction.result().finalTags());
        } else {
            assertTrue(interaction.result().finalTags().isEmpty());
        }
    }

    @Test
    void editThenAccept() throws Exception {
        // ARRANGE //
        var sequence = withModelAnnotatorSequence();
        PartOfSpeech originalTag = sequence.tokens().get(1).initialTag();
        var canonicalTags = List.copyOf(sequence.tokens().get(1).alternativeTagScores().keySet());
        PartOfSpeech chosen = canonicalTags.get(1);

        // ACT //
        var interaction = run("2\n2\nA\n", sequence);

        // ASSERT //
        assertEquals(TaggingAction.ACCEPT, interaction.result().action());
        assertEquals(chosen, interaction.result().finalTags().get(1));
        assertNotEquals(originalTag, chosen);
    }

    @Test
    void lowConfidenceRowsAreStyled() throws Exception {
        // ARRANGE //
        var sequence = withModelAnnotatorSequence();
        long lowConfidenceCount = sequence.tokens().stream().map(AnnotatorToken::initialConfidence)
                .filter(c -> c != null && c < 0.80).count();

        // ACT //
        var interaction = run("A\n", sequence);

        // ASSERT //
        long styledLineCount = interaction.output().lines().filter(line -> line.contains(BOLD_YELLOW)).count();
        assertEquals(lowConfidenceCount, styledLineCount, "expected exactly one styled line per low-confidence row");
    }

    @Test
    void noModelEditScreenListsEveryTag() throws Exception {
        // ARRANGE //
        var sequence = noModelAnnotatorSequence();
        var provider = new PartOfSpeechTagProvider();

        // ACT //
        var interaction = run("1\nC\nA\n", sequence);

        // ASSERT //
        String editRegion = editScreenRegion(interaction.output());
        for (PartOfSpeech tag : provider.tags()) {
            assertTrue(editRegion.contains(tag.name()), "expected edit screen to list every tag; missing " + tag);
        }
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
        var interaction = runWithMaxWidth("1\nC\nA\n", sequence, maxWidth);

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

    @Test
    void undoRevertsLastEdit() throws Exception {
        // ARRANGE //
        var sequence = withModelAnnotatorSequence();

        // ACT //
        var interaction = run("2\n2\nU\nA\n", sequence);

        // ASSERT //
        assertEquals(TaggingAction.ACCEPT, interaction.result().action());
        assertEquals(initialTagsOf(sequence), interaction.result().finalTags());
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

    private static <F, T extends Comparable<T>> List<T> initialTagsOf(AnnotatorSequence<F, T> sequence) {
        return sequence.tokens().stream().map(AnnotatorToken::initialTag).toList();
    }

    private static AnnotatorSequence<String, PartOfSpeech> noModelAnnotatorSequence() {
        List<String> tokens = List.of("The", "quick", "brown", "fox", "jumps", "over", "the", "lazy", "dog", ".");
        return annotatorSequence(1, 1, tokens, new PartOfSpeechTagProvider());
    }

    private InteractionResult run(String input, AnnotatorSequence<String, PartOfSpeech> sequence) throws Exception {
        return runWithMaxWidth(input, sequence, 30);
    }

    private InteractionResult runWithMaxWidth(
            String input,
            AnnotatorSequence<String, PartOfSpeech> sequence,
            int maxWidth
    ) throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Terminal terminal = new DumbTerminal("test", "ansi", in, out, StandardCharsets.UTF_8)) {
            var ui = JLineTaggingInterface.<String, PartOfSpeech>builder().tagProvider(new PartOfSpeechTagProvider())
                    .terminal(terminal).maxTokenDisplayWidth(maxWidth).build();
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
        List<Double> topScores = List.of(0.99, 0.55, 0.92, 0.97, 0.95, 0.88, 0.99, 0.62, 0.95, 0.91);
        List<Set<String>> features = new ArrayList<>();
        List<Map<PartOfSpeech, Double>> tagScores = new ArrayList<>();
        for (int index = 0; index < tokens.size(); index++) {
            features.add(Set.of());
            Map<PartOfSpeech, Double> map = new LinkedHashMap<>();
            PartOfSpeech top = topTags.get(index);
            double topScore = topScores.get(index);
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
