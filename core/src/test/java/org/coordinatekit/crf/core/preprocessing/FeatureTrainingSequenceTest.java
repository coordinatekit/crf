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

class FeatureTrainingSequenceTest {
    record ExceptionParameters(Class<? extends Throwable> expectedException, String message, Executable executable) {}

    record SequenceParameters(
            List<String> tokens,
            List<String> tags,
            List<Set<String>> features,
            List<String> expectedTokens,
            List<Integer> expectedPositions,
            List<String> expectedTags,
            List<Set<String>> expectedFeatures
    ) {}

    static Stream<SequenceParameters> sequenceProvider() {
        return Stream.of(
                new SequenceParameters(
                        List.of("Hello"),
                        List.of("GREETING"),
                        List.of(Set.of("f1", "f2")),
                        List.of("Hello"),
                        List.of(0),
                        List.of("GREETING"),
                        List.of(Set.of("f1", "f2"))
                ),
                new SequenceParameters(
                        List.of("Hello", "world", "!"),
                        List.of("GREETING", "NOUN", "PUNCT"),
                        List.of(Set.of("f1"), Set.of("f2", "f3"), Set.of("f4")),
                        List.of("Hello", "world", "!"),
                        List.of(0, 1, 2),
                        List.of("GREETING", "NOUN", "PUNCT"),
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
                        "The number of tags must be equal to the number of tokens. (tokens: 1, tags: 2)",
                        () -> new FeatureTrainingSequence<>(
                                List.of("Hello"),
                                List.of("GREETING", "SALUTATION"),
                                List.of(Set.of("f1"))
                        )
                ),
                new ExceptionParameters(
                        IllegalArgumentException.class,
                        "The number of features must be equal to the number of tokens. (tokens: 1, features: 2)",
                        () -> new FeatureTrainingSequence<>(
                                List.of("Hello"),
                                List.of("GREETING"),
                                List.of(Set.of("f1"), Set.of("f2"))
                        )
                ),
                new ExceptionParameters(
                        IllegalArgumentException.class,
                        "There must be one or more tokens provided to a feature training sequence.",
                        () -> new FeatureTrainingSequence<>(List.of(), List.of(), List.of())
                )
        );
    }

    @Test
    void get() {
        var sequence = new FeatureTrainingSequence<>(List.of("Hello"), List.of("GREETING"), List.of(Set.of("f1")));

        assertIterableEquals(Set.of("f1"), sequence.get(0).features());
        assertEquals(0, sequence.get(0).position());
        assertEquals("GREETING", sequence.get(0).tag());
        assertEquals("Hello", sequence.get(0).token());
    }

    @Test
    void get__throwsOnInvalidIndex() {
        var sequence = new FeatureTrainingSequence<>(List.of("Hello"), List.of("NOUN"), List.of(Set.of("f1")));

        assertThrows(IndexOutOfBoundsException.class, () -> sequence.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> sequence.get(1));
    }

    @ParameterizedTest
    @MethodSource("sequenceProvider")
    void iterator(SequenceParameters parameters) {
        var sequence = new FeatureTrainingSequence<>(parameters.tokens(), parameters.tags(), parameters.features());

        var actualFeatures = new ArrayList<Set<String>>();
        var actualPositions = new ArrayList<Integer>();
        var actualTags = new ArrayList<String>();
        var actualTokens = new ArrayList<String>();

        for (var token : sequence) {
            actualFeatures.add(token.features());
            actualPositions.add(token.position());
            actualTags.add(token.tag());
            actualTokens.add(token.token());
        }

        assertIterableEquals(parameters.expectedFeatures(), actualFeatures);
        assertIterableEquals(parameters.expectedPositions(), actualPositions);
        assertIterableEquals(parameters.expectedTags(), actualTags);
        assertIterableEquals(parameters.expectedTokens(), actualTokens);
    }

    @ParameterizedTest
    @MethodSource("sequenceProvider")
    void size(SequenceParameters parameters) {
        var sequence = new FeatureTrainingSequence<>(parameters.tokens(), parameters.tags(), parameters.features());

        assertEquals(parameters.tokens().size(), sequence.size());
    }

    @ParameterizedTest
    @MethodSource("sequenceProvider")
    void stream(SequenceParameters parameters) {
        var sequence = new FeatureTrainingSequence<>(parameters.tokens(), parameters.tags(), parameters.features());

        assertIterableEquals(
                parameters.expectedFeatures(),
                sequence.stream().map(FeatureTrainingPositionedToken::features).toList()
        );
        assertIterableEquals(
                parameters.expectedPositions(),
                sequence.stream().map(FeatureTrainingPositionedToken::position).toList()
        );
        assertIterableEquals(
                parameters.expectedTags(),
                sequence.stream().map(FeatureTrainingPositionedToken::tag).toList()
        );
        assertIterableEquals(
                parameters.expectedTokens(),
                sequence.stream().map(FeatureTrainingPositionedToken::token).toList()
        );
    }
}
