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
package org.coordinatekit.crf.core.align;

import org.coordinatekit.crf.core.preprocessing.TrainingPositionedToken;
import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.coordinatekit.crf.core.preprocessing.Tokenizer;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The alignment result for a single stored training sequence.
 *
 * <p>
 * It carries the stored {@link #sequence()} that was re-tokenized, the {@link #retokenizedTokens()}
 * the tokenizer produced, and the {@link #comparison()} describing how they diverge. Keeping the
 * whole sequence preserves its tags and excluded runs for a later rewrite.
 *
 * @param <T> the tag type of the training data
 * @see AlignmentReport
 * @see AlignmentModels#sequenceAlignment(int, AlignmentStatus, TrainingSequence, List,
 *      TokenComparison, String)
 */
public interface SequenceAlignment<T extends Comparable<T>> {
    /**
     * Returns the comparison of the stored and re-tokenized token lists.
     *
     * <p>
     * Present whenever the surface was tokenizable; it is {@code null} exactly when {@link #status()}
     * is {@link AlignmentStatus#UNTOKENIZABLE}. When non-null, its {@link TokenComparison#aligned()}
     * agrees with {@link #status()}: empty differences for {@link AlignmentStatus#ALIGNED}, at least
     * one difference for {@link AlignmentStatus#MISALIGNED}.
     *
     * @return the token comparison, or {@code null} when the surface was untokenizable
     */
    @Nullable
    TokenComparison comparison();

    /**
     * Returns the reason the tokenizer rejected the surface.
     *
     * <p>
     * This is the {@link Tokenizer}-supplied message, present only when {@link #status()} is
     * {@link AlignmentStatus#UNTOKENIZABLE}; it is {@code null} for every other status.
     *
     * @return the failure reason, or {@code null} when the surface was tokenizable
     */
    @Nullable
    String failureReason();

    /**
     * Returns whether this sequence is aligned with the tokenizer.
     *
     * @return {@code true} iff {@link #status()} is {@link AlignmentStatus#ALIGNED}
     */
    default boolean isAligned() {
        return status() == AlignmentStatus.ALIGNED;
    }

    /**
     * Returns the tokens produced by re-tokenizing the {@link TrainingSequence#surface() surface} of
     * {@link #sequence()}.
     *
     * @return the re-tokenized tokens in order, or an empty list when
     *         {@link AlignmentStatus#UNTOKENIZABLE}
     */
    List<String> retokenizedTokens();

    /**
     * Returns the stored training sequence this alignment was computed from.
     *
     * <p>
     * Carrying the whole sequence keeps its tags and excluded runs available for a later rewrite;
     * {@link #storedTokens()} is derived from it.
     *
     * @return the stored training sequence
     */
    TrainingSequence<T> sequence();

    /**
     * Returns the zero-based index of this sequence within its source file, in document order.
     *
     * @return the sequence index
     */
    int sequenceIndex();

    /**
     * Returns the alignment outcome for this sequence.
     *
     * @return the status
     */
    AlignmentStatus status();

    /**
     * Returns the tokens stored in the training data for this sequence.
     *
     * <p>
     * Derived from {@link #sequence()}.
     *
     * @return the stored tokens in order
     */
    default List<String> storedTokens() {
        return sequence().stream().map(TrainingPositionedToken::token).toList();
    }
}
