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
package org.coordinatekit.crf.core.feature;

import static org.coordinatekit.crf.core.feature.Feature.createFeature;
import org.coordinatekit.crf.core.InputSequence;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LengthFeatureExtractorTest {
    record ExtractAtParameters(
            String name,
            int lengthUpperLimit,
            @Nullable Function<Integer, Feature> hasLengthFeatureMapper,
            @Nullable Function<Integer, Feature> lacksLengthFeatureMapper,
            List<String> tokens,
            int position,
            Set<Feature> expectedResult
    ) {}

    static Stream<ExtractAtParameters> extractAt() {
        return Stream.of(
                new ExtractAtParameters(
                        "hasLengthOnly_sequenceSizeEqualsLimit",
                        3,
                        len -> createFeature("HAS_" + len),
                        null,
                        List.of("a", "b", "c"),
                        0,
                        Set.of(createFeature("HAS_1"), createFeature("HAS_2"), createFeature("HAS_3"))
                ),
                new ExtractAtParameters(
                        "hasLengthOnly_sequenceSizeLessThanLimit",
                        5,
                        len -> createFeature("HAS_" + len),
                        null,
                        List.of("a", "b"),
                        0,
                        Set.of(createFeature("HAS_1"), createFeature("HAS_2"))
                ),
                new ExtractAtParameters(
                        "hasLengthOnly_sequenceSizeGreaterThanLimit",
                        2,
                        len -> createFeature("HAS_" + len),
                        null,
                        List.of("a", "b", "c", "d"),
                        0,
                        Set.of(createFeature("HAS_1"), createFeature("HAS_2"))
                ),
                new ExtractAtParameters(
                        "lacksLengthOnly_sequenceSizeLessThanLimit",
                        5,
                        null,
                        len -> createFeature("LACKS_" + len),
                        List.of("a", "b"),
                        0,
                        Set.of(createFeature("LACKS_3"), createFeature("LACKS_4"), createFeature("LACKS_5"))
                ),
                new ExtractAtParameters(
                        "lacksLengthOnly_sequenceSizeEqualsLimit",
                        3,
                        null,
                        len -> createFeature("LACKS_" + len),
                        List.of("a", "b", "c"),
                        0,
                        Set.of()
                ),
                new ExtractAtParameters(
                        "bothMappers_partitionsCorrectly",
                        5,
                        len -> createFeature("HAS_" + len),
                        len -> createFeature("LACKS_" + len),
                        List.of("a", "b", "c"),
                        0,
                        Set.of(
                                createFeature("HAS_1"),
                                createFeature("HAS_2"),
                                createFeature("HAS_3"),
                                createFeature("LACKS_4"),
                                createFeature("LACKS_5")
                        )
                ),
                new ExtractAtParameters(
                        "noMappers_returnsEmptySet",
                        3,
                        null,
                        null,
                        List.of("a", "b", "c"),
                        0,
                        Set.of()
                ),
                new ExtractAtParameters(
                        "singleTokenSequence",
                        3,
                        len -> createFeature("HAS_" + len),
                        len -> createFeature("LACKS_" + len),
                        List.of("only"),
                        0,
                        Set.of(createFeature("HAS_1"), createFeature("LACKS_2"), createFeature("LACKS_3"))
                ),
                new ExtractAtParameters(
                        "positionDoesNotAffectResult",
                        3,
                        len -> createFeature("HAS_" + len),
                        len -> createFeature("LACKS_" + len),
                        List.of("a", "b"),
                        1,
                        Set.of(createFeature("HAS_1"), createFeature("HAS_2"), createFeature("LACKS_3"))
                ),
                new ExtractAtParameters(
                        "limitOfOne_singleHasFeature",
                        1,
                        len -> createFeature("HAS_" + len),
                        len -> createFeature("LACKS_" + len),
                        List.of("a", "b", "c"),
                        0,
                        Set.of(createFeature("HAS_1"))
                ),
                new ExtractAtParameters(
                        "limitOfOne_singleLacksFeatureForEmptySequenceNotPossible",
                        1,
                        len -> createFeature("HAS_" + len),
                        len -> createFeature("LACKS_" + len),
                        List.of("a"),
                        0,
                        Set.of(createFeature("HAS_1"))
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void extractAt(ExtractAtParameters parameters) {
        // ARRANGE //
        LengthFeatureExtractor extractor = LengthFeatureExtractor.builder(parameters.lengthUpperLimit())
                .hasLengthFeatureMapper(parameters.hasLengthFeatureMapper())
                .lacksLengthFeatureMapper(parameters.lacksLengthFeatureMapper()).build();
        InputSequence sequence = new InputSequence(parameters.tokens());

        // ACT //
        Set<Feature> actual = extractor.extractAt(sequence, parameters.position());

        // ASSERT //
        assertEquals(parameters.expectedResult(), actual);
    }
}
