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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.coordinatekit.crf.core.InputSequence;
import org.coordinatekit.crf.core.PositionedToken;
import org.coordinatekit.crf.core.Sequence;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TransformingFeatureExtractorTest {
    private static final Function<String, Set<Feature>> CLASSIFYING_TRANSFORMER = token -> {
        if (token.matches("\\d+")) {
            return Set.of(Features.of("IS_NUMBER"));
        } else if (token.matches("[A-Z]+")) {
            return Set.of(Features.of("IS_CAPS"));
        } else {
            return Set.of(Features.of("OTHER"));
        }
    };

    record ExtractAtParameters(
            String name,
            Function<String, Set<Feature>> transformer,
            List<String> tokens,
            int position,
            Set<Feature> expectedResult
    ) {}

    static Stream<ExtractAtParameters> extractAt() {
        return Stream.of(
                new ExtractAtParameters(
                        "transformer_applied_to_token",
                        token -> Set.of(Features.of("LENGTH", String.valueOf(token.length()))),
                        List.of("hello"),
                        0,
                        Set.of(Features.of("LENGTH", "5"))
                ),
                new ExtractAtParameters(
                        "transformer_returns_multiple_features",
                        token -> Set.of(
                                Features.of("LENGTH", String.valueOf(token.length())),
                                Features.of("LOWER", token.toLowerCase(Locale.ROOT)),
                                Features.of("UPPER", token.toUpperCase(Locale.ROOT))
                        ),
                        List.of("Hello"),
                        0,
                        Set.of(Features.of("LENGTH", "5"), Features.of("LOWER", "hello"), Features.of("UPPER", "HELLO"))
                ),
                new ExtractAtParameters(
                        "transformer_returns_empty_set",
                        token -> Set.of(),
                        List.of("hello"),
                        0,
                        Set.of()
                ),
                new ExtractAtParameters(
                        "extracts_at_position_0",
                        token -> Set.of(Features.of("TOKEN", token)),
                        List.of("first", "second", "third"),
                        0,
                        Set.of(Features.of("TOKEN", "first"))
                ),
                new ExtractAtParameters(
                        "extracts_at_position_1",
                        token -> Set.of(Features.of("TOKEN", token)),
                        List.of("first", "second", "third"),
                        1,
                        Set.of(Features.of("TOKEN", "second"))
                ),
                new ExtractAtParameters(
                        "extracts_at_position_2",
                        token -> Set.of(Features.of("TOKEN", token)),
                        List.of("first", "second", "third"),
                        2,
                        Set.of(Features.of("TOKEN", "third"))
                ),
                new ExtractAtParameters(
                        "conditional_logic_number",
                        CLASSIFYING_TRANSFORMER,
                        List.of("123", "ABC", "hello"),
                        0,
                        Set.of(Features.of("IS_NUMBER"))
                ),
                new ExtractAtParameters(
                        "conditional_logic_caps",
                        CLASSIFYING_TRANSFORMER,
                        List.of("123", "ABC", "hello"),
                        1,
                        Set.of(Features.of("IS_CAPS"))
                ),
                new ExtractAtParameters(
                        "conditional_logic_other",
                        CLASSIFYING_TRANSFORMER,
                        List.of("123", "ABC", "hello"),
                        2,
                        Set.of(Features.of("OTHER"))
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void extractAt(ExtractAtParameters parameters) {
        // ARRANGE //
        TransformingFeatureExtractor extractor = new TransformingFeatureExtractor(parameters.transformer());
        Sequence<PositionedToken> sequence = new InputSequence(parameters.tokens());

        // ACT //
        Set<Feature> actual = extractor.extractAt(sequence, parameters.position());

        // ASSERT //
        assertEquals(parameters.expectedResult(), actual);
    }
}
