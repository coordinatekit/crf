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

import org.coordinatekit.crf.core.PositionedToken;
import org.coordinatekit.crf.core.Sequence;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Extracts features from tokens within a sequence for use in CRF model training and tagging.
 *
 * <p>
 * Implementations define how features are computed for each token based on its context within the
 * sequence. Features typically capture properties such as token prefixes/suffixes, character
 * patterns, neighboring tokens, and domain-specific attributes.
 *
 * @param <F> the type of features produced by this extractor
 */
@NullMarked
public interface FeatureExtractor<F> {

    /**
     * Extracts features for all tokens in a sequence.
     *
     * <p>
     * This default implementation iterates through the sequence and calls
     * {@link #extractAt(Sequence, int)} for each position.
     *
     * @param sequence the input sequence of tokens
     * @return a new sequence with features attached to each token
     */
    default Sequence<FeaturePositionedToken<F>> extract(Sequence<? extends PositionedToken> sequence) {
        List<String> tokens = new ArrayList<>();
        List<Set<F>> featureSequences = new ArrayList<>();

        for (int i = 0; i < sequence.size(); i++) {
            tokens.add(sequence.get(i).token());
            featureSequences.add(extractAt(sequence, i));
        }

        return new FeatureSequence<>(tokens, featureSequences);
    }

    /**
     * Extracts features for the token at the specified position within a sequence.
     *
     * <p>
     * Implementations should use the surrounding context (neighboring tokens) to compute relevant
     * features. The full sequence is provided to allow access to tokens before and after the target
     * position.
     *
     * @param sequence the sequence containing the token
     * @param position the zero-based index of the token to extract features for
     * @return the set of features for the token at the given position
     */
    Set<F> extractAt(Sequence<? extends PositionedToken> sequence, int position);

    /**
     * Extracts features for all tokens in a training sequence, preserving tag information.
     *
     * <p>
     * This default implementation iterates through the sequence and calls
     * {@link #extractAt(Sequence, int)} for each position, while also preserving the tag associated
     * with each token.
     *
     * @param <T> the type of tags in the training sequence
     * @param sequence the input training sequence with tagged tokens
     * @return a new sequence with features and tags attached to each token
     */
    default <T> Sequence<FeatureTrainingPositionedToken<F, T>> extractTraining(
            Sequence<? extends TrainingPositionedToken<T>> sequence
    ) {
        List<String> tokens = new ArrayList<>();
        List<T> tags = new ArrayList<>();
        List<Set<F>> featureSequences = new ArrayList<>();

        for (int i = 0; i < sequence.size(); i++) {
            tokens.add(sequence.get(i).token());
            tags.add(sequence.get(i).tag());
            featureSequences.add(extractAt(sequence, i));
        }

        return new FeatureTrainingSequence<>(tokens, tags, featureSequences);
    }
}
