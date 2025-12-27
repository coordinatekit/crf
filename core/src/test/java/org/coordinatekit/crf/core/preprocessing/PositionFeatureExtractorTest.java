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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NullMarked
class PositionFeatureExtractorTest {
    record ExtractAtParameters(
            String name,
            @Nullable String firstFeature,
            @Nullable String lastFeature,
            @Nullable Function<Integer, String> positionFromStartFeatureMapper,
            @Nullable Function<Integer, String> positionFromEndFeatureMapper,
            List<String> tokens,
            int position,
            Set<String> expectedResult
    ) {}

    static Stream<ExtractAtParameters> extractAt() {
        return Stream.of(
                new ExtractAtParameters(
                        "firstFeature_atPositionZero",
                        "FIRST",
                        null,
                        null,
                        null,
                        List.of("a", "b", "c"),
                        0,
                        Set.of("FIRST")
                ),
                new ExtractAtParameters(
                        "firstFeature_notAtMiddlePosition",
                        "FIRST",
                        null,
                        null,
                        null,
                        List.of("a", "b", "c"),
                        1,
                        Set.of()
                ),
                new ExtractAtParameters(
                        "firstFeature_notAtLastPosition",
                        "FIRST",
                        null,
                        null,
                        null,
                        List.of("a", "b", "c"),
                        2,
                        Set.of()
                ),
                new ExtractAtParameters(
                        "lastFeature_atLastPosition",
                        null,
                        "LAST",
                        null,
                        null,
                        List.of("a", "b", "c"),
                        2,
                        Set.of("LAST")
                ),
                new ExtractAtParameters(
                        "lastFeature_notAtFirstPosition",
                        null,
                        "LAST",
                        null,
                        null,
                        List.of("a", "b", "c"),
                        0,
                        Set.of()
                ),
                new ExtractAtParameters(
                        "lastFeature_notAtMiddlePosition",
                        null,
                        "LAST",
                        null,
                        null,
                        List.of("a", "b", "c"),
                        1,
                        Set.of()
                ),
                new ExtractAtParameters(
                        "singleToken_bothFirstAndLast",
                        "FIRST",
                        "LAST",
                        null,
                        null,
                        List.of("only"),
                        0,
                        Set.of("FIRST", "LAST")
                ),
                new ExtractAtParameters(
                        "positionFromStart_atZero",
                        null,
                        null,
                        pos -> "START_" + pos,
                        null,
                        List.of("a", "b", "c"),
                        0,
                        Set.of("START_0")
                ),
                new ExtractAtParameters(
                        "positionFromStart_atMiddle",
                        null,
                        null,
                        pos -> "START_" + pos,
                        null,
                        List.of("a", "b", "c"),
                        1,
                        Set.of("START_1")
                ),
                new ExtractAtParameters(
                        "positionFromStart_atEnd",
                        null,
                        null,
                        pos -> "START_" + pos,
                        null,
                        List.of("a", "b", "c"),
                        2,
                        Set.of("START_2")
                ),
                new ExtractAtParameters(
                        "positionFromEnd_atLastPosition",
                        null,
                        null,
                        null,
                        pos -> "END_" + pos,
                        List.of("a", "b", "c"),
                        2,
                        Set.of("END_0")
                ),
                new ExtractAtParameters(
                        "positionFromEnd_atMiddlePosition",
                        null,
                        null,
                        null,
                        pos -> "END_" + pos,
                        List.of("a", "b", "c"),
                        1,
                        Set.of("END_1")
                ),
                new ExtractAtParameters(
                        "positionFromEnd_atFirstPosition",
                        null,
                        null,
                        null,
                        pos -> "END_" + pos,
                        List.of("a", "b", "c"),
                        0,
                        Set.of("END_2")
                ),
                new ExtractAtParameters(
                        "allFeatures_atFirstPosition",
                        "FIRST",
                        "LAST",
                        pos -> "START_" + pos,
                        pos -> "END_" + pos,
                        List.of("a", "b", "c"),
                        0,
                        Set.of("FIRST", "START_0", "END_2")
                ),
                new ExtractAtParameters(
                        "allFeatures_atMiddlePosition",
                        "FIRST",
                        "LAST",
                        pos -> "START_" + pos,
                        pos -> "END_" + pos,
                        List.of("a", "b", "c"),
                        1,
                        Set.of("START_1", "END_1")
                ),
                new ExtractAtParameters(
                        "allFeatures_atLastPosition",
                        "FIRST",
                        "LAST",
                        pos -> "START_" + pos,
                        pos -> "END_" + pos,
                        List.of("a", "b", "c"),
                        2,
                        Set.of("LAST", "START_2", "END_0")
                ),
                new ExtractAtParameters(
                        "noFeatures_returnsEmptySet",
                        null,
                        null,
                        null,
                        null,
                        List.of("a", "b", "c"),
                        1,
                        Set.of()
                ),
                new ExtractAtParameters(
                        "singleToken_allFeatures",
                        "FIRST",
                        "LAST",
                        pos -> "START_" + pos,
                        pos -> "END_" + pos,
                        List.of("only"),
                        0,
                        Set.of("FIRST", "LAST", "START_0", "END_0")
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void extractAt(ExtractAtParameters parameters) {
        // ARRANGE //
        PositionFeatureExtractor<String> extractor = PositionFeatureExtractor.<String>builder()
                .firstFeature(parameters.firstFeature()).lastFeature(parameters.lastFeature())
                .positionFromStartFeatureMapper(parameters.positionFromStartFeatureMapper())
                .positionFromEndFeatureMapper(parameters.positionFromEndFeatureMapper()).build();
        InputSequence sequence = new InputSequence(parameters.tokens());

        // ACT //
        Set<String> actual = extractor.extractAt(sequence, parameters.position());

        // ASSERT //
        assertEquals(parameters.expectedResult(), actual);
    }
}
