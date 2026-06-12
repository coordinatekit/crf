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
 * @param <F> the type of features associated with each token
 * @param <T> the type of tags assigned to tokens, must be comparable for ordering
 * @see CrfTagger
 * @see TaggedSequence
 * @see Tokenization
 */
@NullMarked
public interface TaggedTokenization<F, T extends Comparable<T>> {
    /**
     * Returns the per-token tagging output: each token's features and ranked tag scores.
     *
     * @return the tagged sequence
     */
    Sequence<TaggedPositionedToken<F, T>> taggedSequence();

    /**
     * Returns the authoritative tokenization, carrying both the token segments and the excluded runs
     * the tokenizer dropped around them.
     *
     * @return the tokenization
     */
    Tokenization tokenization();
}
