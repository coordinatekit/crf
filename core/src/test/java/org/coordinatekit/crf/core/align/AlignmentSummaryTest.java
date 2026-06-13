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

import static org.coordinatekit.crf.core.align.AlignmentTestSupport.assertThrowsWithMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.coordinatekit.crf.core.align.AlignmentTestSupport.ExceptionCase;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

class AlignmentSummaryTest {
    record EqualsInequalityParameters(String name, @Nullable Object different) {}

    record SummaryParameters(
            String name,
            long aligned,
            long misaligned,
            long untokenizable,
            long expectedTotal,
            boolean expectedAllAligned
    ) {}

    static Stream<ExceptionCase> constructor__exception() {
        return Stream.of(
                new ExceptionCase(
                        "negativeAligned",
                        () -> new AlignmentSummary(-1, 0, 0),
                        IllegalArgumentException.class,
                        "aligned must not be negative, got: -1"
                ),
                new ExceptionCase(
                        "negativeMisaligned",
                        () -> new AlignmentSummary(0, -1, 0),
                        IllegalArgumentException.class,
                        "misaligned must not be negative, got: -1"
                ),
                new ExceptionCase(
                        "negativeUntokenizable",
                        () -> new AlignmentSummary(0, 0, -1),
                        IllegalArgumentException.class,
                        "untokenizable must not be negative, got: -1"
                )
        );
    }

    static Stream<EqualsInequalityParameters> equals__inequality() {
        return Stream.of(
                new EqualsInequalityParameters("differsInAligned", new AlignmentSummary(9, 2, 3)),
                new EqualsInequalityParameters("differsInMisaligned", new AlignmentSummary(1, 9, 3)),
                new EqualsInequalityParameters("differsInUntokenizable", new AlignmentSummary(1, 2, 9)),
                new EqualsInequalityParameters("null", null),
                new EqualsInequalityParameters("otherType", "not a summary")
        );
    }

    static Stream<SummaryParameters> summary() {
        return Stream.of(
                new SummaryParameters("empty", 0, 0, 0, 0, true),
                new SummaryParameters("allAligned", 3, 0, 0, 3, true),
                new SummaryParameters("hasMisaligned", 2, 1, 0, 3, false),
                new SummaryParameters("hasUntokenizable", 2, 0, 1, 3, false)
        );
    }

    @MethodSource
    @ParameterizedTest
    void constructor__exception(ExceptionCase parameters) {
        assertThrowsWithMessage(parameters);
    }

    @MethodSource
    @ParameterizedTest
    void equals__inequality(EqualsInequalityParameters parameters) {
        // ARRANGE //
        AlignmentSummary summary = new AlignmentSummary(1, 2, 3);

        // ASSERT //
        assertNotEquals(summary, parameters.different());
    }

    @Test
    void equals__valueSemantics() {
        // ARRANGE //
        AlignmentSummary summary = new AlignmentSummary(1, 2, 3);
        AlignmentSummary equal = new AlignmentSummary(1, 2, 3);

        // ASSERT //
        assertEquals(summary, equal);
        assertEquals(summary.hashCode(), equal.hashCode());
    }

    @MethodSource
    @ParameterizedTest
    void summary(SummaryParameters parameters) {
        // ACT //
        AlignmentSummary summary = new AlignmentSummary(
                parameters.aligned(),
                parameters.misaligned(),
                parameters.untokenizable()
        );

        // ASSERT //
        assertEquals(parameters.aligned(), summary.aligned());
        assertEquals(parameters.misaligned(), summary.misaligned());
        assertEquals(parameters.untokenizable(), summary.untokenizable());
        assertEquals(parameters.expectedTotal(), summary.total());
        assertEquals(parameters.expectedAllAligned(), summary.allAligned());
    }
}
