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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PositionFeatureExtractorTest {
    private static final Function<Integer, Feature> END_MAPPER = position -> Features.of("END_" + position);
    private static final Function<Integer, Feature> START_MAPPER = position -> Features.of("START_" + position);

    record ExtractAtParameters(
            String name,
            @Nullable Feature firstFeature,
            @Nullable Feature lastFeature,
            @Nullable Function<Integer, Feature> positionFromStartFeatureMapper,
            @Nullable Function<Integer, Feature> positionFromEndFeatureMapper,
            List<String> tokens,
            int position,
            Set<Feature> expectedResult
    ) {}

    static Stream<ExtractAtParameters> extractAt() {
        return Stream.of(
                new ExtractAtParameters(
                        "firstFeature_atPositionZero",
                        Features.of("FIRST"),
                        null,
                        null,
                        null,
                        List.of("a", "b", "c"),
                        0,
                        Set.of(Features.of("FIRST"))
                ),
                new ExtractAtParameters(
                        "firstFeature_notAtMiddlePosition",
                        Features.of("FIRST"),
                        null,
                        null,
                        null,
                        List.of("a", "b", "c"),
                        1,
                        Set.of()
                ),
                new ExtractAtParameters(
                        "firstFeature_notAtLastPosition",
                        Features.of("FIRST"),
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
                        Features.of("LAST"),
                        null,
                        null,
                        List.of("a", "b", "c"),
                        2,
                        Set.of(Features.of("LAST"))
                ),
                new ExtractAtParameters(
                        "lastFeature_notAtFirstPosition",
                        null,
                        Features.of("LAST"),
                        null,
                        null,
                        List.of("a", "b", "c"),
                        0,
                        Set.of()
                ),
                new ExtractAtParameters(
                        "lastFeature_notAtMiddlePosition",
                        null,
                        Features.of("LAST"),
                        null,
                        null,
                        List.of("a", "b", "c"),
                        1,
                        Set.of()
                ),
                new ExtractAtParameters(
                        "singleToken_bothFirstAndLast",
                        Features.of("FIRST"),
                        Features.of("LAST"),
                        null,
                        null,
                        List.of("only"),
                        0,
                        Set.of(Features.of("FIRST"), Features.of("LAST"))
                ),
                new ExtractAtParameters(
                        "positionFromStart_atZero",
                        null,
                        null,
                        START_MAPPER,
                        null,
                        List.of("a", "b", "c"),
                        0,
                        Set.of(Features.of("START_0"))
                ),
                new ExtractAtParameters(
                        "positionFromStart_atMiddle",
                        null,
                        null,
                        START_MAPPER,
                        null,
                        List.of("a", "b", "c"),
                        1,
                        Set.of(Features.of("START_1"))
                ),
                new ExtractAtParameters(
                        "positionFromStart_atEnd",
                        null,
                        null,
                        START_MAPPER,
                        null,
                        List.of("a", "b", "c"),
                        2,
                        Set.of(Features.of("START_2"))
                ),
                new ExtractAtParameters(
                        "positionFromEnd_atLastPosition",
                        null,
                        null,
                        null,
                        END_MAPPER,
                        List.of("a", "b", "c"),
                        2,
                        Set.of(Features.of("END_0"))
                ),
                new ExtractAtParameters(
                        "positionFromEnd_atMiddlePosition",
                        null,
                        null,
                        null,
                        END_MAPPER,
                        List.of("a", "b", "c"),
                        1,
                        Set.of(Features.of("END_1"))
                ),
                new ExtractAtParameters(
                        "positionFromEnd_atFirstPosition",
                        null,
                        null,
                        null,
                        END_MAPPER,
                        List.of("a", "b", "c"),
                        0,
                        Set.of(Features.of("END_2"))
                ),
                new ExtractAtParameters(
                        "allFeatures_atFirstPosition",
                        Features.of("FIRST"),
                        Features.of("LAST"),
                        START_MAPPER,
                        END_MAPPER,
                        List.of("a", "b", "c"),
                        0,
                        Set.of(Features.of("FIRST"), Features.of("START_0"), Features.of("END_2"))
                ),
                new ExtractAtParameters(
                        "allFeatures_atMiddlePosition",
                        Features.of("FIRST"),
                        Features.of("LAST"),
                        START_MAPPER,
                        END_MAPPER,
                        List.of("a", "b", "c"),
                        1,
                        Set.of(Features.of("START_1"), Features.of("END_1"))
                ),
                new ExtractAtParameters(
                        "allFeatures_atLastPosition",
                        Features.of("FIRST"),
                        Features.of("LAST"),
                        START_MAPPER,
                        END_MAPPER,
                        List.of("a", "b", "c"),
                        2,
                        Set.of(Features.of("LAST"), Features.of("START_2"), Features.of("END_0"))
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
                        Features.of("FIRST"),
                        Features.of("LAST"),
                        START_MAPPER,
                        END_MAPPER,
                        List.of("only"),
                        0,
                        Set.of(Features.of("FIRST"), Features.of("LAST"), Features.of("START_0"), Features.of("END_0"))
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void extractAt(ExtractAtParameters parameters) {
        // ARRANGE //
        PositionFeatureExtractor extractor = PositionFeatureExtractor.builder().firstFeature(parameters.firstFeature())
                .lastFeature(parameters.lastFeature())
                .positionFromStartFeatureMapper(parameters.positionFromStartFeatureMapper())
                .positionFromEndFeatureMapper(parameters.positionFromEndFeatureMapper()).build();
        InputSequence sequence = new InputSequence(parameters.tokens());

        // ACT //
        Set<Feature> actual = extractor.extractAt(sequence, parameters.position());

        // ASSERT //
        assertEquals(parameters.expectedResult(), actual);
    }
}
