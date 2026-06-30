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
import org.coordinatekit.crf.core.preprocessing.Tokenization;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * The full result of tagging an input string: the per-token tags paired with the authoritative
 * {@link Tokenization} the tagger computed.
 *
 * <p>
 * A {@link CrfTagger} tokenizes its input before extracting features, so it already holds the
 * lossless segment decomposition (tokens <em>and</em> excluded runs). This type hands that
 * decomposition back to callers alongside the tagged tokens, so a consumer never has to re-tokenize
 * to recover the excluded runs. The token segments of {@link #tokenization()} line up one-to-one
 * with the entries of {@link #taggedSequence()} in document order.
 *
 * <p>
 * Instances are created through {@link TaggedTokenizations}.
 *
 * @param <T> the type of tags assigned to tokens, must be comparable for ordering
 * @see CrfTagger
 * @see TaggedSequence
 * @see Tokenization
 */
@NullMarked
public interface TaggedTokenization<T extends Comparable<T>> {
    /**
     * Returns the conditional probability {@code P(tags | input)} the model assigns to the given tag
     * sequence over this tagging's input.
     *
     * <p>
     * Where {@link #taggedSequence()} carries the single best tagging, this answers how probable
     * <em>any</em> chosen tagging is, which lets a user-interface recompute a total likelihood as the
     * user revises tags. It is bound to this tagging's input and expects one tag per token of
     * {@link #tokenization()}.
     *
     * <p>
     * The result is the normalized probability of the whole tagging in {@code [0, 1]}: the joint of
     * every token's tag under the model, not an aggregate of per-token marginals. A tagging the model
     * deems impossible scores {@code 0}.
     *
     * @param tags the candidate tag for each token, in token order; must have exactly one entry per
     *        token of this tagging's input
     * @return the conditional probability of {@code tags} given the input, in {@code [0, 1]}
     * @throws NullPointerException if {@code tags} is null
     * @throws IllegalArgumentException if {@code tags} does not have one entry per token of the input
     */
    double probabilityOf(List<T> tags);

    /**
     * Returns the per-token tagging output: each token's features and ranked tag scores.
     *
     * @return the tagged sequence
     */
    Sequence<TaggedPositionedToken<T>> taggedSequence();

    /**
     * Returns the authoritative tokenization, carrying both the token segments and the excluded runs
     * the tokenizer dropped around them.
     *
     * @return the tokenization
     */
    Tokenization tokenization();
}
