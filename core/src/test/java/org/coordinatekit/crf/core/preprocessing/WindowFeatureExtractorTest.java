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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@NullMarked
class WindowFeatureExtractorTest {
    private static final FeatureExtractor<String> TOKEN_EXTRACTOR = (seq, pos) -> Set
            .of("TOKEN=" + seq.get(pos).token());

    private static final WindowFeatureMapper<String> POSITION_MAPPER = (feature, pos) -> (pos < 0 ? "PREV_" : "NEXT_")
            + Math.abs(pos) + "__" + feature;

    private static WindowFeatureExtractor<String> extractor(int before, int after, boolean includeCurrent) {
        return WindowFeatureExtractor.builder(TOKEN_EXTRACTOR, POSITION_MAPPER).windowBefore(before).windowAfter(after)
                .includeCurrentToken(includeCurrent).build();
    }

    private static WindowFeatureExtractor<String> defaultExtractor() {
        return WindowFeatureExtractor.builder(TOKEN_EXTRACTOR, POSITION_MAPPER).build();
    }

    private static WindowFeatureExtractor<String> multipleFeaturesExtractor() {
        FeatureExtractor<String> multiFeatureExtractor = (seq, pos) -> {
            String token = seq.get(pos).token();
            return Set.of("TOKEN=" + token, "LENGTH=" + token.length());
        };

        return WindowFeatureExtractor.builder(multiFeatureExtractor, POSITION_MAPPER).build();
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
                        () -> WindowFeatureExtractor.builder(TOKEN_EXTRACTOR, POSITION_MAPPER).windowBefore(-1),
                        IllegalArgumentException.class,
                        "windowBefore must be non-negative"
                ),
                new BuilderExceptionParameters(
                        "negativeWindowAfter",
                        () -> WindowFeatureExtractor.builder(TOKEN_EXTRACTOR, POSITION_MAPPER).windowAfter(-1),
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
            WindowFeatureExtractor<String> extractor,
            List<String> tokens,
            int position,
            Set<String> expectedResult
    ) {}

    static Stream<ExtractAtParameters> extractAt() {
        return Stream.of(
                new ExtractAtParameters(
                        "currentPreviousAndNext_defaultWindow",
                        defaultExtractor(),
                        List.of("a", "b", "c"),
                        1,
                        Set.of("TOKEN=b", "PREV_1__TOKEN=a", "NEXT_1__TOKEN=c")
                ),
                new ExtractAtParameters(
                        "excludeCurrentToken_neighborsOnly",
                        extractor(1, 1, false),
                        List.of("a", "b", "c"),
                        1,
                        Set.of("PREV_1__TOKEN=a", "NEXT_1__TOKEN=c")
                ),
                new ExtractAtParameters(
                        "startOfSequence_currentAndNext",
                        defaultExtractor(),
                        List.of("a", "b", "c"),
                        0,
                        Set.of("TOKEN=a", "NEXT_1__TOKEN=b")
                ),
                new ExtractAtParameters(
                        "endOfSequence_currentAndPrevious",
                        defaultExtractor(),
                        List.of("a", "b", "c"),
                        2,
                        Set.of("TOKEN=c", "PREV_1__TOKEN=b")
                ),
                new ExtractAtParameters(
                        "largerWindow_multipleNeighbors",
                        extractor(2, 2, true),
                        List.of("a", "b", "c", "d", "e"),
                        2,
                        Set.of("TOKEN=c", "PREV_1__TOKEN=b", "PREV_2__TOKEN=a", "NEXT_1__TOKEN=d", "NEXT_2__TOKEN=e")
                ),
                new ExtractAtParameters(
                        "partialWindowAtBoundaries",
                        extractor(3, 3, true),
                        List.of("a", "b", "c"),
                        1,
                        Set.of("TOKEN=b", "PREV_1__TOKEN=a", "NEXT_1__TOKEN=c")
                ),
                new ExtractAtParameters(
                        "zeroWindowBefore_currentAndAfter",
                        extractor(0, 1, true),
                        List.of("a", "b", "c"),
                        1,
                        Set.of("TOKEN=b", "NEXT_1__TOKEN=c")
                ),
                new ExtractAtParameters(
                        "zeroWindowAfter_currentAndBefore",
                        extractor(1, 0, true),
                        List.of("a", "b", "c"),
                        1,
                        Set.of("TOKEN=b", "PREV_1__TOKEN=a")
                ),
                new ExtractAtParameters(
                        "zeroWindowBoth_currentOnly",
                        extractor(0, 0, true),
                        List.of("a", "b", "c"),
                        1,
                        Set.of("TOKEN=b")
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
                        Set.of("TOKEN=alone")
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
                        Set.of("TOKEN=c", "PREV_1__TOKEN=b", "PREV_2__TOKEN=a", "NEXT_1__TOKEN=d")
                ),
                new ExtractAtParameters(
                        "defaultWindowSize_middlePosition",
                        defaultExtractor(),
                        List.of("a", "b", "c", "d", "e"),
                        2,
                        Set.of("TOKEN=c", "PREV_1__TOKEN=b", "NEXT_1__TOKEN=d")
                ),
                new ExtractAtParameters(
                        "delegateReturnsMultipleFeatures",
                        multipleFeaturesExtractor(),
                        List.of("hi", "there"),
                        0,
                        Set.of("TOKEN=hi", "LENGTH=2", "NEXT_1__TOKEN=there", "NEXT_1__LENGTH=5")
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void extractAt(ExtractAtParameters parameters) {
        // ARRANGE //
        InputSequence sequence = new InputSequence(parameters.tokens());

        // ACT //
        Set<String> actual = parameters.extractor().extractAt(sequence, parameters.position());

        // ASSERT //
        assertEquals(parameters.expectedResult(), actual);
    }

    @Test
    void extractAt__worksWithIntegerFeatures() {
        FeatureExtractor<Integer> lengthExtractor = (seq, pos) -> Set.of(seq.get(pos).token().length());
        WindowFeatureMapper<Integer> mapper = (feature, pos) -> feature * 10 + Math.abs(pos);

        WindowFeatureExtractor<Integer> extractor = WindowFeatureExtractor.builder(lengthExtractor, mapper)
                .includeCurrentToken(false).build();

        InputSequence sequence = new InputSequence(List.of("hi", "there", "world"));
        Set<Integer> features = extractor.extractAt(sequence, 1);

        // "hi" has length 2, position -1, result = 2*10 + 1 = 21
        // "world" has length 5, position +1, result = 5*10 + 1 = 51
        assertEquals(Set.of(21, 51), features);
    }

    @Test
    void extractAt__worksWithIntegerFeaturesIncludingCurrent() {
        FeatureExtractor<Integer> lengthExtractor = (seq, pos) -> Set.of(seq.get(pos).token().length());
        WindowFeatureMapper<Integer> mapper = (feature, pos) -> feature * 10 + Math.abs(pos);

        WindowFeatureExtractor<Integer> extractor = WindowFeatureExtractor.builder(lengthExtractor, mapper).build();

        InputSequence sequence = new InputSequence(List.of("hi", "there", "world"));
        Set<Integer> features = extractor.extractAt(sequence, 1);

        // "there" has length 5 (current token, untransformed)
        // "hi" has length 2, position -1, result = 2*10 + 1 = 21
        // "world" has length 5, position +1, result = 5*10 + 1 = 51
        assertEquals(Set.of(5, 21, 51), features);
    }
}
