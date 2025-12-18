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
package org.coordinatekit.crf.core.preprocessing;

import org.coordinatekit.crf.core.Sequence;
import org.jspecify.annotations.NullMarked;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A sequence of tokens with both extracted features and training labels.
 *
 * <p>
 * This class stores tokens along with their extracted features and training labels. Instances are
 * typically created by {@link FeatureExtractor} during the feature extraction phase for training
 * data. Feature training sequences are used during CRF model training.
 *
 * @param <F> the type of features associated with tokens in this sequence
 * @param <T> the type of tags (labels) associated with tokens in this sequence
 * @see FeatureTrainingPositionedToken
 * @see Sequence
 */
@NullMarked
public class FeatureTrainingSequence<F, T> implements Sequence<FeatureTrainingPositionedToken<F, T>> {
    private record FeatureTrainingSequenceToken<F, T> (int position, String token, Set<F> features, T tag)
            implements FeatureTrainingPositionedToken<F, T> {}

    private final List<FeatureTrainingPositionedToken<F, T>> tokens;

    /**
     * Constructs a new feature training sequence from the given tokens, tags, and features.
     *
     * <p>
     * All three lists must have the same size, where {@code tags.get(i)} and {@code features.get(i)}
     * correspond to {@code tokens.get(i)}.
     *
     * @param tokens the list of token strings in order
     * @param tags the list of training labels, one per token
     * @param features the list of feature sets, one per token
     * @throws IllegalArgumentException if the list of tokens is empty, or if the number of tokens does
     *         not match the number of features or tags
     * @throws IndexOutOfBoundsException if the lists have different sizes
     */
    public FeatureTrainingSequence(List<String> tokens, List<T> tags, List<Set<F>> features) {
        if (tokens.size() != tags.size()) {
            throw new IllegalArgumentException(
                    String.format(
                            "The number of tags must be equal to the number of tokens. (tokens: %d, tags: %d)",
                            tokens.size(),
                            tags.size()
                    )
            );
        } else if (tokens.size() != features.size()) {
            throw new IllegalArgumentException(
                    String.format(
                            "The number of features must be equal to the number of tokens. (tokens: %d, features: %d)",
                            tokens.size(),
                            features.size()
                    )
            );
        } else if (tokens.isEmpty()) {
            throw new IllegalArgumentException(
                    "There must be one or more tokens provided to a feature training sequence."
            );
        }

        this.tokens = IntStream.range(0, tokens.size())
                .<FeatureTrainingPositionedToken<F, T>>mapToObj(
                        index -> new FeatureTrainingSequenceToken<>(
                                index,
                                tokens.get(index),
                                Set.copyOf(features.get(index)),
                                tags.get(index)
                        )
                ).toList();
    }

    @Override
    public FeatureTrainingPositionedToken<F, T> get(int position) {
        return tokens.get(position);
    }

    @Override
    public int size() {
        return tokens.size();
    }

    @Override
    public Stream<FeatureTrainingPositionedToken<F, T>> stream() {
        return tokens.stream();
    }

    @Override
    public Iterator<FeatureTrainingPositionedToken<F, T>> iterator() {
        return tokens.iterator();
    }
}
