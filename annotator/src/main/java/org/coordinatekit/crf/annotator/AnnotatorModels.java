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
import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.tag.TagScore;
import org.coordinatekit.crf.core.tag.TaggedPositionedToken;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;

/**
 * Factory for the public value types in the {@code ui} package.
 *
 * <p>
 * Each public interface in this package ({@link AnnotatorSequence}, {@link AnnotatorToken},
 * {@link TaggingResult}) is paired with a private record implementation here and a static factory
 * method that constructs and validates instances. Callers should statically import the factory
 * methods.
 */
public final class AnnotatorModels {
    private AnnotatorModels() {}

    /**
     * Creates an {@link AnnotatorSequence} from a tagger's output.
     *
     * <p>
     * Each token's {@link TaggedPositionedToken#token() token}, {@link TaggedPositionedToken#features()
     * features}, top-scoring {@link TaggedPositionedToken#tag() tag} and corresponding score, and full
     * {@link TaggedPositionedToken#tagScores() tag-score} set are projected onto a corresponding
     * {@link AnnotatorToken}. The alternative-tag-score map preserves the score-descending order of
     * {@code tagScores()}.
     *
     * @param sequenceNumber the 1-based position of this sequence within the overall annotation batch
     * @param totalSequences the total number of sequences in the overall annotation batch
     * @param taggedSequence the tagger's output for the sequence
     * @param <F> the feature type
     * @param <T> the tag type
     * @return a new annotator sequence
     * @throws IllegalArgumentException if {@code sequenceNumber < 1} or
     *         {@code totalSequences < sequenceNumber}
     */
    public static <F, T extends Comparable<T>> AnnotatorSequence<F, T> annotatorSequence(
            int sequenceNumber,
            int totalSequences,
            Sequence<TaggedPositionedToken<F, T>> taggedSequence
    ) {
        Objects.requireNonNull(taggedSequence, "taggedSequence must not be null");
        validateSequenceBounds(sequenceNumber, totalSequences);

        List<AnnotatorToken<F, T>> tokens = new ArrayList<>(taggedSequence.size());
        for (TaggedPositionedToken<F, T> token : taggedSequence) {
            SortedSet<TagScore<T>> tagScores = token.tagScores();
            Map<T, @Nullable Double> scoreMap = new LinkedHashMap<>();
            for (TagScore<T> tagScore : tagScores) {
                scoreMap.put(tagScore.tag(), tagScore.score());
            }
            tokens.add(
                    new DefaultAnnotatorToken<>(
                            token.token(),
                            token.features(),
                            token.tag(),
                            tagScores.first().score(),
                            scoreMap
                    )
            );
        }
        return new DefaultAnnotatorSequence<>(sequenceNumber, totalSequences, tokens);
    }

    /**
     * Creates an {@link AnnotatorSequence} for the no-tagger path, where the user will choose tags from
     * scratch.
     *
     * <p>
     * The returned sequence has empty feature sets, {@link TagProvider#startingTag() startingTag} as
     * every initial tag, {@code null} for every initial confidence, and an alternative-tag-score map
     * per token keyed by {@link TagProvider#tags() tagProvider.tags()} (in natural order) with every
     * value {@code null}.
     *
     * @param sequenceNumber the 1-based position of this sequence within the overall annotation batch
     * @param totalSequences the total number of sequences in the overall annotation batch
     * @param tokens the tokens of the sequence
     * @param tagProvider the tag provider, whose {@link TagProvider#tags() tags} set defines the tag
     *        space offered on the edit screen
     * @param <F> the feature type
     * @param <T> the tag type
     * @return a new annotator sequence
     * @throws IllegalArgumentException if {@code sequenceNumber < 1},
     *         {@code totalSequences < sequenceNumber}, {@code tokens} is empty, or
     *         {@code tagProvider.tags()} is empty
     */
    public static <F, T extends Comparable<T>> AnnotatorSequence<F, T> annotatorSequence(
            int sequenceNumber,
            int totalSequences,
            List<String> tokens,
            TagProvider<T> tagProvider
    ) {
        Objects.requireNonNull(tokens, "tokens must not be null");
        Objects.requireNonNull(tagProvider, "tagProvider must not be null");
        validateSequenceBounds(sequenceNumber, totalSequences);
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("tokens must not be empty");
        }
        SortedSet<T> availableTags = tagProvider.tags();
        if (availableTags.isEmpty()) {
            throw new IllegalArgumentException("tagProvider.tags() must not be empty");
        }

        T startingTag = tagProvider.startingTag();
        Map<T, @Nullable Double> nullScoreMap = new LinkedHashMap<>();
        for (T tag : availableTags) {
            nullScoreMap.put(tag, null);
        }
        List<AnnotatorToken<F, T>> annotatorTokens = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            annotatorTokens.add(new DefaultAnnotatorToken<>(token, Set.of(), startingTag, null, nullScoreMap));
        }
        return new DefaultAnnotatorSequence<>(sequenceNumber, totalSequences, annotatorTokens);
    }

    /**
     * Creates a {@link TaggingResult}.
     *
     * <p>
     * The {@code finalTags} list is defensively copied.
     *
     * @param action the action chosen by the user
     * @param finalTags the per-token tags chosen by the user (may be empty)
     * @param <T> the tag type
     * @return a new tagging result
     */
    public static <T> TaggingResult<T> taggingResult(TaggingAction action, List<T> finalTags) {
        return new DefaultTaggingResult<>(action, finalTags);
    }

    private static <T extends Comparable<T>> Map<T, @Nullable Double> canonicallyOrderedScoreMap(
            Map<T, @Nullable Double> scores
    ) {
        List<Map.Entry<T, @Nullable Double>> entries = new ArrayList<>(scores.entrySet());
        entries.sort(
                Comparator.<Map.Entry<T, @Nullable Double>, Boolean>comparing(entry -> entry.getValue() == null)
                        .thenComparing(entry -> entry.getValue() == null ? 0.0 : -entry.getValue())
                        .thenComparing(Map.Entry::getKey)
        );
        Map<T, @Nullable Double> ordered = new LinkedHashMap<>();
        for (Map.Entry<T, @Nullable Double> entry : entries) {
            ordered.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(ordered);
    }

    private static void validateSequenceBounds(int sequenceNumber, int totalSequences) {
        if (sequenceNumber < 1) {
            throw new IllegalArgumentException("sequenceNumber must be at least 1, got: " + sequenceNumber);
        }
        if (totalSequences < sequenceNumber) {
            throw new IllegalArgumentException(
                    "totalSequences must be at least sequenceNumber, got: totalSequences=" + totalSequences
                            + ", sequenceNumber=" + sequenceNumber
            );
        }
    }

    private record DefaultAnnotatorSequence<F, T extends Comparable<T>> (
            int sequenceNumber,
            int totalSequences,
            List<AnnotatorToken<F, T>> tokens
    ) implements AnnotatorSequence<F, T> {
        private DefaultAnnotatorSequence {
            tokens = List.copyOf(tokens);
        }
    }

    private record DefaultAnnotatorToken<F, T extends Comparable<T>> (
            String token,
            Set<F> features,
            T initialTag,
            @Nullable Double initialConfidence,
            Map<T, @Nullable Double> alternativeTagScores
    ) implements AnnotatorToken<F, T> {
        private DefaultAnnotatorToken {
            alternativeTagScores = canonicallyOrderedScoreMap(alternativeTagScores);
        }
    }

    private record DefaultTaggingResult<T> (TaggingAction action, List<T> finalTags) implements TaggingResult<T> {
        private DefaultTaggingResult {
            Objects.requireNonNull(action, "action must not be null");
            Objects.requireNonNull(finalTags, "finalTags must not be null");
            finalTags = List.copyOf(finalTags);
        }
    }
}
