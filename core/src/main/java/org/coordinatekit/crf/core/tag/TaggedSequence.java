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
package org.coordinatekit.crf.core.tag;

import org.coordinatekit.crf.core.Sequence;
import org.jspecify.annotations.NullMarked;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A sequence of tokens that have been tagged by a CRF model with associated features and tag
 * scores.
 *
 * <p>
 * Each token in the sequence includes its extracted features and a set of possible tags ranked by
 * their scores from the CRF model. This allows access to both the most likely tag and alternative
 * tags with their associated probabilities.
 *
 * @param <F> the type of features associated with each token
 * @param <T> the type of tags assigned to tokens must be comparable for ordering
 */
@NullMarked
public class TaggedSequence<F, T extends Comparable<T>> implements Sequence<TaggedPositionedToken<F, T>> {
    private record TaggedSequenceTagScore<T extends Comparable<T>> (T tag, double score) implements TagScore<T> {
        @Override
        public int compareTo(TagScore<T> that) {
            return Comparator.comparingDouble((TagScore<T> ts) -> -1 * ts.score()).thenComparing(TagScore::tag)
                    .compare(this, that);
        }
    }

    private record TaggedSequenceToken<F, T extends Comparable<T>> (
            int position,
            String token,
            Set<F> features,
            SortedSet<TagScore<T>> tagScores
    ) implements TaggedPositionedToken<F, T> {
        @Override
        public T tag() {
            return tagScores().first().tag();
        }

        @Override
        public List<T> tag(int numberOfTags) {
            Stream<TagScore<T>> stream = tagScores().stream();

            if (numberOfTags > 0) {
                stream = stream.limit(numberOfTags);
            }

            return stream.map(TagScore::tag).toList();
        }
    }

    private final List<TaggedPositionedToken<F, T>> tokens;

    /**
     * Creates a new tagged sequence from parallel lists of tokens, features, and tag scores.
     *
     * <p>
     * The three lists must have the same size, with each index representing a single token's data. Tag
     * scores are sorted by score in descending order, with ties broken by tag natural ordering.
     *
     * @param tokens the list of token strings
     * @param features the list of feature sets, one per token
     * @param tagScores the list of tag-to-score mappings, one per token
     * @throws IllegalArgumentException if the lists have different sizes or if the token list is empty
     */
    public TaggedSequence(List<String> tokens, List<Set<F>> features, List<Map<T, Double>> tagScores) {
        if (tokens.size() != features.size()) {
            throw new IllegalArgumentException(
                    String.format(
                            "The number of features must be equal to the number of tokens. (tokens: %d, features: %d)",
                            tokens.size(),
                            features.size()
                    )
            );
        } else if (tokens.size() != tagScores.size()) {
            throw new IllegalArgumentException(
                    String.format(
                            "The number of tag scores must be equal to the number of tokens. (tokens: %d, tag scores: %d)",
                            tokens.size(),
                            tagScores.size()
                    )
            );
        } else if (tokens.isEmpty()) {
            throw new IllegalArgumentException("There must be one or more tokens provided to a tagged sequence.");
        }

        this.tokens = IntStream.range(0, tokens.size())
                .<TaggedPositionedToken<F, T>>mapToObj(
                        index -> new TaggedSequenceToken<>(
                                index,
                                tokens.get(index),
                                Set.copyOf(features.get(index)),
                                tagScores.get(index).entrySet().stream()
                                        .map(entry -> new TaggedSequenceTagScore<>(entry.getKey(), entry.getValue()))
                                        .collect(Collectors.toCollection(TreeSet::new))
                        )
                ).toList();
    }

    @Override
    public TaggedPositionedToken<F, T> get(int position) {
        return tokens.get(position);
    }

    @Override
    public Iterator<TaggedPositionedToken<F, T>> iterator() {
        return tokens.iterator();
    }

    @Override
    public int size() {
        return tokens.size();
    }

    @Override
    public Stream<TaggedPositionedToken<F, T>> stream() {
        return tokens.stream();
    }
}
