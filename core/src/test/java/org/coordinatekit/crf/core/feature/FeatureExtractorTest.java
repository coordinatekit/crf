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

import static org.coordinatekit.crf.core.feature.Feature.createFeatureWithValue;
import org.coordinatekit.crf.core.InputSequence;
import org.coordinatekit.crf.core.PositionedToken;
import org.coordinatekit.crf.core.Sequence;
import org.coordinatekit.crf.core.preprocessing.TrainingPositionedToken;
import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FeatureExtractorTest {
    private static final FeatureExtractor SIMPLE_FEATURE_EXTRACTOR = (sequence, position) -> {
        String token = sequence.get(position).token();
        return Set.of(
                createFeatureWithValue("LENGTH", String.valueOf(token.length())),
                createFeatureWithValue("LOWER", token.toLowerCase(Locale.ROOT))
        );
    };

    record ExtractParameters(List<String> tokens, List<Set<Feature>> expectedFeatures) {}

    static Stream<ExtractParameters> extractProvider() {
        return Stream.of(
                new ExtractParameters(
                        List.of("Hello"),
                        List.of(Set.of(createFeatureWithValue("LENGTH", "5"), createFeatureWithValue("LOWER", "hello")))
                ),
                new ExtractParameters(
                        List.of("Hello", "World"),
                        List.of(
                                Set.of(createFeatureWithValue("LENGTH", "5"), createFeatureWithValue("LOWER", "hello")),
                                Set.of(createFeatureWithValue("LENGTH", "5"), createFeatureWithValue("LOWER", "world"))
                        )
                ),
                new ExtractParameters(
                        List.of("A", "BB", "CCC"),
                        List.of(
                                Set.of(createFeatureWithValue("LENGTH", "1"), createFeatureWithValue("LOWER", "a")),
                                Set.of(createFeatureWithValue("LENGTH", "2"), createFeatureWithValue("LOWER", "bb")),
                                Set.of(createFeatureWithValue("LENGTH", "3"), createFeatureWithValue("LOWER", "ccc"))
                        )
                )
        );
    }

    @ParameterizedTest
    @MethodSource("extractProvider")
    void extract(ExtractParameters parameters) {
        Sequence<PositionedToken> input = new InputSequence(parameters.tokens());
        Sequence<FeaturePositionedToken> result = SIMPLE_FEATURE_EXTRACTOR.extract(input);

        assertEquals(parameters.tokens().size(), result.size());
        for (int i = 0; i < result.size(); i++) {
            int position = i;
            String token = parameters.tokens().get(i);

            assertAll(
                    "position " + position + " (token: " + token + ")",
                    () -> assertEquals(token, result.get(position).token(), "token"),
                    () -> assertEquals(position, result.get(position).position(), "position"),
                    () -> assertEquals(
                            parameters.expectedFeatures().get(position),
                            result.get(position).features(),
                            "features"
                    )
            );
        }
    }

    record ExtractTrainingParameters(List<String> tokens, List<String> tags, List<Set<Feature>> expectedFeatures) {}

    static Stream<ExtractTrainingParameters> extractTrainingProvider() {
        return Stream.of(
                new ExtractTrainingParameters(
                        List.of("Hello"),
                        List.of("GREETING"),
                        List.of(Set.of(createFeatureWithValue("LENGTH", "5"), createFeatureWithValue("LOWER", "hello")))
                ),
                new ExtractTrainingParameters(
                        List.of("New", "York"),
                        List.of("CITY", "CITY"),
                        List.of(
                                Set.of(createFeatureWithValue("LENGTH", "3"), createFeatureWithValue("LOWER", "new")),
                                Set.of(createFeatureWithValue("LENGTH", "4"), createFeatureWithValue("LOWER", "york"))
                        )
                ),
                new ExtractTrainingParameters(
                        List.of("123", "Main", "St"),
                        List.of("NUMBER", "STREET", "SUFFIX"),
                        List.of(
                                Set.of(createFeatureWithValue("LENGTH", "3"), createFeatureWithValue("LOWER", "123")),
                                Set.of(createFeatureWithValue("LENGTH", "4"), createFeatureWithValue("LOWER", "main")),
                                Set.of(createFeatureWithValue("LENGTH", "2"), createFeatureWithValue("LOWER", "st"))
                        )
                )
        );
    }

    @ParameterizedTest
    @MethodSource("extractTrainingProvider")
    void extractTraining(ExtractTrainingParameters parameters) {
        Sequence<TrainingPositionedToken<String>> input = TrainingSequence
                .ofTokens(parameters.tokens(), parameters.tags());
        Sequence<FeatureTrainingPositionedToken<String>> result = SIMPLE_FEATURE_EXTRACTOR.extractTraining(input);

        assertEquals(parameters.tokens().size(), result.size());
        for (int i = 0; i < result.size(); i++) {
            int position = i;
            String token = parameters.tokens().get(i);

            assertAll(
                    "position " + position + " (token: " + token + ")",
                    () -> assertEquals(token, result.get(position).token(), "token"),
                    () -> assertEquals(position, result.get(position).position(), "position"),
                    () -> assertEquals(parameters.tags().get(position), result.get(position).tag(), "tag"),
                    () -> assertEquals(
                            parameters.expectedFeatures().get(position),
                            result.get(position).features(),
                            "features"
                    )
            );
        }
    }
}
