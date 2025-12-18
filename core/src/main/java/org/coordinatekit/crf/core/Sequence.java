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
package org.coordinatekit.crf.core;

import org.jspecify.annotations.NullMarked;

import java.util.stream.Stream;

/**
 * Represents an ordered sequence of positioned tokens.
 *
 * <p>
 * A sequence is a fundamental abstraction in conditional random field (CRF) processing,
 * representing a series of tokens (such as words in a sentence) where each token has a defined
 * position. Sequences are used throughout the CRF pipeline for training data representation,
 * feature extraction, and tagging.
 *
 * @param <E> the type of positioned token contained in this sequence
 * @see PositionedToken
 * @see InputSequence
 */
@NullMarked
public interface Sequence<E extends PositionedToken> extends Iterable<E> {
    /**
     * Returns the token at the specified position in this sequence.
     *
     * @param position the position of the token to return (zero-based)
     * @return the token at the specified position
     * @throws IndexOutOfBoundsException if the index is out of range
     *         ({@code position < 0 || position >= size()})
     */
    E get(int position);

    /**
     * Returns the number of tokens in this sequence.
     *
     * @return the number of tokens in this sequence
     */
    int size();

    /**
     * Returns a sequential {@code Stream} over the tokens in this sequence.
     *
     * @return a stream of tokens
     */
    Stream<E> stream();
}
