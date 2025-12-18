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

import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A sequence of tokens representing user input for tagging.
 *
 * <p>
 * This class converts a list of strings into a sequence of {@link PositionedToken} objects, where
 * each token is assigned a zero-based position corresponding to its index in the original list.
 *
 * <p>
 * Example usage:
 *
 * <pre>
 * {
 *     &#64;code
 *     List<String> words = List.of("Hello", "world");
 *     Sequence<PositionedToken> sequence = new InputSequence(words);
 *     // sequence.get(0).token() returns "Hello"
 *     // sequence.get(1).token() returns "world"
 * }
 * </pre>
 *
 * @see Sequence
 * @see PositionedToken
 */
@NullMarked
public class InputSequence implements Sequence<PositionedToken> {
    private record SimplePositionedToken(int position, String token) implements PositionedToken {}

    private final List<PositionedToken> tokens;

    /**
     * Creates a new sequence from the given list of string tokens.
     *
     * <p>
     * Each string is wrapped in a {@link PositionedToken} with its position set to its index in the
     * input list.
     *
     * @param tokens the list of string tokens to wrap; must not be null
     * @throws IllegalArgumentException if the list of tokens is empty
     */
    public InputSequence(List<String> tokens) {
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("There must be one or more tokens provided to an input sequence.");
        }

        this.tokens = IntStream.range(0, tokens.size())
                .<PositionedToken>mapToObj(index -> new SimplePositionedToken(index, tokens.get(index))).toList();
    }

    @Override
    public PositionedToken get(int position) {
        return tokens.get(position);
    }

    @Override
    public int size() {
        return tokens.size();
    }

    @Override
    public Stream<PositionedToken> stream() {
        return tokens.stream();
    }

    @Override
    public Iterator<PositionedToken> iterator() {
        return tokens.iterator();
    }
}
