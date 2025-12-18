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
import org.coordinatekit.crf.core.PositionedToken;
import org.coordinatekit.crf.core.Sequence;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

@NullMarked
class FeatureExtractorTest {
    private static final FeatureExtractor<String> SIMPLE_FEATURE_EXTRACTOR = (sequence, position) -> {
        String token = sequence.get(position).token();
        return Set.of("LENGTH=" + token.length(), "LOWER=" + token.toLowerCase(Locale.ROOT));
    };

    record ExtractParameters(List<String> tokens, List<Set<String>> expectedFeatures) {}

    static Stream<ExtractParameters> extractProvider() {
        return Stream.of(
                new ExtractParameters(List.of("Hello"), List.of(Set.of("LENGTH=5", "LOWER=hello"))),
                new ExtractParameters(
                        List.of("Hello", "World"),
                        List.of(Set.of("LENGTH=5", "LOWER=hello"), Set.of("LENGTH=5", "LOWER=world"))
                ),
                new ExtractParameters(
                        List.of("A", "BB", "CCC"),
                        List.of(
                                Set.of("LENGTH=1", "LOWER=a"),
                                Set.of("LENGTH=2", "LOWER=bb"),
                                Set.of("LENGTH=3", "LOWER=ccc")
                        )
                )
        );
    }

    @ParameterizedTest
    @MethodSource("extractProvider")
    void extract(ExtractParameters parameters) {
        Sequence<PositionedToken> input = new InputSequence(parameters.tokens());
        Sequence<FeaturePositionedToken<String>> result = SIMPLE_FEATURE_EXTRACTOR.extract(input);

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

    record ExtractTrainingParameters(List<String> tokens, List<String> tags, List<Set<String>> expectedFeatures) {}

    static Stream<ExtractTrainingParameters> extractTrainingProvider() {
        return Stream.of(
                new ExtractTrainingParameters(
                        List.of("Hello"),
                        List.of("GREETING"),
                        List.of(Set.of("LENGTH=5", "LOWER=hello"))
                ),
                new ExtractTrainingParameters(
                        List.of("New", "York"),
                        List.of("CITY", "CITY"),
                        List.of(Set.of("LENGTH=3", "LOWER=new"), Set.of("LENGTH=4", "LOWER=york"))
                ),
                new ExtractTrainingParameters(
                        List.of("123", "Main", "St"),
                        List.of("NUMBER", "STREET", "SUFFIX"),
                        List.of(
                                Set.of("LENGTH=3", "LOWER=123"),
                                Set.of("LENGTH=4", "LOWER=main"),
                                Set.of("LENGTH=2", "LOWER=st")
                        )
                )
        );
    }

    @ParameterizedTest
    @MethodSource("extractTrainingProvider")
    void extractTraining(ExtractTrainingParameters parameters) {
        Sequence<TrainingPositionedToken<String>> input = new TrainingSequence<>(
                parameters.tokens(),
                parameters.tags()
        );
        Sequence<FeatureTrainingPositionedToken<String, String>> result = SIMPLE_FEATURE_EXTRACTOR
                .extractTraining(input);

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
