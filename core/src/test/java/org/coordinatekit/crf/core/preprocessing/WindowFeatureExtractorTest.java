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
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WindowFeatureExtractorTest {
    private static final FeatureExtractor TOKEN_EXTRACTOR = (seq, pos) -> Set
            .of(Features.of("TOKEN", seq.get(pos).token()));

    private static WindowFeatureExtractor extractor(int before, int after, boolean includeCurrent) {
        return WindowFeatureExtractor.builder(TOKEN_EXTRACTOR).windowBefore(before).windowAfter(after)
                .includeCurrentToken(includeCurrent).build();
    }

    private static WindowFeatureExtractor defaultExtractor() {
        return WindowFeatureExtractor.builder(TOKEN_EXTRACTOR).build();
    }

    private static WindowFeatureExtractor multipleFeaturesExtractor() {
        FeatureExtractor multiFeatureExtractor = (seq, pos) -> {
            String token = seq.get(pos).token();
            return Set.of(Features.of("TOKEN", token), Features.of("LENGTH", String.valueOf(token.length())));
        };

        return WindowFeatureExtractor.builder(multiFeatureExtractor).build();
    }

    record BuilderExceptionParameters(
            String name,
            Executable action,
            Class<? extends Exception> expectedClass,
            String expectedMessage
    ) {}

    static Stream<BuilderExceptionParameters> builder__exception() {
        return Stream.of(
                new BuilderExceptionParameters(
                        "negativeWindowBefore",
                        () -> WindowFeatureExtractor.builder(TOKEN_EXTRACTOR).windowBefore(-1),
                        IllegalArgumentException.class,
                        "windowBefore must be non-negative"
                ),
                new BuilderExceptionParameters(
                        "negativeWindowAfter",
                        () -> WindowFeatureExtractor.builder(TOKEN_EXTRACTOR).windowAfter(-1),
                        IllegalArgumentException.class,
                        "windowAfter must be non-negative"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void builder__exception(BuilderExceptionParameters parameters) {
        // ACT //
        Exception exception = assertThrows(parameters.expectedClass(), parameters.action());

        // ASSERT //
        assertEquals(parameters.expectedMessage(), exception.getMessage());
    }

    record ExtractAtParameters(
            String name,
            WindowFeatureExtractor extractor,
            List<String> tokens,
            int position,
            Set<Feature> expectedResult
    ) {}

    static Stream<ExtractAtParameters> extractAt() {
        return Stream.of(
                new ExtractAtParameters(
                        "currentPreviousAndNext_defaultWindow",
                        defaultExtractor(),
                        List.of("a", "b", "c"),
                        1,
                        Set.of(
                                Features.of("TOKEN", "b"),
                                Features.of("TOKEN", "a").withOffset(-1),
                                Features.of("TOKEN", "c").withOffset(1)
                        )
                ),
                new ExtractAtParameters(
                        "excludeCurrentToken_neighborsOnly",
                        extractor(1, 1, false),
                        List.of("a", "b", "c"),
                        1,
                        Set.of(Features.of("TOKEN", "a").withOffset(-1), Features.of("TOKEN", "c").withOffset(1))
                ),
                new ExtractAtParameters(
                        "startOfSequence_currentAndNext",
                        defaultExtractor(),
                        List.of("a", "b", "c"),
                        0,
                        Set.of(Features.of("TOKEN", "a"), Features.of("TOKEN", "b").withOffset(1))
                ),
                new ExtractAtParameters(
                        "endOfSequence_currentAndPrevious",
                        defaultExtractor(),
                        List.of("a", "b", "c"),
                        2,
                        Set.of(Features.of("TOKEN", "c"), Features.of("TOKEN", "b").withOffset(-1))
                ),
                new ExtractAtParameters(
                        "largerWindow_multipleNeighbors",
                        extractor(2, 2, true),
                        List.of("a", "b", "c", "d", "e"),
                        2,
                        Set.of(
                                Features.of("TOKEN", "c"),
                                Features.of("TOKEN", "b").withOffset(-1),
                                Features.of("TOKEN", "a").withOffset(-2),
                                Features.of("TOKEN", "d").withOffset(1),
                                Features.of("TOKEN", "e").withOffset(2)
                        )
                ),
                new ExtractAtParameters(
                        "partialWindowAtBoundaries",
                        extractor(3, 3, true),
                        List.of("a", "b", "c"),
                        1,
                        Set.of(
                                Features.of("TOKEN", "b"),
                                Features.of("TOKEN", "a").withOffset(-1),
                                Features.of("TOKEN", "c").withOffset(1)
                        )
                ),
                new ExtractAtParameters(
                        "zeroWindowBefore_currentAndAfter",
                        extractor(0, 1, true),
                        List.of("a", "b", "c"),
                        1,
                        Set.of(Features.of("TOKEN", "b"), Features.of("TOKEN", "c").withOffset(1))
                ),
                new ExtractAtParameters(
                        "zeroWindowAfter_currentAndBefore",
                        extractor(1, 0, true),
                        List.of("a", "b", "c"),
                        1,
                        Set.of(Features.of("TOKEN", "b"), Features.of("TOKEN", "a").withOffset(-1))
                ),
                new ExtractAtParameters(
                        "zeroWindowBoth_currentOnly",
                        extractor(0, 0, true),
                        List.of("a", "b", "c"),
                        1,
                        Set.of(Features.of("TOKEN", "b"))
                ),
                new ExtractAtParameters(
                        "zeroWindowBoth_excludeCurrent_empty",
                        extractor(0, 0, false),
                        List.of("a", "b", "c"),
                        1,
                        Set.of()
                ),
                new ExtractAtParameters(
                        "singleToken_currentOnly",
                        defaultExtractor(),
                        List.of("alone"),
                        0,
                        Set.of(Features.of("TOKEN", "alone"))
                ),
                new ExtractAtParameters(
                        "singleToken_excludeCurrent_empty",
                        extractor(1, 1, false),
                        List.of("alone"),
                        0,
                        Set.of()
                ),
                new ExtractAtParameters(
                        "asymmetricWindow_moreBefore",
                        extractor(2, 1, true),
                        List.of("a", "b", "c", "d", "e"),
                        2,
                        Set.of(
                                Features.of("TOKEN", "c"),
                                Features.of("TOKEN", "b").withOffset(-1),
                                Features.of("TOKEN", "a").withOffset(-2),
                                Features.of("TOKEN", "d").withOffset(1)
                        )
                ),
                new ExtractAtParameters(
                        "defaultWindowSize_middlePosition",
                        defaultExtractor(),
                        List.of("a", "b", "c", "d", "e"),
                        2,
                        Set.of(
                                Features.of("TOKEN", "c"),
                                Features.of("TOKEN", "b").withOffset(-1),
                                Features.of("TOKEN", "d").withOffset(1)
                        )
                ),
                new ExtractAtParameters(
                        "delegateReturnsMultipleFeatures",
                        multipleFeaturesExtractor(),
                        List.of("hi", "there"),
                        0,
                        Set.of(
                                Features.of("TOKEN", "hi"),
                                Features.of("LENGTH", "2"),
                                Features.of("TOKEN", "there").withOffset(1),
                                Features.of("LENGTH", "5").withOffset(1)
                        )
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void extractAt(ExtractAtParameters parameters) {
        // ARRANGE //
        InputSequence sequence = new InputSequence(parameters.tokens());

        // ACT //
        Set<Feature> actual = parameters.extractor().extractAt(sequence, parameters.position());

        // ASSERT //
        assertEquals(parameters.expectedResult(), actual);
    }
}
