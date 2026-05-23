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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
import static org.coordinatekit.crf.annotator.AnnotatorModels.taggingResult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

class AnnotatorSequenceTest {
    enum TestTag {
        ALPHA, BETA, GAMMA
    }

    @NullMarked
    static final class TestTagProvider implements TagProvider<TestTag> {
        private final SortedSet<TestTag> tags;

        TestTagProvider(TestTag... values) {
            SortedSet<TestTag> sorted = new TreeSet<>();
            Collections.addAll(sorted, values);
            this.tags = Collections.unmodifiableSortedSet(sorted);
        }

        @Override
        public TestTag decode(@Nullable String tag) {
            return tag == null ? TestTag.ALPHA : TestTag.valueOf(tag);
        }

        @Override
        public String encode(TestTag rawTag) {
            return rawTag.name();
        }

        @Override
        public TestTag startingTag() {
            return TestTag.ALPHA;
        }

        @Override
        public SortedSet<TestTag> tags() {
            return tags;
        }
    }

    record CanonicalOrderingParameters(String name, Map<TestTag, Double> inputScores, List<TestTag> expectedKeyOrder) {}

    record ExceptionParameters(
            String name,
            Executable action,
            Class<? extends Exception> expectedClass,
            String expectedMessage
    ) {}

    record ImmutabilityParameters(String name, Executable action) {}

    @SuppressWarnings("DataFlowIssue")
    static Stream<ExceptionParameters> annotatorSequence__exception() {
        TestTagProvider provider = new TestTagProvider(TestTag.values());
        return Stream.of(
                new ExceptionParameters(
                        "withTagger_sequenceNumberZero",
                        () -> annotatorSequence(0, 1, singleTokenTaggedSequence()),
                        IllegalArgumentException.class,
                        "sequenceNumber must be at least 1, got: 0"
                ),
                new ExceptionParameters(
                        "withTagger_sequenceNumberNegative",
                        () -> annotatorSequence(-1, 1, singleTokenTaggedSequence()),
                        IllegalArgumentException.class,
                        "sequenceNumber must be at least 1, got: -1"
                ),
                new ExceptionParameters(
                        "withTagger_totalSequencesNegative",
                        () -> annotatorSequence(1, -1, singleTokenTaggedSequence()),
                        IllegalArgumentException.class,
                        "totalSequences must be at least sequenceNumber, got: totalSequences=-1, sequenceNumber=1"
                ),
                new ExceptionParameters(
                        "withTagger_totalSequencesLessThanSequenceNumber",
                        () -> annotatorSequence(3, 2, singleTokenTaggedSequence()),
                        IllegalArgumentException.class,
                        "totalSequences must be at least sequenceNumber, got: totalSequences=2, sequenceNumber=3"
                ),
                new ExceptionParameters(
                        "withTagger_nullTaggedSequence",
                        () -> annotatorSequence(1, 1, (TaggedSequence<String, TestTag>) null),
                        NullPointerException.class,
                        "taggedSequence must not be null"
                ),
                new ExceptionParameters(
                        "withoutTagger_sequenceNumberZero",
                        () -> AnnotatorModels.<String, TestTag>annotatorSequence(0, 1, List.of("a"), provider),
                        IllegalArgumentException.class,
                        "sequenceNumber must be at least 1, got: 0"
                ),
                new ExceptionParameters(
                        "withoutTagger_sequenceNumberNegative",
                        () -> AnnotatorModels.<String, TestTag>annotatorSequence(-1, 1, List.of("a"), provider),
                        IllegalArgumentException.class,
                        "sequenceNumber must be at least 1, got: -1"
                ),
                new ExceptionParameters(
                        "withoutTagger_totalSequencesLessThanSequenceNumber",
                        () -> AnnotatorModels.<String, TestTag>annotatorSequence(3, 2, List.of("a"), provider),
                        IllegalArgumentException.class,
                        "totalSequences must be at least sequenceNumber, got: totalSequences=2, sequenceNumber=3"
                ),
                new ExceptionParameters(
                        "withoutTagger_emptyTokens",
                        () -> AnnotatorModels.<String, TestTag>annotatorSequence(1, 1, List.of(), provider),
                        IllegalArgumentException.class,
                        "tokens must not be empty"
                ),
                new ExceptionParameters(
                        "withoutTagger_emptyTags",
                        () -> AnnotatorModels
                                .<String, TestTag>annotatorSequence(1, 1, List.of("a"), new TestTagProvider()),
                        IllegalArgumentException.class,
                        "tagProvider.tags() must not be empty"
                ),
                new ExceptionParameters(
                        "withoutTagger_nullTokens",
                        () -> AnnotatorModels.<String, TestTag>annotatorSequence(1, 1, null, provider),
                        NullPointerException.class,
                        "tokens must not be null"
                ),
                new ExceptionParameters(
                        "withoutTagger_nullTagProvider",
                        () -> AnnotatorModels.<String, TestTag>annotatorSequence(1, 1, List.of("a"), null),
                        NullPointerException.class,
                        "tagProvider must not be null"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void annotatorSequence__exception(ExceptionParameters parameters) {
        // ACT //
        Exception exception = assertThrows(parameters.expectedClass(), parameters.action());

        // ASSERT //
        assertEquals(parameters.expectedMessage(), exception.getMessage());
    }

    static Stream<CanonicalOrderingParameters> canonicalOrdering() {
        return Stream.of(
                new CanonicalOrderingParameters(
                        "descending_scores_distinct",
                        scoreMap(TestTag.BETA, 0.5, TestTag.GAMMA, 0.3, TestTag.ALPHA, 0.1),
                        List.of(TestTag.BETA, TestTag.GAMMA, TestTag.ALPHA)
                ),
                new CanonicalOrderingParameters(
                        "ties_broken_by_natural_tag_order",
                        scoreMap(TestTag.GAMMA, 0.5, TestTag.ALPHA, 0.5, TestTag.BETA, 0.2),
                        List.of(TestTag.ALPHA, TestTag.GAMMA, TestTag.BETA)
                ),
                new CanonicalOrderingParameters(
                        "all_tied_natural_order",
                        scoreMap(TestTag.GAMMA, 0.5, TestTag.BETA, 0.5, TestTag.ALPHA, 0.5),
                        List.of(TestTag.ALPHA, TestTag.BETA, TestTag.GAMMA)
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void canonicalOrdering(CanonicalOrderingParameters parameters) {
        // ARRANGE //
        TaggedSequence<String, TestTag> tagged = new TaggedSequence<>(
                List.of("token"),
                List.of(Set.of()),
                List.of(parameters.inputScores())
        );

        // ACT //
        AnnotatorSequence<String, TestTag> sequence = annotatorSequence(1, 1, tagged);

        // ASSERT //
        assertEquals(
                parameters.expectedKeyOrder(),
                List.copyOf(sequence.tokens().getFirst().alternativeTagScores().keySet())
        );
    }

    @Test
    void canonicalOrdering__nullScoresSortLastInTagProviderOrder() {
        // ARRANGE //
        TestTagProvider tagProvider = new TestTagProvider(TestTag.values());

        // ACT //
        AnnotatorSequence<String, TestTag> sequence = AnnotatorModels
                .annotatorSequence(1, 1, List.of("a"), tagProvider);

        // ASSERT //
        AnnotatorToken<String, TestTag> annotatorToken = sequence.tokens().getFirst();
        assertEquals(
                List.of(TestTag.ALPHA, TestTag.BETA, TestTag.GAMMA),
                List.copyOf(annotatorToken.alternativeTagScores().keySet())
        );
        annotatorToken.alternativeTagScores().values().forEach(Assertions::assertNull);
    }

    @Test
    void taggingResult__defensivelyCopiesFinalTags() {
        // ARRANGE //
        List<TestTag> source = new ArrayList<>(List.of(TestTag.ALPHA, TestTag.BETA));

        // ACT //
        TaggingResult<TestTag> result = taggingResult(TaggingAction.ACCEPT, source);
        source.add(TestTag.GAMMA);

        // ASSERT //
        assertEquals(TaggingAction.ACCEPT, result.action());
        assertEquals(List.of(TestTag.ALPHA, TestTag.BETA), result.finalTags());
        assertThrowsExactly(UnsupportedOperationException.class, () -> result.finalTags().add(TestTag.GAMMA));
    }

    @SuppressWarnings("DataFlowIssue")
    static Stream<ExceptionParameters> taggingResult__exception() {
        return Stream.of(
                new ExceptionParameters(
                        "nullAction",
                        () -> taggingResult(null, List.of(TestTag.ALPHA)),
                        NullPointerException.class,
                        "action must not be null"
                ),
                new ExceptionParameters(
                        "nullFinalTags",
                        () -> taggingResult(TaggingAction.ACCEPT, null),
                        NullPointerException.class,
                        "finalTags must not be null"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void taggingResult__exception(ExceptionParameters parameters) {
        // ACT //
        Exception exception = assertThrows(parameters.expectedClass(), parameters.action());

        // ASSERT //
        assertEquals(parameters.expectedMessage(), exception.getMessage());
    }

    @Test
    void withTagger__copiesFromTaggedSequence() {
        // ARRANGE //
        List<String> tokens = List.of("the", "fox");
        List<Set<String>> features = List.of(Set.of("f1"), Set.of("f2", "f3"));
        Map<TestTag, Double> firstScores = scoreMap(TestTag.ALPHA, 0.7, TestTag.BETA, 0.2, TestTag.GAMMA, 0.1);
        Map<TestTag, Double> secondScores = scoreMap(TestTag.BETA, 0.6, TestTag.GAMMA, 0.3, TestTag.ALPHA, 0.1);
        TaggedSequence<String, TestTag> tagged = new TaggedSequence<>(
                tokens,
                features,
                List.of(firstScores, secondScores)
        );

        // ACT //
        AnnotatorSequence<String, TestTag> sequence = annotatorSequence(2, 5, tagged);

        // ASSERT //
        assertEquals(2, sequence.sequenceNumber());
        assertEquals(5, sequence.totalSequences());
        assertEquals(2, sequence.tokens().size());
        AnnotatorToken<String, TestTag> first = sequence.tokens().getFirst();
        assertEquals("the", first.token());
        assertEquals(Set.of("f1"), first.features());
        assertEquals(TestTag.ALPHA, first.initialTag());
        assertEquals(0.7, first.initialConfidence());
        assertEquals(
                List.of(TestTag.ALPHA, TestTag.BETA, TestTag.GAMMA),
                List.copyOf(first.alternativeTagScores().keySet())
        );
        assertEquals(0.7, first.alternativeTagScores().get(TestTag.ALPHA));
        AnnotatorToken<String, TestTag> second = sequence.tokens().get(1);
        assertEquals("fox", second.token());
        assertEquals(Set.of("f2", "f3"), second.features());
        assertEquals(TestTag.BETA, second.initialTag());
        assertEquals(0.6, second.initialConfidence());
        assertEquals(
                List.of(TestTag.BETA, TestTag.GAMMA, TestTag.ALPHA),
                List.copyOf(second.alternativeTagScores().keySet())
        );
        assertEquals(0.6, second.alternativeTagScores().get(TestTag.BETA));
    }

    @Test
    void withoutTagger__populatesDefaults() {
        // ARRANGE //
        var tokens = List.of("a", "b", "c");
        var tagProvider = new TestTagProvider(TestTag.values());

        // ACT //
        AnnotatorSequence<String, TestTag> sequence = annotatorSequence(1, 1, tokens, tagProvider);

        // ASSERT //
        assertEquals(3, sequence.tokens().size());
        for (int index = 0; index < tokens.size(); index++) {
            AnnotatorToken<String, TestTag> annotatorToken = sequence.tokens().get(index);
            assertEquals(tokens.get(index), annotatorToken.token());
            assertEquals(Set.of(), annotatorToken.features());
            assertEquals(TestTag.ALPHA, annotatorToken.initialTag());
            Assertions.assertNull(annotatorToken.initialConfidence());
            assertEquals(
                    List.of(TestTag.ALPHA, TestTag.BETA, TestTag.GAMMA),
                    List.copyOf(annotatorToken.alternativeTagScores().keySet())
            );
            annotatorToken.alternativeTagScores().values().forEach(Assertions::assertNull);
        }
    }

    static Stream<ImmutabilityParameters> withoutTagger__resultIsImmutable() {
        return Stream.of(
                new ImmutabilityParameters(
                        "tokens_add",
                        () -> immutableSequence().tokens().add(immutableSequence().tokens().getFirst())
                ),
                new ImmutabilityParameters("tokens_clear", () -> immutableSequence().tokens().clear()),
                new ImmutabilityParameters("tokens_iteratorRemove", () -> {
                    var iterator = immutableSequence().tokens().iterator();
                    iterator.next();
                    iterator.remove();
                }),
                new ImmutabilityParameters("tokens_remove", () -> immutableSequence().tokens().removeFirst()),
                new ImmutabilityParameters(
                        "tokens_set",
                        () -> immutableSequence().tokens().set(0, immutableSequence().tokens().getFirst())
                ),
                new ImmutabilityParameters(
                        "alternativeTagScores_clear",
                        () -> immutableSequence().tokens().getFirst().alternativeTagScores().clear()
                ),
                new ImmutabilityParameters(
                        "alternativeTagScores_put",
                        () -> immutableSequence().tokens().getFirst().alternativeTagScores().put(TestTag.ALPHA, 0.5)
                ),
                new ImmutabilityParameters(
                        "alternativeTagScores_remove",
                        () -> immutableSequence().tokens().getFirst().alternativeTagScores().remove(TestTag.ALPHA)
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void withoutTagger__resultIsImmutable(ImmutabilityParameters parameters) {
        // ACT + ASSERT //
        assertThrowsExactly(UnsupportedOperationException.class, parameters.action());
    }

    private static AnnotatorSequence<String, TestTag> immutableSequence() {
        return AnnotatorModels.annotatorSequence(1, 1, List.of("a"), new TestTagProvider(TestTag.values()));
    }

    private static Map<TestTag, Double> scoreMap(
            TestTag firstTag,
            double firstScore,
            TestTag secondTag,
            double secondScore,
            TestTag thirdTag,
            double thirdScore
    ) {
        Map<TestTag, Double> scores = new LinkedHashMap<>();
        scores.put(firstTag, firstScore);
        scores.put(secondTag, secondScore);
        scores.put(thirdTag, thirdScore);
        return scores;
    }

    private static TaggedSequence<String, TestTag> singleTokenTaggedSequence() {
        return new TaggedSequence<>(List.of("a"), List.of(Set.of()), List.of(Map.of(TestTag.ALPHA, 1.0)));
    }
}
