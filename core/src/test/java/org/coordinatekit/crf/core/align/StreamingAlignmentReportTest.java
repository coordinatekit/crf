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

import org.coordinatekit.crf.core.align.AlignmentTestSupport.ExceptionCase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

// The streaming behavior itself — sequences(), summary(), materialize() — is exercised through the
// public entry point in AlignmentDetectorTest; this class covers constructor validation only.
class StreamingAlignmentReportTest {
    @SuppressWarnings({"DataFlowIssue", "NullAway"})
    static Stream<ExceptionCase> constructor__exception() {
        AlignmentDetector<String> detector = defaultDetector();
        return Stream.of(
                new ExceptionCase(
                        "nullDetector",
                        () -> new StreamingAlignmentReport<>((AlignmentDetector<String>) null, Path.of("training.xml")),
                        NullPointerException.class,
                        "detector must not be null"
                ),
                new ExceptionCase(
                        "nullSource",
                        () -> new StreamingAlignmentReport<>(detector, null),
                        NullPointerException.class,
                        "source must not be null"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void constructor__exception(ExceptionCase parameters) {
        assertThrowsWithMessage(parameters);
    }
}
