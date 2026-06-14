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

import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Factory for the public value types and the default strategy in the {@code align} package.
 *
 * <p>
 * Each public value type in this package ({@link SequenceAlignment}, {@link TokenComparison},
 * {@link TokenDifference}) is paired with a private record implementation here and a static factory
 * method that constructs and validates instances. The default {@link AlignmentStrategy} is also
 * obtained here, via {@link #exactMatchStrategy()}. Callers should statically import the factory
 * methods.
 */
public final class AlignmentModels {
    private static final AlignmentStrategy EXACT_MATCH_STRATEGY = AlignmentModels::compareExactMatch;

    private AlignmentModels() {}

    /**
     * Compares two token lists by exact, element-for-element prefix equality.
     *
     * <p>
     * The lists are aligned only when equal. Otherwise the first index at which they differ — or the
     * shorter length when one is a strict prefix of the other — opens a single divergent region that
     * spans the remainder of both lists. The region is an {@link DifferenceKind#INSERTION} when the
     * stored list is exhausted, a {@link DifferenceKind#DELETION} when the re-tokenized list is, and a
     * {@link DifferenceKind#REPLACEMENT} otherwise.
     *
     * @param storedTokens the tokens stored in the training data
     * @param retokenizedTokens the tokens produced by re-tokenizing the surface
     * @return the comparison, with an empty difference list when the lists are equal
     */
    private static TokenComparison compareExactMatch(List<String> storedTokens, List<String> retokenizedTokens) {
        int shared = Math.min(storedTokens.size(), retokenizedTokens.size());
        int divergeAt = -1;
        for (int position = 0; position < shared; position++) {
            if (!storedTokens.get(position).equals(retokenizedTokens.get(position))) {
                divergeAt = position;
                break;
            }
        }
        if (divergeAt < 0) {
            if (storedTokens.size() == retokenizedTokens.size()) {
                return tokenComparison(List.of());
            }
            divergeAt = shared;
        }

        DifferenceKind kind;
        if (divergeAt == storedTokens.size()) {
            kind = DifferenceKind.INSERTION;
        } else if (divergeAt == retokenizedTokens.size()) {
            kind = DifferenceKind.DELETION;
        } else {
            kind = DifferenceKind.REPLACEMENT;
        }
        TokenDifference difference = tokenDifference(
                kind,
                divergeAt,
                storedTokens.size(),
                divergeAt,
                retokenizedTokens.size()
        );
        return tokenComparison(List.of(difference));
    }

    /**
     * Returns the default exact-match strategy.
     *
     * <p>
     * It treats two token lists as aligned only when they are element-for-element equal; see
     * {@link #compareExactMatch(List, List)} for how it shapes the divergence otherwise. The returned
     * strategy is stateless and safe to share.
     *
     * @return the exact-match alignment strategy
     */
    public static AlignmentStrategy exactMatchStrategy() {
        return EXACT_MATCH_STRATEGY;
    }

    /**
     * Creates a {@link SequenceAlignment}.
     *
     * <p>
     * The {@code retokenizedTokens} list is defensively copied. The alignment carries its
     * {@code sequence}, from which {@link SequenceAlignment#storedTokens()} is derived. The cross-field
     * invariants between {@code status}, {@code comparison}, and {@code failureReason} are enforced: an
     * {@link AlignmentStatus#UNTOKENIZABLE} alignment has a null comparison and a non-null failure
     * reason; an {@link AlignmentStatus#ALIGNED} or {@link AlignmentStatus#MISALIGNED} alignment has a
     * non-null comparison, a null failure reason, and a comparison whose
     * {@link TokenComparison#aligned()} agrees with the status.
     *
     * @param sequenceIndex the zero-based index of the sequence within its source file
     * @param status the alignment outcome
     * @param sequence the stored training sequence being aligned
     * @param retokenizedTokens the tokens produced by re-tokenizing the surface
     * @param comparison the comparison of the two token lists, or {@code null} when the surface was
     *        untokenizable
     * @param failureReason the tokenizer's rejection message, or {@code null} when the surface was
     *        tokenizable
     * @param <T> the tag type of the training data
     * @return a new sequence alignment
     * @throws IllegalArgumentException if {@code sequenceIndex} is negative, or if the
     *         status/comparison/failureReason invariants are violated
     * @throws NullPointerException if {@code status}, {@code sequence}, or {@code retokenizedTokens} is
     *         null
     */
    public static <T extends Comparable<T>> SequenceAlignment<T> sequenceAlignment(
            int sequenceIndex,
            AlignmentStatus status,
            TrainingSequence<T> sequence,
            List<String> retokenizedTokens,
            @Nullable TokenComparison comparison,
            @Nullable String failureReason
    ) {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(sequence, "sequence must not be null");
        Objects.requireNonNull(retokenizedTokens, "retokenizedTokens must not be null");
        if (sequenceIndex < 0) {
            throw new IllegalArgumentException("sequenceIndex must not be negative, got: " + sequenceIndex);
        }
        if (status == AlignmentStatus.UNTOKENIZABLE) {
            if (comparison != null) {
                throw new IllegalArgumentException("comparison must be null when status is UNTOKENIZABLE");
            }
            if (failureReason == null) {
                throw new IllegalArgumentException("failureReason must not be null when status is UNTOKENIZABLE");
            }
        } else {
            if (comparison == null) {
                throw new IllegalArgumentException("comparison must not be null when status is " + status);
            }
            if (failureReason != null) {
                throw new IllegalArgumentException("failureReason must be null when status is " + status);
            }
            boolean expectedAligned = status == AlignmentStatus.ALIGNED;
            if (comparison.aligned() != expectedAligned) {
                throw new IllegalArgumentException(
                        "comparison.aligned() must be " + expectedAligned + " when status is " + status
                );
            }
        }
        return new DefaultSequenceAlignment<>(
                sequenceIndex,
                status,
                sequence,
                List.copyOf(retokenizedTokens),
                comparison,
                failureReason
        );
    }

    /**
     * Creates a {@link TokenComparison}.
     *
     * <p>
     * The {@code differences} list is defensively copied. An empty list denotes aligned token lists.
     *
     * <p>
     * Beyond the per-span checks enforced by
     * {@link #tokenDifference(DifferenceKind, int, int, int, int)}, the list as a whole must be
     * mutually coherent under endpoint arithmetic. The differences are taken to be ordered, with the
     * matched gap between consecutive regions equal-length on both sides. Concretely: the first
     * difference must start at equal stored and re-tokenized indices (the matched prefix before the
     * first divergence is equal-length on both sides); and for each subsequent difference the matched
     * gap since the previous region — {@code storedStart - previousStoredEnd} versus
     * {@code retokenizedStart - previousRetokenizedEnd} — must be non-negative (regions do not overlap)
     * and equal on both sides. Anchors may otherwise drift between the two lists as insertions and
     * deletions accumulate. Checks that need the token lists themselves (suffix-length equality,
     * in-bounds endpoints, gap content equality) are enforced later at
     * {@link #sequenceAlignment(int, AlignmentStatus, TrainingSequence, List, TokenComparison, String)},
     * the only factory holding both the comparison and the token lists.
     *
     * @param differences the divergent regions in order
     * @return a new token comparison
     * @throws IllegalArgumentException if the first difference does not start at equal stored and
     *         re-tokenized indices, if two consecutive differences overlap, or if a matched gap differs
     *         in length between the two lists
     * @throws NullPointerException if {@code differences} is null
     */
    public static TokenComparison tokenComparison(List<TokenDifference> differences) {
        Objects.requireNonNull(differences, "differences must not be null");
        for (int index = 0; index < differences.size(); index++) {
            TokenDifference difference = differences.get(index);
            if (index == 0) {
                if (difference.storedStart() != difference.retokenizedStart()) {
                    throw new IllegalArgumentException(
                            "first difference must start at equal stored and re-tokenized indices, got: "
                                    + difference.storedStart() + " != " + difference.retokenizedStart()
                    );
                }
            } else {
                TokenDifference previous = differences.get(index - 1);
                int storedGap = difference.storedStart() - previous.storedEnd();
                int retokenizedGap = difference.retokenizedStart() - previous.retokenizedEnd();
                if (storedGap < 0) {
                    throw new IllegalArgumentException(
                            "differences must not overlap, got stored gap: " + storedGap + " at difference index "
                                    + index
                    );
                }
                if (storedGap != retokenizedGap) {
                    throw new IllegalArgumentException(
                            "matched gap before difference index " + index
                                    + " must have equal length on both sides, got: " + storedGap + " != "
                                    + retokenizedGap
                    );
                }
            }
        }
        return new DefaultTokenComparison(List.copyOf(differences));
    }

    /**
     * Creates a {@link TokenDifference}.
     *
     * <p>
     * Spans are half-open {@code [start, end)} indices into the stored and re-tokenized token lists.
     * They must be non-negative with {@code end >= start}, and their emptiness must match {@code kind}:
     * an {@link DifferenceKind#INSERTION} has an empty stored span, a {@link DifferenceKind#DELETION}
     * has an empty re-tokenized span, and a {@link DifferenceKind#REPLACEMENT} has both spans
     * non-empty.
     *
     * @param kind the kind of difference
     * @param storedStart the inclusive start index within the stored list
     * @param storedEnd the exclusive end index within the stored list
     * @param retokenizedStart the inclusive start index within the re-tokenized list
     * @param retokenizedEnd the exclusive end index within the re-tokenized list
     * @return a new token difference
     * @throws IllegalArgumentException if a span is negative, an end precedes its start, or the span
     *         emptiness does not match {@code kind}
     * @throws NullPointerException if {@code kind} is null
     */
    public static TokenDifference tokenDifference(
            DifferenceKind kind,
            int storedStart,
            int storedEnd,
            int retokenizedStart,
            int retokenizedEnd
    ) {
        Objects.requireNonNull(kind, "kind must not be null");
        if (storedStart < 0) {
            throw new IllegalArgumentException("storedStart must not be negative, got: " + storedStart);
        }
        if (storedEnd < storedStart) {
            throw new IllegalArgumentException(
                    "storedEnd must not be less than storedStart, got: " + storedEnd + " < " + storedStart
            );
        }
        if (retokenizedStart < 0) {
            throw new IllegalArgumentException("retokenizedStart must not be negative, got: " + retokenizedStart);
        }
        if (retokenizedEnd < retokenizedStart) {
            throw new IllegalArgumentException(
                    "retokenizedEnd must not be less than retokenizedStart, got: " + retokenizedEnd + " < "
                            + retokenizedStart
            );
        }
        boolean storedEmpty = storedStart == storedEnd;
        boolean retokenizedEmpty = retokenizedStart == retokenizedEnd;
        switch (kind) {
            case INSERTION -> {
                if (!storedEmpty || retokenizedEmpty) {
                    throw new IllegalArgumentException(
                            "INSERTION requires an empty stored span and a non-empty re-tokenized span"
                    );
                }
            }
            case DELETION -> {
                if (storedEmpty || !retokenizedEmpty) {
                    throw new IllegalArgumentException(
                            "DELETION requires a non-empty stored span and an empty re-tokenized span"
                    );
                }
            }
            case REPLACEMENT -> {
                if (storedEmpty || retokenizedEmpty) {
                    throw new IllegalArgumentException("REPLACEMENT requires non-empty stored and re-tokenized spans");
                }
            }
        }
        return new DefaultTokenDifference(kind, storedStart, storedEnd, retokenizedStart, retokenizedEnd);
    }

    private record DefaultSequenceAlignment<T extends Comparable<T>> (
            int sequenceIndex,
            AlignmentStatus status,
            TrainingSequence<T> sequence,
            List<String> retokenizedTokens,
            @Nullable TokenComparison comparison,
            @Nullable String failureReason
    ) implements SequenceAlignment<T> {}

    private record DefaultTokenComparison(List<TokenDifference> differences) implements TokenComparison {}

    private record DefaultTokenDifference(
            DifferenceKind kind,
            int storedStart,
            int storedEnd,
            int retokenizedStart,
            int retokenizedEnd
    ) implements TokenDifference {}
}
