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
import static org.coordinatekit.crf.core.align.AlignmentTestSupport.defaultDetector;
import static org.coordinatekit.crf.core.align.AlignmentTestSupport.sequenceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.coordinatekit.crf.core.align.AlignmentTestSupport.ExceptionCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

// The materialized behavior itself — sequences(), summary(), snapshot decoupling — is exercised
// through the public entry point in AlignmentDetectorTest; this class covers constructor validation
// and the defensive copy only.
class MaterializedAlignmentReportTest {
    private final AlignmentDetector<String> detector = defaultDetector();

    @SuppressWarnings({"DataFlowIssue", "NullAway"})
    static Stream<ExceptionCase> constructor__exception() {
        return Stream.of(
                new ExceptionCase(
                        "nullSource",
                        () -> new MaterializedAlignmentReport<>(null, List.of()),
                        NullPointerException.class,
                        "source must not be null"
                ),
                new ExceptionCase(
                        "nullSequences",
                        () -> new MaterializedAlignmentReport<>(Path.of("training.xml"), null),
                        NullPointerException.class,
                        "sequences must not be null"
                )
        );
    }

    @Test
    void constructor__defensiveCopy() {
        // ARRANGE //
        SequenceAlignment<String> alignment = detector.align(0, sequenceOf("New York"));
        List<SequenceAlignment<String>> mutable = new ArrayList<>(List.of(alignment));
        MaterializedAlignmentReport<String> report = new MaterializedAlignmentReport<>(
                Path.of("training.xml"),
                mutable
        );

        // ACT //
        mutable.clear();

        // ASSERT //
        assertEquals(1, report.sequences().count());
    }

    @MethodSource
    @ParameterizedTest
    void constructor__exception(ExceptionCase parameters) {
        assertThrowsWithMessage(parameters);
    }
}
