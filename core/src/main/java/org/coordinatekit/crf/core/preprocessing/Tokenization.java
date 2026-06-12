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

import org.coordinatekit.crf.core.InputSequence;
import org.coordinatekit.crf.core.PositionedToken;
import org.coordinatekit.crf.core.Sequence;

import java.util.ArrayList;
import java.util.List;

/**
 * The result of tokenizing raw text: an ordered list of {@link Segment segments} recording every
 * token and every excluded character run a {@link Tokenizer} dropped around them.
 *
 * <p>
 * A {@code Tokenization} is the authoritative, lossless record of how a {@link Tokenizer} split an
 * input string. The segments preserve the full surface in document order — each is either a
 * {@link SegmentKind#TOKEN token} or an {@link SegmentKind#EXCLUDED excluded} run — so
 * {@link #surface()} reconstructs the original string exactly. {@link #sequence()} hands out the
 * token-only projection consumed by feature extraction and the rest of the CRF pipeline, with each
 * token's position equal to its index among the token segments.
 *
 * <p>
 * Unlike its sibling {@link TrainingSequence}, a {@code Tokenization} is not itself a
 * {@link Sequence}: it <em>holds</em> the token projection and exposes it through
 * {@link #sequence()} rather than by inheritance, so {@code segments()} and {@code sequence()} are
 * never confused.
 *
 * @see Tokenizer
 * @see Segment
 * @see Sequence
 */
public final class Tokenization {
    private final List<Segment> segments;
    private final Sequence<PositionedToken> tokens;

    /**
     * Creates a tokenization from an ordered list of segments, computing the token-only projection.
     *
     * @param segments the ordered segments, mixing {@link SegmentKind#TOKEN} and
     *        {@link SegmentKind#EXCLUDED} segments; must contain at least one token segment
     * @throws IllegalArgumentException if no token segment is present
     * @throws NullPointerException if {@code segments} or any segment is null
     */
    public Tokenization(List<Segment> segments) {
        this.segments = List.copyOf(segments);

        List<String> tokenTexts = new ArrayList<>();
        for (Segment segment : this.segments) {
            if (segment.kind() == SegmentKind.TOKEN) {
                tokenTexts.add(segment.text());
            }
        }
        if (tokenTexts.isEmpty()) {
            throw new IllegalArgumentException("There must be one or more tokens provided to a tokenization.");
        }
        this.tokens = new InputSequence(tokenTexts);
    }

    /**
     * Returns the ordered segments backing this tokenization, including any excluded runs.
     *
     * @return an unmodifiable list of segments in document order
     */
    public List<Segment> segments() {
        return segments;
    }

    /**
     * Returns the token-only projection of this tokenization.
     *
     * <p>
     * The projection ranges over {@link SegmentKind#TOKEN} segments alone, in document order, with each
     * token's {@link PositionedToken#position() position} equal to its index among the token segments.
     * Excluded segments never shift token positions.
     *
     * @return the token-only view
     */
    public Sequence<PositionedToken> sequence() {
        return tokens;
    }

    /**
     * Reconstructs the original surface string by concatenating every segment's text in document order.
     *
     * <p>
     * For a tokenizer that captures every dropped character, {@code surface()} returns exactly the
     * string that was tokenized.
     *
     * @return the reconstructed surface string
     */
    public String surface() {
        return Segments.surface(segments);
    }
}
