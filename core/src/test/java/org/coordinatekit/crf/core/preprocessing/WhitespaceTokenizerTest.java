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

import static org.coordinatekit.crf.core.preprocessing.Segments.excluded;
import static org.coordinatekit.crf.core.preprocessing.Segments.token;

import org.coordinatekit.crf.core.PositionedToken;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WhitespaceTokenizerTest {
    record TokenizeParameters(String input, List<String> expectedTokens) {}

    static Stream<TokenizeParameters> tokenizeProvider() {
        return Stream.of(
                new TokenizeParameters("Hello", List.of("Hello")),
                new TokenizeParameters("Hello world", List.of("Hello", "world")),
                new TokenizeParameters("Hello  world", List.of("Hello", "world")),
                new TokenizeParameters("Hello\tworld", List.of("Hello", "world")),
                new TokenizeParameters("Hello\nworld", List.of("Hello", "world")),
                new TokenizeParameters("  Hello  world  ", List.of("Hello", "world")),
                new TokenizeParameters("one two three four", List.of("one", "two", "three", "four"))
        );
    }

    @ParameterizedTest
    @MethodSource("tokenizeProvider")
    void tokenize(TokenizeParameters parameters) {
        var tokenizer = new WhitespaceTokenizer();

        var result = tokenizer.tokenize(parameters.input());

        assertIterableEquals(
                parameters.expectedTokens(),
                result.sequence().stream().map(PositionedToken::token).toList()
        );
        assertEquals(parameters.expectedTokens().size(), result.sequence().size());
        for (int index = 0; index < result.sequence().size(); index++) {
            assertEquals(index, result.sequence().get(index).position());
        }
    }

    record SurfaceParameters(String name, String input, List<Segment> expectedSegments) {}

    static Stream<SurfaceParameters> tokenize__capturesExcludedRuns() {
        return Stream.of(
                new SurfaceParameters(
                        "no_surrounding_whitespace",
                        "Hello world",
                        List.of(token("Hello"), excluded(" "), token("world"))
                ),
                new SurfaceParameters(
                        "collapsed_runs_preserved",
                        "Hello  world",
                        List.of(token("Hello"), excluded("  "), token("world"))
                ),
                new SurfaceParameters(
                        "leading_and_trailing",
                        "  Hello  world  ",
                        List.of(excluded("  "), token("Hello"), excluded("  "), token("world"), excluded("  "))
                ),
                new SurfaceParameters(
                        "tabs_and_newlines",
                        "Hello\tworld\n",
                        List.of(token("Hello"), excluded("\t"), token("world"), excluded("\n"))
                ),
                new SurfaceParameters("single_token_trailing", "Hello ", List.of(token("Hello"), excluded(" "))),
                new SurfaceParameters("single_token_leading", " Hello", List.of(excluded(" "), token("Hello")))
        );
    }

    @MethodSource
    @ParameterizedTest
    void tokenize__capturesExcludedRuns(SurfaceParameters parameters) {
        // ARRANGE //
        var tokenizer = new WhitespaceTokenizer();

        // ACT //
        var result = tokenizer.tokenize(parameters.input());

        // ASSERT //
        assertEquals(parameters.expectedSegments(), result.segments());
        assertEquals(parameters.input(), result.surface());
    }

    record TokenizeExceptionParameters(
            String input,
            Class<? extends RuntimeException> expectedClass,
            String expectedMessage
    ) {}

    @SuppressWarnings({"DataFlowIssue", "NullAway"})
    static Stream<TokenizeExceptionParameters> tokenize_exception() {
        return Stream.of(
                new TokenizeExceptionParameters(null, NullPointerException.class, "The input string may not be null."),
                new TokenizeExceptionParameters(
                        "",
                        InvalidInputException.class,
                        "The input sequence `` is invalid with the following reason: `The input string is empty`."
                ),
                new TokenizeExceptionParameters(
                        "   ",
                        InvalidInputException.class,
                        "The input sequence `   ` is invalid with the following reason: `The input string is blank`."
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void tokenize_exception(TokenizeExceptionParameters parameters) {
        var tokenizer = new WhitespaceTokenizer();

        RuntimeException exception = assertThrows(
                parameters.expectedClass(),
                () -> tokenizer.tokenize(parameters.input())
        );
        assertEquals(parameters.expectedMessage(), exception.getMessage());
    }
}
