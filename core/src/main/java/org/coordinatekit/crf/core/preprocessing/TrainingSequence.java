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
import org.coordinatekit.crf.core.io.TrainingDataSequencer;
import org.jspecify.annotations.NullMarked;

import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A sequence of tokens with training labels.
 *
 * <p>
 * This class stores tokens along with their training labels. Instances are typically created by
 * {@link TrainingDataSequencer} implementations when reading annotated training data.
 *
 * @param <T> the type of tags (labels) associated with tokens in this sequence
 * @see TrainingPositionedToken
 * @see Sequence
 */
@NullMarked
public class TrainingSequence<T> implements Sequence<TrainingPositionedToken<T>> {
    private record TrainingSequenceToken<T> (int position, T tag, String token) implements TrainingPositionedToken<T> {}

    private final List<TrainingPositionedToken<T>> tokens;

    /**
     * Constructs a new training sequence from the given tokens and tags.
     *
     * <p>
     * The tokens and tags lists must have the same size, where {@code tags.get(i)} corresponds to the
     * label for {@code tokens.get(i)}.
     *
     * @param tokens the list of token strings in order
     * @param tags the list of training labels, one per token
     * @throws IllegalArgumentException if the list of tokens is empty, or if the number of tokens does
     *         not match the number of tags
     * @throws IndexOutOfBoundsException if the lists have different sizes
     */
    public TrainingSequence(List<String> tokens, List<T> tags) {
        if (tokens.size() != tags.size()) {
            throw new IllegalArgumentException(
                    String.format(
                            "The number of tags must be equal to the number of tokens. (tokens: %d, tags: %d)",
                            tokens.size(),
                            tags.size()
                    )
            );
        } else if (tokens.isEmpty()) {
            throw new IllegalArgumentException("There must be one or more tokens provided to a training sequence.");
        }

        this.tokens = IntStream.range(0, tokens.size())
                .<TrainingPositionedToken<T>>mapToObj(
                        index -> new TrainingSequenceToken<>(index, tags.get(index), tokens.get(index))
                ).toList();
    }

    @Override
    public TrainingPositionedToken<T> get(int position) {
        return tokens.get(position);
    }

    @Override
    public Iterator<TrainingPositionedToken<T>> iterator() {
        return tokens.iterator();
    }

    @Override
    public int size() {
        return tokens.size();
    }

    @Override
    public Stream<TrainingPositionedToken<T>> stream() {
        return tokens.stream();
    }
}
