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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class FeatureSequenceTest {
    record ExceptionParameters(Class<? extends Throwable> expectedException, String message, Executable executable) {}

    record SequenceParameters(
            List<String> tokens,
            List<Set<String>> features,
            List<String> expectedTokens,
            List<Integer> expectedPositions,
            List<Set<String>> expectedFeatures
    ) {}

    static Stream<SequenceParameters> sequenceProvider() {
        return Stream.of(
                new SequenceParameters(
                        List.of("Hello"),
                        List.of(Set.of("f1", "f2")),
                        List.of("Hello"),
                        List.of(0),
                        List.of(Set.of("f1", "f2"))
                ),
                new SequenceParameters(
                        List.of("Hello", "world", "!"),
                        List.of(Set.of("f1"), Set.of("f2", "f3"), Set.of("f4")),
                        List.of("Hello", "world", "!"),
                        List.of(0, 1, 2),
                        List.of(Set.of("f1"), Set.of("f2", "f3"), Set.of("f4"))
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void constructor__exception(ExceptionParameters parameters) {
        Throwable t = assertThrows(parameters.expectedException(), parameters.executable());
        assertEquals(parameters.message(), t.getMessage());
    }

    static Stream<ExceptionParameters> constructor__exception() {
        return Stream.of(
                new ExceptionParameters(
                        IllegalArgumentException.class,
                        "The number of features must be equal to the number of tokens. (tokens: 1, features: 2)",
                        () -> new FeatureSequence<>(List.of("Hello"), List.of(Set.of("f1"), Set.of("f2")))
                ),
                new ExceptionParameters(
                        IllegalArgumentException.class,
                        "There must be one or more tokens provided to a feature sequence.",
                        () -> new FeatureSequence<>(List.of(), List.of())
                )
        );
    }

    @Test
    void get() {
        var sequence = new FeatureSequence<>(List.of("Hello"), List.of(Set.of("f1")));

        assertIterableEquals(Set.of("f1"), sequence.get(0).features());
        assertEquals(0, sequence.get(0).position());
        assertEquals("Hello", sequence.get(0).token());
    }

    @Test
    void get__throwsOnInvalidIndex() {
        var sequence = new FeatureSequence<>(List.of("Hello"), List.of(Set.of("f1")));

        assertThrows(IndexOutOfBoundsException.class, () -> sequence.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> sequence.get(1));
    }

    @ParameterizedTest
    @MethodSource("sequenceProvider")
    void iterator(SequenceParameters parameters) {
        var sequence = new FeatureSequence<>(parameters.tokens(), parameters.features());

        var actualFeatures = new ArrayList<Set<String>>();
        var actualPositions = new ArrayList<Integer>();
        var actualTokens = new ArrayList<String>();

        for (var token : sequence) {
            actualFeatures.add(token.features());
            actualPositions.add(token.position());
            actualTokens.add(token.token());
        }

        assertIterableEquals(parameters.expectedFeatures(), actualFeatures);
        assertIterableEquals(parameters.expectedPositions(), actualPositions);
        assertIterableEquals(parameters.expectedTokens(), actualTokens);
    }

    @ParameterizedTest
    @MethodSource("sequenceProvider")
    void size(SequenceParameters parameters) {
        var sequence = new FeatureSequence<>(parameters.tokens(), parameters.features());

        assertEquals(parameters.tokens().size(), sequence.size());
    }

    @ParameterizedTest
    @MethodSource("sequenceProvider")
    void stream(SequenceParameters parameters) {
        var sequence = new FeatureSequence<>(parameters.tokens(), parameters.features());

        assertIterableEquals(
                parameters.expectedFeatures(),
                sequence.stream().map(FeaturePositionedToken::features).toList()
        );
        assertIterableEquals(
                parameters.expectedPositions(),
                sequence.stream().map(FeaturePositionedToken::position).toList()
        );
        assertIterableEquals(
                parameters.expectedTokens(),
                sequence.stream().map(FeaturePositionedToken::token).toList()
        );
    }
}
