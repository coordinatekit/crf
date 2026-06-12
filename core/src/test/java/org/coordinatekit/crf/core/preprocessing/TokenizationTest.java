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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenizationTest {
    record SurfaceParameters(String name, List<Segment> segments, String expectedSurface) {}

    static Stream<SurfaceParameters> surface() {
        return Stream.of(
                new SurfaceParameters("no_excluded", List.of(token("Hello"), token("world")), "Helloworld"),
                new SurfaceParameters(
                        "single_space_between",
                        List.of(token("Hello"), excluded(" "), token("world")),
                        "Hello world"
                ),
                new SurfaceParameters(
                        "leading_and_trailing",
                        List.of(excluded("  "), token("Hello"), excluded(" "), token("world"), excluded("\n")),
                        "  Hello world\n"
                ),
                new SurfaceParameters("single_token", List.of(token("Hello")), "Hello")
        );
    }

    @MethodSource
    @ParameterizedTest
    void surface(SurfaceParameters parameters) {
        // ARRANGE //
        Tokenization tokenization = new Tokenization(parameters.segments());

        // ACT //
        String surface = tokenization.surface();

        // ASSERT //
        assertEquals(parameters.expectedSurface(), surface);
    }

    @Test
    void constructor__rejectsNoTokenSegments() {
        // ACT //
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new Tokenization(List.of(excluded("  ")))
        );

        // ASSERT //
        assertEquals("There must be one or more tokens provided to a tokenization.", exception.getMessage());
    }

    @Test
    void segments__returnsDocumentOrder() {
        // ARRANGE //
        List<Segment> segments = List.of(excluded("  "), token("Hello"), excluded(" "), token("world"));
        Tokenization tokenization = new Tokenization(segments);

        // ACT & ASSERT //
        assertEquals(segments, tokenization.segments());
    }

    @Test
    void sequence__projectsTokenSegmentsOnly() {
        // ARRANGE //
        Tokenization tokenization = new Tokenization(
                List.of(excluded("  "), token("Hello"), excluded(" "), token("world"), excluded("\n"))
        );

        // ACT //
        var sequence = tokenization.sequence();

        // ASSERT //
        assertEquals(2, sequence.size());
        assertEquals("Hello", sequence.get(0).token());
        assertEquals(0, sequence.get(0).position());
        assertEquals("world", sequence.get(1).token());
        assertEquals(1, sequence.get(1).position());
        assertIterableEquals(List.of("Hello", "world"), sequence.stream().map(PositionedToken::token).toList());
    }
}
