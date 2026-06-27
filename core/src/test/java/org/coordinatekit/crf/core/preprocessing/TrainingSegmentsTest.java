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

import static org.coordinatekit.crf.core.preprocessing.TrainingSegments.excluded;
import static org.coordinatekit.crf.core.preprocessing.TrainingSegments.token;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrainingSegmentsTest {
    record ExceptionParameters(
            String name,
            Executable action,
            Class<? extends Exception> expectedClass,
            String expectedMessage
    ) {}

    @SuppressWarnings({"DataFlowIssue", "NullAway"})
    static Stream<ExceptionParameters> factory__exception() {
        return Stream.of(
                new ExceptionParameters(
                        "excluded_rejects_null_text",
                        () -> excluded(null),
                        NullPointerException.class,
                        "text must not be null"
                ),
                new ExceptionParameters(
                        "token_rejects_null_tag",
                        () -> token(null, "Fox"),
                        NullPointerException.class,
                        "tag must not be null"
                ),
                new ExceptionParameters(
                        "token_rejects_null_text",
                        () -> token("Noun", null),
                        NullPointerException.class,
                        "text must not be null"
                )
        );
    }

    @Test
    void excluded__hasNoTag() {
        // ACT //
        TrainingSegment<String> segment = excluded("  ");

        // ASSERT //
        assertEquals(SegmentKind.EXCLUDED, segment.kind());
        assertEquals("  ", segment.text());
        assertNull(segment.tag());
    }

    @MethodSource
    @ParameterizedTest
    void factory__exception(ExceptionParameters parameters) {
        // ACT //
        Exception exception = assertThrows(parameters.expectedClass(), parameters.action());

        // ASSERT //
        assertEquals(parameters.expectedMessage(), exception.getMessage());
    }

    @Test
    void token__carriesTagAndText() {
        // ACT //
        TrainingSegment<String> segment = token("Noun", "Fox");

        // ASSERT //
        assertEquals(SegmentKind.TOKEN, segment.kind());
        assertEquals("Fox", segment.text());
        assertEquals("Noun", segment.tag());
    }
}
