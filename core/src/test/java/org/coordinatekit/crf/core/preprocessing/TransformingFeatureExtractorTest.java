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
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@NullMarked
class TransformingFeatureExtractorTest {

    record ExtractAtParameters(
            String name,
            Function<String, Set<String>> transformer,
            List<String> tokens,
            int position,
            Set<String> expectedResult
    ) {}

    static Stream<ExtractAtParameters> extractAt() {
        return Stream.of(
                new ExtractAtParameters(
                        "transformer_applied_to_token",
                        token -> Set.of("LENGTH=" + token.length()),
                        List.of("hello"),
                        0,
                        Set.of("LENGTH=5")
                ),
                new ExtractAtParameters(
                        "transformer_returns_multiple_features",
                        token -> Set.of(
                                "LENGTH=" + token.length(),
                                "LOWER=" + token.toLowerCase(Locale.ROOT),
                                "UPPER=" + token.toUpperCase(Locale.ROOT)
                        ),
                        List.of("Hello"),
                        0,
                        Set.of("LENGTH=5", "LOWER=hello", "UPPER=HELLO")
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
                        token -> Set.of("TOKEN=" + token),
                        List.of("first", "second", "third"),
                        0,
                        Set.of("TOKEN=first")
                ),
                new ExtractAtParameters(
                        "extracts_at_position_1",
                        token -> Set.of("TOKEN=" + token),
                        List.of("first", "second", "third"),
                        1,
                        Set.of("TOKEN=second")
                ),
                new ExtractAtParameters(
                        "extracts_at_position_2",
                        token -> Set.of("TOKEN=" + token),
                        List.of("first", "second", "third"),
                        2,
                        Set.of("TOKEN=third")
                ),
                new ExtractAtParameters("conditional_logic_number", token -> {
                    if (token.matches("\\d+")) {
                        return Set.of("IS_NUMBER");
                    } else if (token.matches("[A-Z]+")) {
                        return Set.of("IS_CAPS");
                    } else {
                        return Set.of("OTHER");
                    }
                }, List.of("123", "ABC", "hello"), 0, Set.of("IS_NUMBER")),
                new ExtractAtParameters("conditional_logic_caps", token -> {
                    if (token.matches("\\d+")) {
                        return Set.of("IS_NUMBER");
                    } else if (token.matches("[A-Z]+")) {
                        return Set.of("IS_CAPS");
                    } else {
                        return Set.of("OTHER");
                    }
                }, List.of("123", "ABC", "hello"), 1, Set.of("IS_CAPS")),
                new ExtractAtParameters("conditional_logic_other", token -> {
                    if (token.matches("\\d+")) {
                        return Set.of("IS_NUMBER");
                    } else if (token.matches("[A-Z]+")) {
                        return Set.of("IS_CAPS");
                    } else {
                        return Set.of("OTHER");
                    }
                }, List.of("123", "ABC", "hello"), 2, Set.of("OTHER"))
        );
    }

    @MethodSource
    @ParameterizedTest
    void extractAt(ExtractAtParameters parameters) {
        // ARRANGE //
        TransformingFeatureExtractor<String> extractor = new TransformingFeatureExtractor<>(parameters.transformer());
        Sequence<PositionedToken> sequence = new InputSequence(parameters.tokens());

        // ACT //
        Set<String> actual = extractor.extractAt(sequence, parameters.position());

        // ASSERT //
        assertEquals(parameters.expectedResult(), actual);
    }

    @Test
    void extractAt__integerFeatures() {
        // ARRANGE //
        TransformingFeatureExtractor<Integer> extractor = new TransformingFeatureExtractor<>(
                token -> Set.of(token.length(), token.hashCode())
        );
        Sequence<PositionedToken> sequence = new InputSequence(List.of("hi"));

        // ACT //
        Set<Integer> actual = extractor.extractAt(sequence, 0);

        // ASSERT //
        assertEquals(Set.of(2, "hi".hashCode()), actual);
    }
}
