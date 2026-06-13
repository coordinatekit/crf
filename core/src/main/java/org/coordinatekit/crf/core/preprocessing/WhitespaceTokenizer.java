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

import static org.coordinatekit.crf.core.preprocessing.Segments.excluded;
import static org.coordinatekit.crf.core.preprocessing.Segments.token;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A tokenizer that splits input on whitespace characters.
 *
 * <p>
 * This implementation splits the input string on one or more consecutive whitespace characters
 * (spaces, tabs, newlines, etc.). The whitespace it splits on is not discarded: each run of
 * whitespace is captured as an {@link SegmentKind#EXCLUDED excluded} segment on the resulting
 * {@link Tokenization}, including any leading or trailing whitespace, so
 * {@link Tokenization#surface()} reproduces the original input exactly.
 *
 * @see Tokenizer
 */
public class WhitespaceTokenizer implements Tokenizer {
    /** Creates a new whitespace tokenizer. */
    public WhitespaceTokenizer() {}

    /**
     * {@inheritDoc}
     *
     * <p>
     * Scans the input into alternating runs of whitespace (captured as {@link SegmentKind#EXCLUDED
     * excluded} segments) and non-whitespace (captured as {@link SegmentKind#TOKEN token} segments),
     * preserving leading and trailing whitespace so the input can be reconstructed exactly. Empty
     * whitespace runs are not emitted as segments.
     *
     * @throws InvalidInputException if the input string is empty or blank
     * @throws NullPointerException if the input string is null
     */
    @Override
    public Tokenization tokenize(String input) {
        Objects.requireNonNull(input, "The input string may not be null.");

        if (input.isEmpty()) {
            throw new InvalidInputException(input, "The input string is empty");
        } else if (input.isBlank()) {
            throw new InvalidInputException(input, "The input string is blank");
        }

        List<Segment> segments = new ArrayList<>();
        int length = input.length();
        int index = 0;

        int leadingStart = index;
        while (index < length && Character.isWhitespace(input.charAt(index))) {
            index++;
        }
        if (index > leadingStart) {
            segments.add(excluded(input.substring(leadingStart, index)));
        }

        while (index < length) {
            int tokenStart = index;
            while (index < length && !Character.isWhitespace(input.charAt(index))) {
                index++;
            }
            segments.add(token(input.substring(tokenStart, index)));

            int whitespaceStart = index;
            while (index < length && Character.isWhitespace(input.charAt(index))) {
                index++;
            }
            if (index > whitespaceStart) {
                segments.add(excluded(input.substring(whitespaceStart, index)));
            }
        }

        return new Tokenization(segments);
    }
}
