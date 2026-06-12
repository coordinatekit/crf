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

import static org.coordinatekit.crf.core.preprocessing.TrainingSegments.token;

import org.coordinatekit.crf.core.Sequence;
import org.coordinatekit.crf.core.io.TrainingDataSequencer;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A sequence of tagged tokens, backed by an ordered list of {@link TrainingSegment segments} that
 * also preserves the excluded characters dropped around the tokens.
 *
 * <p>
 * Instances are typically created by {@link TrainingDataSequencer} implementations when reading
 * annotated training data. The segment model records the full surface of an example: each segment
 * is either a {@link SegmentKind#TOKEN token} or an {@link SegmentKind#EXCLUDED excluded} run, and
 * {@link #surface()} reconstructs the original text by concatenating them in order.
 *
 * <p>
 * As a {@link Sequence}, this type exposes only the token-only projection: {@link #get(int)},
 * {@link #size()}, {@link #stream()}, and {@link #iterator()} range over {@link SegmentKind#TOKEN}
 * segments alone, and each token's {@link TrainingPositionedToken#position() position} is its index
 * among the token segments. Excluded segments therefore never shift token positions, keeping the
 * token view behaviorally identical to a sequence built from bare {@code (tokens, tags)}.
 *
 * @param <T> the type of tags (labels) associated with tokens in this sequence
 * @see TrainingSegment
 * @see TrainingPositionedToken
 * @see Sequence
 */
@NullMarked
public class TrainingSequence<T> implements Sequence<TrainingPositionedToken<T>> {
    private record TrainingSequenceToken<T> (int position, T tag, String token) implements TrainingPositionedToken<T> {}

    private final List<TrainingSegment<T>> segments;
    private final List<TrainingPositionedToken<T>> tokens;

    /**
     * Constructs a training sequence from segments, computing the token-only projection.
     *
     * @param segments the ordered segments; must contain at least one {@link SegmentKind#TOKEN} segment
     * @throws IllegalArgumentException if no token segment is present
     */
    private TrainingSequence(List<TrainingSegment<T>> segments) {
        this.segments = List.copyOf(segments);

        List<TrainingPositionedToken<T>> projection = new ArrayList<>();
        for (TrainingSegment<T> segment : this.segments) {
            if (segment.kind() == SegmentKind.TOKEN) {
                T tag = Objects.requireNonNull(segment.tag());
                projection.add(new TrainingSequenceToken<>(projection.size(), tag, segment.text()));
            }
        }
        if (projection.isEmpty()) {
            throw new IllegalArgumentException("There must be one or more tokens provided to a training sequence.");
        }
        this.tokens = List.copyOf(projection);
    }

    /**
     * Constructs a new training sequence from the given tokens and tags, with no excluded runs.
     *
     * <p>
     * The tokens and tags lists must have the same size, where {@code tags.get(i)} corresponds to the
     * label for {@code tokens.get(i)}.
     *
     * @param tokens the list of token strings in order
     * @param tags the list of training labels, one per token
     * @throws IllegalArgumentException if the list of tokens is empty, or if the number of tokens does
     *         not match the number of tags
     * @deprecated since 0.2.0 — this token-only constructor invents no excluded runs, so the resulting
     *             {@link #surface()} is the bare token concatenation. Prefer {@link #ofSegments(List)}
     *             to preserve excluded characters, or {@link #ofTokens(List, List)} when no excluded
     *             runs exist.
     */
    @Deprecated(since = "0.2.0")
    public TrainingSequence(List<String> tokens, List<T> tags) {
        this(tokenSegments(tokens, tags));
    }

    /**
     * Creates a training sequence from an ordered list of segments.
     *
     * @param segments the ordered segments, mixing {@link SegmentKind#TOKEN} and
     *        {@link SegmentKind#EXCLUDED} segments; must contain at least one token segment
     * @param <T> the tag type
     * @return a new training sequence
     * @throws IllegalArgumentException if no token segment is present
     */
    public static <T> TrainingSequence<T> ofSegments(List<TrainingSegment<T>> segments) {
        return new TrainingSequence<>(segments);
    }

    /**
     * Creates a training sequence from tokens and tags, with no excluded runs.
     *
     * <p>
     * The resulting {@link #surface()} is the bare concatenation of the tokens, with no separators.
     *
     * @param tokens the list of token strings in order
     * @param tags the list of training labels, one per token
     * @param <T> the tag type
     * @return a new training sequence
     * @throws IllegalArgumentException if the list of tokens is empty, or if the number of tokens does
     *         not match the number of tags
     */
    public static <T> TrainingSequence<T> ofTokens(List<String> tokens, List<T> tags) {
        return new TrainingSequence<>(tokenSegments(tokens, tags));
    }

    /**
     * Builds token-only segments from parallel token and tag lists, validating their sizes.
     *
     * @param tokens the token strings
     * @param tags the tags, one per token
     * @param <T> the tag type
     * @return token segments in order
     * @throws IllegalArgumentException if the lists differ in size
     */
    private static <T> List<TrainingSegment<T>> tokenSegments(List<String> tokens, List<T> tags) {
        if (tokens.size() != tags.size()) {
            throw new IllegalArgumentException(
                    String.format(
                            "The number of tags must be equal to the number of tokens. (tokens: %d, tags: %d)",
                            tokens.size(),
                            tags.size()
                    )
            );
        }
        List<TrainingSegment<T>> segments = new ArrayList<>(tokens.size());
        for (int index = 0; index < tokens.size(); index++) {
            segments.add(token(tags.get(index), tokens.get(index)));
        }
        return segments;
    }

    @Override
    public TrainingPositionedToken<T> get(int position) {
        return tokens.get(position);
    }

    @Override
    public Iterator<TrainingPositionedToken<T>> iterator() {
        return tokens.iterator();
    }

    /**
     * Returns the ordered segments backing this sequence, including any excluded runs.
     *
     * @return an unmodifiable list of segments in document order
     */
    public List<TrainingSegment<T>> segments() {
        return segments;
    }

    @Override
    public int size() {
        return tokens.size();
    }

    @Override
    public Stream<TrainingPositionedToken<T>> stream() {
        return tokens.stream();
    }

    /**
     * Reconstructs the surface text of this sequence by concatenating every segment's text in document
     * order.
     *
     * @return the reconstructed surface string
     */
    public String surface() {
        return Segments.surface(segments);
    }
}
