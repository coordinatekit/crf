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
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.coordinatekit.crf.core.InputSequence;
import org.coordinatekit.crf.core.PositionedToken;
import org.coordinatekit.crf.core.Sequence;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PatternMatchingFeatureExtractorTest {

    record ExtractAtParameters(
            String name,
            PatternMatchingFeatureExtractor extractor,
            List<String> tokens,
            int position,
            Set<Feature> expectedResult
    ) {}

    static Stream<ExtractAtParameters> extractAt() {
        return Stream.of(
                new ExtractAtParameters(
                        "matching_token_returns_matched_feature",
                        PatternMatchingFeatureExtractor.builder("[A-Z]+").matchedFeature(createFeature("IS_CAPS"))
                                .build(),
                        List.of("HELLO"),
                        0,
                        Set.of(createFeature("IS_CAPS"))
                ),
                new ExtractAtParameters(
                        "non_matching_token_returns_not_matched_feature",
                        PatternMatchingFeatureExtractor.builder("[A-Z]+").notMatchedFeature(createFeature("NOT_CAPS"))
                                .build(),
                        List.of("hello"),
                        0,
                        Set.of(createFeature("NOT_CAPS"))
                ),
                new ExtractAtParameters(
                        "matching_token_with_null_matched_feature_returns_empty_set",
                        PatternMatchingFeatureExtractor.builder("[A-Z]+").notMatchedFeature(createFeature("NOT_CAPS"))
                                .build(),
                        List.of("HELLO"),
                        0,
                        Set.of()
                ),
                new ExtractAtParameters(
                        "non_matching_token_with_null_not_matched_feature_returns_empty_set",
                        PatternMatchingFeatureExtractor.builder("[A-Z]+").matchedFeature(createFeature("IS_CAPS"))
                                .build(),
                        List.of("hello"),
                        0,
                        Set.of()
                ),
                new ExtractAtParameters(
                        "case_sensitive_pattern_does_not_match_different_case",
                        PatternMatchingFeatureExtractor.builder("[A-Z]+", true).matchedFeature(createFeature("IS_CAPS"))
                                .notMatchedFeature(createFeature("NOT_CAPS")).build(),
                        List.of("Hello"),
                        0,
                        Set.of(createFeature("NOT_CAPS"))
                ),
                new ExtractAtParameters(
                        "case_insensitive_pattern_matches_different_case",
                        PatternMatchingFeatureExtractor.builder("[A-Z]+", false)
                                .matchedFeature(createFeature("IS_ALPHA")).notMatchedFeature(createFeature("NOT_ALPHA"))
                                .build(),
                        List.of("Hello"),
                        0,
                        Set.of(createFeature("IS_ALPHA"))
                ),
                new ExtractAtParameters(
                        "builder_with_compiled_pattern",
                        PatternMatchingFeatureExtractor.builder(Pattern.compile("\\d+"))
                                .matchedFeature(createFeature("IS_NUMBER")).build(),
                        List.of("123"),
                        0,
                        Set.of(createFeature("IS_NUMBER"))
                ),
                new ExtractAtParameters(
                        "extracts_at_position_0",
                        PatternMatchingFeatureExtractor.builder("[A-Z]+").matchedFeature(createFeature("IS_CAPS"))
                                .notMatchedFeature(createFeature("NOT_CAPS")).build(),
                        List.of("hello", "WORLD", "foo"),
                        0,
                        Set.of(createFeature("NOT_CAPS"))
                ),
                new ExtractAtParameters(
                        "extracts_at_position_1",
                        PatternMatchingFeatureExtractor.builder("[A-Z]+").matchedFeature(createFeature("IS_CAPS"))
                                .notMatchedFeature(createFeature("NOT_CAPS")).build(),
                        List.of("hello", "WORLD", "foo"),
                        1,
                        Set.of(createFeature("IS_CAPS"))
                ),
                new ExtractAtParameters(
                        "extracts_at_position_2",
                        PatternMatchingFeatureExtractor.builder("[A-Z]+").matchedFeature(createFeature("IS_CAPS"))
                                .notMatchedFeature(createFeature("NOT_CAPS")).build(),
                        List.of("hello", "WORLD", "foo"),
                        2,
                        Set.of(createFeature("NOT_CAPS"))
                ),
                new ExtractAtParameters(
                        "pattern_must_match_entire_token",
                        PatternMatchingFeatureExtractor.builder("[A-Z]+").matchedFeature(createFeature("IS_CAPS"))
                                .notMatchedFeature(createFeature("NOT_CAPS")).build(),
                        List.of("HELLOworld"),
                        0,
                        Set.of(createFeature("NOT_CAPS"))
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void extractAt(ExtractAtParameters parameters) {
        // ARRANGE //
        Sequence<PositionedToken> sequence = new InputSequence(parameters.tokens());

        // ACT //
        Set<Feature> actual = parameters.extractor().extractAt(sequence, parameters.position());

        // ASSERT //
        assertEquals(parameters.expectedResult(), actual);
    }
}
