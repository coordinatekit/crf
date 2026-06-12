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

import java.util.Objects;

/**
 * Factory for {@link TaggedTokenization} instances.
 *
 * <p>
 * {@link TaggedTokenization} is a public interface backed by a private record here, with a static
 * factory method that constructs and validates instances. Callers should statically import the
 * factory method.
 *
 * @see TaggedTokenization
 */
@NullMarked
public final class TaggedTokenizations {
    private TaggedTokenizations() {}

    /**
     * Creates a tagged tokenization pairing a tagged sequence with the tokenization it came from.
     *
     * <p>
     * The token segments of {@code tokenization} must line up one-to-one with the entries of
     * {@code taggedSequence} in document order; this method rejects a tagged sequence whose size
     * differs from the tokenization's token count.
     *
     * @param taggedSequence the per-token tagging output
     * @param tokenization the authoritative tokenization carrying tokens and excluded runs
     * @param <F> the type of features associated with each token
     * @param <T> the type of tags assigned to tokens, must be comparable for ordering
     * @return a new tagged tokenization
     * @throws NullPointerException if {@code taggedSequence} or {@code tokenization} is null
     * @throws IllegalArgumentException if {@code taggedSequence} does not have exactly one entry per
     *         token segment of {@code tokenization}
     */
    public static <F, T extends Comparable<T>> TaggedTokenization<F, T> of(
            Sequence<TaggedPositionedToken<F, T>> taggedSequence,
            Tokenization tokenization
    ) {
        return new DefaultTaggedTokenization<>(taggedSequence, tokenization);
    }

    private record DefaultTaggedTokenization<F, T extends Comparable<T>> (
            Sequence<TaggedPositionedToken<F, T>> taggedSequence,
            Tokenization tokenization
    ) implements TaggedTokenization<F, T> {
        private DefaultTaggedTokenization {
            Objects.requireNonNull(taggedSequence, "taggedSequence must not be null");
            Objects.requireNonNull(tokenization, "tokenization must not be null");
            int tokenCount = tokenization.sequence().size();
            if (taggedSequence.size() != tokenCount) {
                throw new IllegalArgumentException(
                        String.format(
                                "The tagged sequence must have one entry per token segment: "
                                        + "got %d tagged tokens for %d token segments.",
                                taggedSequence.size(),
                                tokenCount
                        )
                );
            }
        }
    }
}
