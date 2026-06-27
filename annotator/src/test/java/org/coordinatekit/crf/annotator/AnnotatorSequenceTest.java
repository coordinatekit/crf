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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

import static org.coordinatekit.crf.annotator.AnnotatorModels.annotatorSequence;
import static org.coordinatekit.crf.annotator.AnnotatorModels.taggingResult;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.scoreMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

class AnnotatorSequenceTest {
    enum TestTag {
        ALPHA, BETA, GAMMA
    }

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

    record DefensiveCopyParameters(
            String name,
            BiFunction<List<Set<String>>, List<Set<String>>, AnnotatorSequence<String, TestTag>> factory
    ) {}

    record ExceptionParameters(
            String name,
            Executable action,
            Class<? extends Exception> expectedClass,
            String expectedMessage
    ) {}

    record ImmutabilityParameters(String name, Executable action) {}

    record WithFeaturesParameters(
            String name,
            Supplier<AnnotatorSequence<String, TestTag>> sequenceSupplier,
            boolean expectedFeaturesAvailable,
            List<Set<String>> expectedFeatures,
            boolean expectedVerboseFeaturesAvailable,
            List<Set<String>> expectedVerboseFeatures
    ) {}

    private static final List<Set<String>> KEY_FEATURES = List.of(Set.of("k1"), Set.of("k2a", "k2b"));
    private static final List<Set<String>> VERBOSE_FEATURES = List.of(Set.of("v1"), Set.of());

    static Stream<DefensiveCopyParameters> annotatorSequence__defensivelyCopiesFeatureInputs() {
        return Stream.of(
                new DefensiveCopyParameters(
                        "withoutTagger",
                        (features, verboseFeatures) -> annotatorSequence(
                                1,
                                1,
                                List.of("a"),
                                new TestTagProvider(TestTag.values()),
                                features,
                                verboseFeatures
                        )
                ),
                new DefensiveCopyParameters(
                        "withTagger",
                        (features, verboseFeatures) -> annotatorSequence(
                                1,
                                1,
                                singleTokenTaggedSequence(),
                                features,
                                verboseFeatures
                        )
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void annotatorSequence__defensivelyCopiesFeatureInputs(DefensiveCopyParameters parameters) {
        // ARRANGE //
        List<Set<String>> features = new ArrayList<>(List.of(new HashSet<>(Set.of("k1"))));
        List<Set<String>> verboseFeatures = new ArrayList<>(List.of(new HashSet<>(Set.of("v1"))));
        AnnotatorSequence<String, TestTag> sequence = parameters.factory().apply(features, verboseFeatures);

        // ACT //
        features.getFirst().add("late");
        verboseFeatures.getFirst().add("late");

        // ASSERT //
        assertEquals(Set.of("k1"), sequence.tokens().getFirst().features());
        assertEquals(Set.of("v1"), sequence.tokens().getFirst().verboseFeatures());
    }

    @SuppressWarnings({"DataFlowIssue", "NullAway"})
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
                        "withTagger_featuresSizeMismatch",
                        () -> annotatorSequence(
                                1,
                                1,
                                singleTokenTaggedSequence(),
                                List.of(Set.of("f1"), Set.of("f2")),
                                null
                        ),
                        IllegalArgumentException.class,
                        "features must have one entry per token, got: features=2, tokens=1"
                ),
                new ExceptionParameters(
                        "withTagger_verboseFeaturesSizeMismatch",
                        () -> annotatorSequence(
                                1,
                                1,
                                singleTokenTaggedSequence(),
                                List.of(Set.of("f1")),
                                List.of(Set.of("v1"), Set.of("v2"))
                        ),
                        IllegalArgumentException.class,
                        "verboseFeatures must have one entry per token, got: verboseFeatures=2, tokens=1"
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
                ),
                new ExceptionParameters(
                        "withoutTagger_featuresSizeMismatch",
                        () -> annotatorSequence(
                                1,
                                1,
                                List.of("a"),
                                provider,
                                List.of(Set.of("f1"), Set.of("f2")),
                                null
                        ),
                        IllegalArgumentException.class,
                        "features must have one entry per token, got: features=2, tokens=1"
                ),
                new ExceptionParameters(
                        "withoutTagger_verboseFeaturesSizeMismatch",
                        () -> annotatorSequence(
                                1,
                                1,
                                List.of("a"),
                                provider,
                                List.of(Set.of("f1")),
                                List.of(Set.of("v1"), Set.of("v2"))
                        ),
                        IllegalArgumentException.class,
                        "verboseFeatures must have one entry per token, got: verboseFeatures=2, tokens=1"
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
    void probabilityOf__carriedOnWithTaggerPathAndNullOtherwise() {
        // ARRANGE //
        ToDoubleFunction<List<TestTag>> probabilityFunction = tags -> tags.size();
        List<TestTag> tags = List.of(TestTag.ALPHA);

        // ACT //
        AnnotatorSequence<String, TestTag> withScorer = annotatorSequence(
                1,
                1,
                singleTokenTaggedSequence(),
                null,
                null,
                probabilityFunction
        );
        AnnotatorSequence<String, TestTag> withoutScorer = annotatorSequence(1, 1, singleTokenTaggedSequence());
        AnnotatorSequence<String, TestTag> noTagger = annotatorSequence(
                1,
                1,
                List.of("a"),
                new TestTagProvider(TestTag.values())
        );

        // ASSERT //
        assertEquals(1.0, withScorer.probabilityOf(tags));
        Assertions.assertNull(withoutScorer.probabilityOf(tags));
        Assertions.assertNull(noTagger.probabilityOf(tags));
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

    @SuppressWarnings({"DataFlowIssue", "NullAway"})
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

    static Stream<WithFeaturesParameters> withFeatures__populatesTokensAndAvailability() {
        TestTagProvider provider = new TestTagProvider(TestTag.values());
        List<Set<String>> noFeatures = List.of(Set.of(), Set.of());
        return Stream.of(
                new WithFeaturesParameters(
                        "withTagger_keyOnly",
                        () -> annotatorSequence(1, 1, twoTokenTaggedSequence(), KEY_FEATURES, null),
                        true,
                        KEY_FEATURES,
                        false,
                        noFeatures
                ),
                new WithFeaturesParameters(
                        "withTagger_keyAndVerbose",
                        () -> annotatorSequence(1, 1, twoTokenTaggedSequence(), KEY_FEATURES, VERBOSE_FEATURES),
                        true,
                        KEY_FEATURES,
                        true,
                        VERBOSE_FEATURES
                ),
                new WithFeaturesParameters(
                        "withTagger_verboseOnly",
                        () -> annotatorSequence(1, 1, twoTokenTaggedSequence(), null, VERBOSE_FEATURES),
                        false,
                        noFeatures,
                        true,
                        VERBOSE_FEATURES
                ),
                new WithFeaturesParameters(
                        "withoutTagger_keyOnly",
                        () -> annotatorSequence(1, 1, List.of("the", "fox"), provider, KEY_FEATURES, null),
                        true,
                        KEY_FEATURES,
                        false,
                        noFeatures
                ),
                new WithFeaturesParameters(
                        "withoutTagger_keyAndVerbose",
                        () -> annotatorSequence(1, 1, List.of("the", "fox"), provider, KEY_FEATURES, VERBOSE_FEATURES),
                        true,
                        KEY_FEATURES,
                        true,
                        VERBOSE_FEATURES
                ),
                new WithFeaturesParameters(
                        "withoutTagger_verboseOnly",
                        () -> annotatorSequence(1, 1, List.of("the", "fox"), provider, null, VERBOSE_FEATURES),
                        false,
                        noFeatures,
                        true,
                        VERBOSE_FEATURES
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void withFeatures__populatesTokensAndAvailability(WithFeaturesParameters parameters) {
        // ACT //
        AnnotatorSequence<String, TestTag> sequence = parameters.sequenceSupplier().get();

        // ASSERT //
        assertEquals(parameters.expectedFeaturesAvailable(), sequence.featureAvailability().keyAvailable());
        assertEquals(parameters.expectedVerboseFeaturesAvailable(), sequence.featureAvailability().verboseAvailable());
        assertEquals(parameters.expectedFeatures(), sequence.tokens().stream().map(AnnotatorToken::features).toList());
        assertEquals(
                parameters.expectedVerboseFeatures(),
                sequence.tokens().stream().map(AnnotatorToken::verboseFeatures).toList()
        );
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
        assertEquals(
                FeatureAvailability.NONE,
                sequence.featureAvailability(),
                "embedded tagger features must not enable the feature display"
        );
        assertToken(
                sequence.tokens().getFirst(),
                "the",
                TestTag.ALPHA,
                0.7,
                List.of(TestTag.ALPHA, TestTag.BETA, TestTag.GAMMA),
                0.7
        );
        assertToken(
                sequence.tokens().get(1),
                "fox",
                TestTag.BETA,
                0.6,
                List.of(TestTag.BETA, TestTag.GAMMA, TestTag.ALPHA),
                0.6
        );
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
        assertEquals(FeatureAvailability.NONE, sequence.featureAvailability());
        for (int index = 0; index < tokens.size(); index++) {
            AnnotatorToken<String, TestTag> annotatorToken = sequence.tokens().get(index);
            assertEquals(tokens.get(index), annotatorToken.token());
            assertEquals(Set.of(), annotatorToken.features());
            assertEquals(Set.of(), annotatorToken.verboseFeatures());
            assertEquals(TestTag.ALPHA, annotatorToken.initialTag());
            Assertions.assertNull(annotatorToken.initialConfidence());
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
                ),
                new ImmutabilityParameters(
                        "features_add",
                        () -> immutableFeaturedSequence().tokens().getFirst().features().add("extra")
                ),
                new ImmutabilityParameters(
                        "verboseFeatures_add",
                        () -> immutableFeaturedSequence().tokens().getFirst().verboseFeatures().add("extra")
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void withoutTagger__resultIsImmutable(ImmutabilityParameters parameters) {
        // ACT + ASSERT //
        assertThrowsExactly(UnsupportedOperationException.class, parameters.action());
    }

    private static void assertToken(
            AnnotatorToken<String, TestTag> token,
            String expectedToken,
            TestTag expectedTag,
            Double expectedConfidence,
            List<TestTag> expectedKeyOrder,
            Double expectedTopScore
    ) {
        assertEquals(expectedToken, token.token());
        assertEquals(Set.of(), token.features());
        assertEquals(Set.of(), token.verboseFeatures());
        assertEquals(expectedTag, token.initialTag());
        assertEquals(expectedConfidence, token.initialConfidence());
        assertEquals(expectedKeyOrder, List.copyOf(token.alternativeTagScores().keySet()));
        assertEquals(expectedTopScore, token.alternativeTagScores().get(expectedTag));
    }

    private static AnnotatorSequence<String, TestTag> immutableFeaturedSequence() {
        return annotatorSequence(
                1,
                1,
                List.of("a"),
                new TestTagProvider(TestTag.values()),
                List.of(Set.of("k1")),
                List.of(Set.of("v1"))
        );
    }

    private static AnnotatorSequence<String, TestTag> immutableSequence() {
        return AnnotatorModels.annotatorSequence(1, 1, List.of("a"), new TestTagProvider(TestTag.values()));
    }

    private static TaggedSequence<String, TestTag> singleTokenTaggedSequence() {
        return new TaggedSequence<>(List.of("a"), List.of(Set.of()), List.of(Map.of(TestTag.ALPHA, 1.0)));
    }

    private static TaggedSequence<String, TestTag> twoTokenTaggedSequence() {
        return new TaggedSequence<>(
                List.of("the", "fox"),
                List.of(Set.of("e1"), Set.of("e2")),
                List.of(Map.of(TestTag.ALPHA, 1.0), Map.of(TestTag.BETA, 1.0))
        );
    }
}
