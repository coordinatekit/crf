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

import static org.coordinatekit.crf.core.align.AlignmentModels.exactMatchStrategy;
import static org.coordinatekit.crf.core.align.AlignmentModels.sequenceAlignment;
import static org.coordinatekit.crf.core.align.AlignmentModels.tokenComparison;
import static org.coordinatekit.crf.core.align.AlignmentModels.tokenDifference;
import static org.coordinatekit.crf.core.align.AlignmentTestSupport.assertThrowsWithMessage;
import static org.coordinatekit.crf.core.align.AlignmentTestSupport.sequenceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.coordinatekit.crf.core.align.AlignmentTestSupport.ExceptionCase;
import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

class AlignmentModelsTest {
    record CompareParameters(
            String name,
            List<String> storedTokens,
            List<String> retokenizedTokens,
            List<TokenDifference> expectedDifferences
    ) {}

    static Stream<CompareParameters> exactMatchStrategy__compare() {
        return Stream.of(
                new CompareParameters("equal", List.of("a", "b"), List.of("a", "b"), List.of()),
                new CompareParameters("bothEmpty", List.of(), List.of(), List.of()),
                new CompareParameters(
                        "interiorReplacement",
                        List.of("a", "b", "c"),
                        List.of("a", "x", "c"),
                        List.of(tokenDifference(DifferenceKind.REPLACEMENT, 1, 3, 1, 3))
                ),
                new CompareParameters(
                        "storedPrefixInsertion",
                        List.of("a"),
                        List.of("a", "b"),
                        List.of(tokenDifference(DifferenceKind.INSERTION, 1, 1, 1, 2))
                ),
                new CompareParameters(
                        "retokenizedPrefixDeletion",
                        List.of("a", "b"),
                        List.of("a"),
                        List.of(tokenDifference(DifferenceKind.DELETION, 1, 2, 1, 1))
                ),
                new CompareParameters(
                        "emptyStoredInsertion",
                        List.of(),
                        List.of("a"),
                        List.of(tokenDifference(DifferenceKind.INSERTION, 0, 0, 0, 1))
                ),
                new CompareParameters(
                        "merge",
                        List.of("Salt", "Lake"),
                        List.of("SaltLake"),
                        List.of(tokenDifference(DifferenceKind.REPLACEMENT, 0, 2, 0, 1))
                ),
                new CompareParameters(
                        "split",
                        List.of("New York"),
                        List.of("New", "York"),
                        List.of(tokenDifference(DifferenceKind.REPLACEMENT, 0, 1, 0, 2))
                )
        );
    }

    @SuppressWarnings("DataFlowIssue")
    static Stream<ExceptionCase> sequenceAlignment__exception() {
        return Stream.of(
                new ExceptionCase(
                        "negativeIndex",
                        () -> sequenceAlignment(
                                -1,
                                AlignmentStatus.ALIGNED,
                                sequenceOf("x"),
                                List.of("x"),
                                tokenComparison(List.of()),
                                null
                        ),
                        IllegalArgumentException.class,
                        "sequenceIndex must not be negative, got: -1"
                ),
                new ExceptionCase(
                        "nullStatus",
                        () -> sequenceAlignment(
                                0,
                                null,
                                sequenceOf("x"),
                                List.of("x"),
                                tokenComparison(List.of()),
                                null
                        ),
                        NullPointerException.class,
                        "status must not be null"
                ),
                new ExceptionCase(
                        "nullSequence",
                        () -> sequenceAlignment(
                                0,
                                AlignmentStatus.ALIGNED,
                                (TrainingSequence<String>) null,
                                List.of("x"),
                                tokenComparison(List.of()),
                                null
                        ),
                        NullPointerException.class,
                        "sequence must not be null"
                ),
                new ExceptionCase(
                        "nullRetokenizedTokens",
                        () -> sequenceAlignment(
                                0,
                                AlignmentStatus.ALIGNED,
                                sequenceOf("x"),
                                null,
                                tokenComparison(List.of()),
                                null
                        ),
                        NullPointerException.class,
                        "retokenizedTokens must not be null"
                ),
                new ExceptionCase(
                        "alignedWithNullComparison",
                        () -> sequenceAlignment(0, AlignmentStatus.ALIGNED, sequenceOf("x"), List.of("x"), null, null),
                        IllegalArgumentException.class,
                        "comparison must not be null when status is ALIGNED"
                ),
                new ExceptionCase(
                        "alignedWithFailureReason",
                        () -> sequenceAlignment(
                                0,
                                AlignmentStatus.ALIGNED,
                                sequenceOf("x"),
                                List.of("x"),
                                tokenComparison(List.of()),
                                "boom"
                        ),
                        IllegalArgumentException.class,
                        "failureReason must be null when status is ALIGNED"
                ),
                new ExceptionCase(
                        "alignedWithNonAlignedComparison",
                        () -> sequenceAlignment(
                                0,
                                AlignmentStatus.ALIGNED,
                                sequenceOf("x"),
                                List.of("x"),
                                tokenComparison(List.of(tokenDifference(DifferenceKind.REPLACEMENT, 0, 1, 0, 2))),
                                null
                        ),
                        IllegalArgumentException.class,
                        "comparison.aligned() must be true when status is ALIGNED"
                ),
                new ExceptionCase(
                        "misalignedWithAlignedComparison",
                        () -> sequenceAlignment(
                                0,
                                AlignmentStatus.MISALIGNED,
                                sequenceOf("x"),
                                List.of("x"),
                                tokenComparison(List.of()),
                                null
                        ),
                        IllegalArgumentException.class,
                        "comparison.aligned() must be false when status is MISALIGNED"
                ),
                new ExceptionCase(
                        "untokenizableWithComparison",
                        () -> sequenceAlignment(
                                0,
                                AlignmentStatus.UNTOKENIZABLE,
                                sequenceOf("x"),
                                List.of(),
                                tokenComparison(List.of()),
                                "blank"
                        ),
                        IllegalArgumentException.class,
                        "comparison must be null when status is UNTOKENIZABLE"
                ),
                new ExceptionCase(
                        "untokenizableWithNullFailureReason",
                        () -> sequenceAlignment(
                                0,
                                AlignmentStatus.UNTOKENIZABLE,
                                sequenceOf("x"),
                                List.of(),
                                null,
                                null
                        ),
                        IllegalArgumentException.class,
                        "failureReason must not be null when status is UNTOKENIZABLE"
                )
        );
    }

    static Stream<ExceptionCase> tokenComparison__exception() {
        return Stream.of(
                new ExceptionCase(
                        "prefixAnchorMismatch",
                        () -> tokenComparison(List.of(tokenDifference(DifferenceKind.INSERTION, 5, 5, 0, 2))),
                        IllegalArgumentException.class,
                        "first difference must start at equal stored and re-tokenized indices, got: 5 != 0"
                ),
                new ExceptionCase(
                        "overlappingRegions",
                        () -> tokenComparison(
                                List.of(
                                        tokenDifference(DifferenceKind.REPLACEMENT, 0, 3, 0, 3),
                                        tokenDifference(DifferenceKind.REPLACEMENT, 2, 4, 2, 4)
                                )
                        ),
                        IllegalArgumentException.class,
                        "differences must not overlap, got stored gap: -1 at difference index 1"
                ),
                new ExceptionCase(
                        "unequalMatchedGap",
                        () -> tokenComparison(
                                List.of(
                                        tokenDifference(DifferenceKind.REPLACEMENT, 0, 1, 0, 1),
                                        tokenDifference(DifferenceKind.REPLACEMENT, 3, 4, 5, 6)
                                )
                        ),
                        IllegalArgumentException.class,
                        "matched gap before difference index 1 must have equal length on both sides, got: 2 != 4"
                )
        );
    }

    @SuppressWarnings("DataFlowIssue")
    static Stream<ExceptionCase> tokenDifference__exception() {
        return Stream.of(
                new ExceptionCase(
                        "nullKind",
                        () -> tokenDifference(null, 0, 1, 0, 1),
                        NullPointerException.class,
                        "kind must not be null"
                ),
                new ExceptionCase(
                        "negativeStoredStart",
                        () -> tokenDifference(DifferenceKind.REPLACEMENT, -1, 0, 0, 1),
                        IllegalArgumentException.class,
                        "storedStart must not be negative, got: -1"
                ),
                new ExceptionCase(
                        "storedEndBeforeStart",
                        () -> tokenDifference(DifferenceKind.REPLACEMENT, 2, 1, 0, 1),
                        IllegalArgumentException.class,
                        "storedEnd must not be less than storedStart, got: 1 < 2"
                ),
                new ExceptionCase(
                        "negativeRetokenizedStart",
                        () -> tokenDifference(DifferenceKind.REPLACEMENT, 0, 1, -1, 0),
                        IllegalArgumentException.class,
                        "retokenizedStart must not be negative, got: -1"
                ),
                new ExceptionCase(
                        "retokenizedEndBeforeStart",
                        () -> tokenDifference(DifferenceKind.REPLACEMENT, 0, 1, 3, 1),
                        IllegalArgumentException.class,
                        "retokenizedEnd must not be less than retokenizedStart, got: 1 < 3"
                ),
                new ExceptionCase(
                        "insertionWithNonEmptyStoredSpan",
                        () -> tokenDifference(DifferenceKind.INSERTION, 0, 1, 0, 1),
                        IllegalArgumentException.class,
                        "INSERTION requires an empty stored span and a non-empty re-tokenized span"
                ),
                new ExceptionCase(
                        "deletionWithNonEmptyRetokenizedSpan",
                        () -> tokenDifference(DifferenceKind.DELETION, 0, 1, 0, 1),
                        IllegalArgumentException.class,
                        "DELETION requires a non-empty stored span and an empty re-tokenized span"
                ),
                new ExceptionCase(
                        "replacementWithEmptyStoredSpan",
                        () -> tokenDifference(DifferenceKind.REPLACEMENT, 0, 0, 0, 1),
                        IllegalArgumentException.class,
                        "REPLACEMENT requires non-empty stored and re-tokenized spans"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void exactMatchStrategy__compare(CompareParameters parameters) {
        // ACT //
        TokenComparison comparison = exactMatchStrategy()
                .compare(parameters.storedTokens(), parameters.retokenizedTokens());

        // ASSERT //
        assertEquals(parameters.expectedDifferences(), comparison.differences());
        assertEquals(parameters.expectedDifferences().isEmpty(), comparison.aligned());
    }

    @Test
    void sequenceAlignment__defensiveCopy() {
        // ARRANGE //
        List<String> retokenizedTokens = new ArrayList<>(List.of("Brown", "Fox"));

        // ACT //
        SequenceAlignment<String> sequence = sequenceAlignment(
                0,
                AlignmentStatus.ALIGNED,
                sequenceOf("Brown", "Fox"),
                retokenizedTokens,
                tokenComparison(List.of()),
                null
        );
        retokenizedTokens.add("extra");

        // ASSERT //
        assertEquals(List.of("Brown", "Fox"), sequence.retokenizedTokens());
    }

    @MethodSource
    @ParameterizedTest
    void sequenceAlignment__exception(ExceptionCase parameters) {
        assertThrowsWithMessage(parameters);
    }

    @Test
    void tokenComparison__acceptsCoherentMultiRegion() {
        // ARRANGE //
        List<TokenDifference> differences = List.of(
                tokenDifference(DifferenceKind.INSERTION, 1, 1, 1, 3),
                tokenDifference(DifferenceKind.REPLACEMENT, 4, 6, 6, 7)
        );

        // ACT //
        TokenComparison comparison = tokenComparison(differences);

        // ASSERT //
        assertEquals(differences, comparison.differences());
        assertFalse(comparison.aligned());
    }

    @Test
    void tokenComparison__defensiveCopy() {
        // ARRANGE //
        List<TokenDifference> differences = new ArrayList<>(
                List.of(tokenDifference(DifferenceKind.REPLACEMENT, 0, 1, 0, 2))
        );

        // ACT //
        TokenComparison comparison = tokenComparison(differences);
        differences.add(tokenDifference(DifferenceKind.INSERTION, 1, 1, 2, 3));

        // ASSERT //
        assertEquals(1, comparison.differences().size());
    }

    @MethodSource
    @ParameterizedTest
    void tokenComparison__exception(ExceptionCase parameters) {
        assertThrowsWithMessage(parameters);
    }

    @MethodSource
    @ParameterizedTest
    void tokenDifference__exception(ExceptionCase parameters) {
        assertThrowsWithMessage(parameters);
    }

    @Test
    void tokenDifference__exposesSpansAndKind() {
        // ACT //
        TokenDifference difference = tokenDifference(DifferenceKind.REPLACEMENT, 1, 3, 1, 2);

        // ASSERT //
        assertEquals(DifferenceKind.REPLACEMENT, difference.kind());
        assertEquals(1, difference.storedStart());
        assertEquals(3, difference.storedEnd());
        assertEquals(1, difference.retokenizedStart());
        assertEquals(2, difference.retokenizedEnd());
    }
}
