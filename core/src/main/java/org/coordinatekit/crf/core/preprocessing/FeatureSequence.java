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
 * A sequence of tokens with extracted features.
 *
 * <p>
 * This class stores tokens along with their extracted features. Instances are typically created by
 * {@link FeatureExtractor} during the feature extraction phase. Feature sequences are used during
 * both training and inference to provide the CRF model with the information needed to make
 * predictions.
 *
 * @param <F> the type of features associated with tokens in this sequence
 * @see FeaturePositionedToken
 * @see Sequence
 */
@NullMarked
public class FeatureSequence<F> implements Sequence<FeaturePositionedToken<F>> {
    private record FeatureSequenceToken<F> (int position, String token, Set<F> features)
            implements FeaturePositionedToken<F> {}

    private final List<FeaturePositionedToken<F>> tokens;

    /**
     * Constructs a new feature sequence from the given tokens and features.
     *
     * <p>
     * The tokens and features lists must have the same size, where {@code features.get(i)} corresponds
     * to the features for {@code tokens.get(i)}.
     *
     * @param tokens the list of token strings in order
     * @param features the list of feature sets, one per token
     * @throws IllegalArgumentException if the list of tokens is empty, or if the number of tokens does
     *         not match the number of features
     * @throws IndexOutOfBoundsException if the lists have different sizes
     */
    public FeatureSequence(List<String> tokens, List<Set<F>> features) {
        if (tokens.size() != features.size()) {
            throw new IllegalArgumentException(
                    String.format(
                            "The number of features must be equal to the number of tokens. (tokens: %d, features: %d)",
                            tokens.size(),
                            features.size()
                    )
            );
        } else if (tokens.isEmpty()) {
            throw new IllegalArgumentException("There must be one or more tokens provided to a feature sequence.");
        }

        this.tokens = IntStream.range(0, tokens.size())
                .<FeaturePositionedToken<F>>mapToObj(
                        index -> new FeatureSequenceToken<>(index, tokens.get(index), Set.copyOf(features.get(index)))
                ).toList();
    }

    @Override
    public FeaturePositionedToken<F> get(int position) {
        return tokens.get(position);
    }

    @Override
    public int size() {
        return tokens.size();
    }

    @Override
    public Stream<FeaturePositionedToken<F>> stream() {
        return tokens.stream();
    }

    @Override
    public Iterator<FeaturePositionedToken<F>> iterator() {
        return tokens.iterator();
    }
}
