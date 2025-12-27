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
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@NullMarked
class SubstringFeatureExtractorTest {
    record BuilderExceptionParameters(
            String name,
            Executable action,
            Class<? extends Exception> expectedClass,
            String expectedMessage
    ) {}

    @SuppressWarnings("WriteOnlyObject")
    static Stream<BuilderExceptionParameters> builder__exception() {
        return Stream.of(
                new BuilderExceptionParameters(
                        "lengthZero",
                        () -> SubstringFeatureExtractor.builder(Function.identity()).length(0),
                        IllegalArgumentException.class,
                        "length must be at least 1"
                ),
                new BuilderExceptionParameters(
                        "lengthNegative",
                        () -> SubstringFeatureExtractor.builder(Function.identity()).length(-1),
                        IllegalArgumentException.class,
                        "length must be at least 1"
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
            Function<String, String> featureMapper,
            boolean ending,
            boolean includeIfLessThanLength,
            int length,
            List<String> tokens,
            int position,
            Set<String> expectedResult
    ) {}

    static Stream<ExtractAtParameters> extractAt() {
        return Stream.of(
                new ExtractAtParameters(
                        "prefix_tokenLongerThanLength",
                        s -> "PREFIX_" + s,
                        false,
                        true,
                        3,
                        List.of("hello"),
                        0,
                        Set.of("PREFIX_hel")
                ),
                new ExtractAtParameters(
                        "prefix_tokenEqualsLength",
                        s -> "PREFIX_" + s,
                        false,
                        true,
                        5,
                        List.of("hello"),
                        0,
                        Set.of("PREFIX_hello")
                ),
                new ExtractAtParameters(
                        "prefix_tokenShorterThanLength_includeTrue",
                        s -> "PREFIX_" + s,
                        false,
                        true,
                        10,
                        List.of("hi"),
                        0,
                        Set.of("PREFIX_hi")
                ),
                new ExtractAtParameters(
                        "prefix_tokenShorterThanLength_includeFalse",
                        s -> "PREFIX_" + s,
                        false,
                        false,
                        10,
                        List.of("hi"),
                        0,
                        Set.of()
                ),
                new ExtractAtParameters(
                        "suffix_tokenLongerThanLength",
                        s -> "SUFFIX_" + s,
                        true,
                        true,
                        3,
                        List.of("hello"),
                        0,
                        Set.of("SUFFIX_llo")
                ),
                new ExtractAtParameters(
                        "suffix_tokenEqualsLength",
                        s -> "SUFFIX_" + s,
                        true,
                        true,
                        5,
                        List.of("hello"),
                        0,
                        Set.of("SUFFIX_hello")
                ),
                new ExtractAtParameters(
                        "suffix_tokenShorterThanLength_includeTrue",
                        s -> "SUFFIX_" + s,
                        true,
                        true,
                        10,
                        List.of("hi"),
                        0,
                        Set.of("SUFFIX_hi")
                ),
                new ExtractAtParameters(
                        "suffix_tokenShorterThanLength_includeFalse",
                        s -> "SUFFIX_" + s,
                        true,
                        false,
                        10,
                        List.of("hi"),
                        0,
                        Set.of()
                ),
                new ExtractAtParameters(
                        "differentPositions_extractsCorrectToken",
                        s -> "PREFIX_" + s,
                        false,
                        true,
                        2,
                        List.of("alpha", "beta", "gamma"),
                        1,
                        Set.of("PREFIX_be")
                ),
                new ExtractAtParameters(
                        "defaultLength_extractsTwo",
                        s -> "PREFIX_" + s,
                        false,
                        true,
                        2,
                        List.of("hello"),
                        0,
                        Set.of("PREFIX_he")
                ),
                new ExtractAtParameters(
                        "lengthOne_extractsSingleChar",
                        s -> "CHAR_" + s,
                        false,
                        true,
                        1,
                        List.of("hello"),
                        0,
                        Set.of("CHAR_h")
                ),
                new ExtractAtParameters(
                        "suffix_lengthOne_extractsLastChar",
                        s -> "CHAR_" + s,
                        true,
                        true,
                        1,
                        List.of("hello"),
                        0,
                        Set.of("CHAR_o")
                ),
                new ExtractAtParameters(
                        "prefix_exactBoundary_tokenEqualsLength",
                        s -> "P_" + s,
                        false,
                        false,
                        3,
                        List.of("abc"),
                        0,
                        Set.of("P_abc")
                ),
                new ExtractAtParameters(
                        "suffix_exactBoundary_tokenEqualsLength",
                        s -> "S_" + s,
                        true,
                        false,
                        3,
                        List.of("abc"),
                        0,
                        Set.of("S_abc")
                ),
                new ExtractAtParameters(
                        "multipleTokens_lastPosition",
                        s -> "S_" + s,
                        true,
                        true,
                        2,
                        List.of("first", "second", "third"),
                        2,
                        Set.of("S_rd")
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void extractAt(ExtractAtParameters parameters) {
        // ARRANGE //
        SubstringFeatureExtractor<String> extractor = SubstringFeatureExtractor.builder(parameters.featureMapper())
                .ending(parameters.ending()).includeIfLessThanLength(parameters.includeIfLessThanLength())
                .length(parameters.length()).build();
        InputSequence sequence = new InputSequence(parameters.tokens());

        // ACT //
        Set<String> actual = extractor.extractAt(sequence, parameters.position());

        // ASSERT //
        assertEquals(parameters.expectedResult(), actual);
    }
}
