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

import static org.coordinatekit.crf.core.preprocessing.Feature.createFeature;
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
    private static final Function<Integer, Feature> END_MAPPER = position -> createFeature("END_" + position);
    private static final Function<Integer, Feature> START_MAPPER = position -> createFeature("START_" + position);

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
                        createFeature("FIRST"),
                        null,
                        null,
                        null,
                        List.of("a", "b", "c"),
                        0,
                        Set.of(createFeature("FIRST"))
                ),
                new ExtractAtParameters(
                        "firstFeature_notAtMiddlePosition",
                        createFeature("FIRST"),
                        null,
                        null,
                        null,
                        List.of("a", "b", "c"),
                        1,
                        Set.of()
                ),
                new ExtractAtParameters(
                        "firstFeature_notAtLastPosition",
                        createFeature("FIRST"),
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
                        createFeature("LAST"),
                        null,
                        null,
                        List.of("a", "b", "c"),
                        2,
                        Set.of(createFeature("LAST"))
                ),
                new ExtractAtParameters(
                        "lastFeature_notAtFirstPosition",
                        null,
                        createFeature("LAST"),
                        null,
                        null,
                        List.of("a", "b", "c"),
                        0,
                        Set.of()
                ),
                new ExtractAtParameters(
                        "lastFeature_notAtMiddlePosition",
                        null,
                        createFeature("LAST"),
                        null,
                        null,
                        List.of("a", "b", "c"),
                        1,
                        Set.of()
                ),
                new ExtractAtParameters(
                        "singleToken_bothFirstAndLast",
                        createFeature("FIRST"),
                        createFeature("LAST"),
                        null,
                        null,
                        List.of("only"),
                        0,
                        Set.of(createFeature("FIRST"), createFeature("LAST"))
                ),
                new ExtractAtParameters(
                        "positionFromStart_atZero",
                        null,
                        null,
                        START_MAPPER,
                        null,
                        List.of("a", "b", "c"),
                        0,
                        Set.of(createFeature("START_0"))
                ),
                new ExtractAtParameters(
                        "positionFromStart_atMiddle",
                        null,
                        null,
                        START_MAPPER,
                        null,
                        List.of("a", "b", "c"),
                        1,
                        Set.of(createFeature("START_1"))
                ),
                new ExtractAtParameters(
                        "positionFromStart_atEnd",
                        null,
                        null,
                        START_MAPPER,
                        null,
                        List.of("a", "b", "c"),
                        2,
                        Set.of(createFeature("START_2"))
                ),
                new ExtractAtParameters(
                        "positionFromEnd_atLastPosition",
                        null,
                        null,
                        null,
                        END_MAPPER,
                        List.of("a", "b", "c"),
                        2,
                        Set.of(createFeature("END_0"))
                ),
                new ExtractAtParameters(
                        "positionFromEnd_atMiddlePosition",
                        null,
                        null,
                        null,
                        END_MAPPER,
                        List.of("a", "b", "c"),
                        1,
                        Set.of(createFeature("END_1"))
                ),
                new ExtractAtParameters(
                        "positionFromEnd_atFirstPosition",
                        null,
                        null,
                        null,
                        END_MAPPER,
                        List.of("a", "b", "c"),
                        0,
                        Set.of(createFeature("END_2"))
                ),
                new ExtractAtParameters(
                        "allFeatures_atFirstPosition",
                        createFeature("FIRST"),
                        createFeature("LAST"),
                        START_MAPPER,
                        END_MAPPER,
                        List.of("a", "b", "c"),
                        0,
                        Set.of(createFeature("FIRST"), createFeature("START_0"), createFeature("END_2"))
                ),
                new ExtractAtParameters(
                        "allFeatures_atMiddlePosition",
                        createFeature("FIRST"),
                        createFeature("LAST"),
                        START_MAPPER,
                        END_MAPPER,
                        List.of("a", "b", "c"),
                        1,
                        Set.of(createFeature("START_1"), createFeature("END_1"))
                ),
                new ExtractAtParameters(
                        "allFeatures_atLastPosition",
                        createFeature("FIRST"),
                        createFeature("LAST"),
                        START_MAPPER,
                        END_MAPPER,
                        List.of("a", "b", "c"),
                        2,
                        Set.of(createFeature("LAST"), createFeature("START_2"), createFeature("END_0"))
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
                        createFeature("FIRST"),
                        createFeature("LAST"),
                        START_MAPPER,
                        END_MAPPER,
                        List.of("only"),
                        0,
                        Set.of(
                                createFeature("FIRST"),
                                createFeature("LAST"),
                                createFeature("START_0"),
                                createFeature("END_0")
                        )
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
