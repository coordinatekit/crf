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

import static org.coordinatekit.crf.core.feature.Feature.createFeature;

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
    record ExceptionParameters(
            String name,
            Executable action,
            Class<? extends Exception> expectedClass,
            String expectedMessage
    ) {}

    record SequenceParameters(
            String name,
            List<String> tokens,
            List<String> tags,
            List<Set<Feature>> features,
            List<String> expectedTokens,
            List<Integer> expectedPositions,
            List<String> expectedTags,
            List<Set<Feature>> expectedFeatures
    ) {}

    static Stream<SequenceParameters> sequences() {
        return Stream.of(
                new SequenceParameters(
                        "single_token",
                        List.of("Hello"),
                        List.of("GREETING"),
                        List.of(Set.of(createFeature("f1"), createFeature("f2"))),
                        List.of("Hello"),
                        List.of(0),
                        List.of("GREETING"),
                        List.of(Set.of(createFeature("f1"), createFeature("f2")))
                ),
                new SequenceParameters(
                        "three_tokens",
                        List.of("Hello", "world", "!"),
                        List.of("GREETING", "NOUN", "PUNCT"),
                        List.of(
                                Set.of(createFeature("f1")),
                                Set.of(createFeature("f2"), createFeature("f3")),
                                Set.of(createFeature("f4"))
                        ),
                        List.of("Hello", "world", "!"),
                        List.of(0, 1, 2),
                        List.of("GREETING", "NOUN", "PUNCT"),
                        List.of(
                                Set.of(createFeature("f1")),
                                Set.of(createFeature("f2"), createFeature("f3")),
                                Set.of(createFeature("f4"))
                        )
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void constructor__exception(ExceptionParameters parameters) {
        // ACT //
        Exception exception = assertThrows(parameters.expectedClass(), parameters.action());

        // ASSERT //
        assertEquals(parameters.expectedMessage(), exception.getMessage(), parameters.name());
    }

    static Stream<ExceptionParameters> constructor__exception() {
        return Stream.of(
                new ExceptionParameters(
                        "tags_size_mismatch",
                        () -> new FeatureTrainingSequence<>(
                                List.of("Hello"),
                                List.of("GREETING", "SALUTATION"),
                                List.of(Set.of(createFeature("f1")))
                        ),
                        IllegalArgumentException.class,
                        "The number of tags must be equal to the number of tokens. (tokens: 1, tags: 2)"
                ),
                new ExceptionParameters(
                        "features_size_mismatch",
                        () -> new FeatureTrainingSequence<>(
                                List.of("Hello"),
                                List.of("GREETING"),
                                List.of(Set.of(createFeature("f1")), Set.of(createFeature("f2")))
                        ),
                        IllegalArgumentException.class,
                        "The number of features must be equal to the number of tokens. (tokens: 1, features: 2)"
                ),
                new ExceptionParameters(
                        "empty_tokens",
                        () -> new FeatureTrainingSequence<>(List.of(), List.of(), List.of()),
                        IllegalArgumentException.class,
                        "There must be one or more tokens provided to a feature training sequence."
                )
        );
    }

    @Test
    void get() {
        var sequence = new FeatureTrainingSequence<>(
                List.of("Hello"),
                List.of("GREETING"),
                List.of(Set.of(createFeature("f1")))
        );

        assertIterableEquals(Set.of(createFeature("f1")), sequence.get(0).features());
        assertEquals(0, sequence.get(0).position());
        assertEquals("GREETING", sequence.get(0).tag());
        assertEquals("Hello", sequence.get(0).token());
    }

    @Test
    void get__throwsOnInvalidIndex() {
        var sequence = new FeatureTrainingSequence<>(
                List.of("Hello"),
                List.of("NOUN"),
                List.of(Set.of(createFeature("f1")))
        );

        assertThrows(IndexOutOfBoundsException.class, () -> sequence.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> sequence.get(1));
    }

    @ParameterizedTest
    @MethodSource("sequences")
    void iterator(SequenceParameters parameters) {
        var sequence = new FeatureTrainingSequence<>(parameters.tokens(), parameters.tags(), parameters.features());

        var actualFeatures = new ArrayList<Set<Feature>>();
        var actualPositions = new ArrayList<Integer>();
        var actualTags = new ArrayList<String>();
        var actualTokens = new ArrayList<String>();

        for (var token : sequence) {
            actualFeatures.add(token.features());
            actualPositions.add(token.position());
            actualTags.add(token.tag());
            actualTokens.add(token.token());
        }

        assertIterableEquals(parameters.expectedFeatures(), actualFeatures, parameters.name());
        assertIterableEquals(parameters.expectedPositions(), actualPositions, parameters.name());
        assertIterableEquals(parameters.expectedTags(), actualTags, parameters.name());
        assertIterableEquals(parameters.expectedTokens(), actualTokens, parameters.name());
    }

    @ParameterizedTest
    @MethodSource("sequences")
    void size(SequenceParameters parameters) {
        var sequence = new FeatureTrainingSequence<>(parameters.tokens(), parameters.tags(), parameters.features());

        assertEquals(parameters.tokens().size(), sequence.size(), parameters.name());
    }

    @ParameterizedTest
    @MethodSource("sequences")
    void stream(SequenceParameters parameters) {
        var sequence = new FeatureTrainingSequence<>(parameters.tokens(), parameters.tags(), parameters.features());

        assertIterableEquals(
                parameters.expectedFeatures(),
                sequence.stream().map(FeatureTrainingPositionedToken::features).toList(),
                parameters.name()
        );
        assertIterableEquals(
                parameters.expectedPositions(),
                sequence.stream().map(FeatureTrainingPositionedToken::position).toList(),
                parameters.name()
        );
        assertIterableEquals(
                parameters.expectedTags(),
                sequence.stream().map(FeatureTrainingPositionedToken::tag).toList(),
                parameters.name()
        );
        assertIterableEquals(
                parameters.expectedTokens(),
                sequence.stream().map(FeatureTrainingPositionedToken::token).toList(),
                parameters.name()
        );
    }
}
