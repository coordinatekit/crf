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
package org.coordinatekit.crf.core.tag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TaggedSequenceTest {
    record ExceptionParameters(Class<? extends Throwable> expectedException, String message, Executable executable) {}

    record SequenceParameters(
            List<String> tokens,
            List<Set<String>> features,
            List<Map<String, Double>> tagScores,
            List<String> expectedTokens,
            List<Set<String>> expectedFeatures,
            List<String> expectedBestTags,
            List<List<String>> expectedTags,
            List<List<String>> expectedFirstTwoTags,
            List<List<Double>> expectedTagScores,
            List<Integer> expectedPositions
    ) {}

    static Stream<SequenceParameters> sequenceProvider() {
        return Stream.of(
                new SequenceParameters(
                        List.of("Hello"),
                        List.of(Set.of("f1")),
                        List.of(Map.of("TAG", 0.5, "ANOTHER_TAG", 0.5)),
                        List.of("Hello"),
                        List.of(Set.of("f1")),
                        List.of("ANOTHER_TAG"),
                        List.of(List.of("ANOTHER_TAG", "TAG")),
                        List.of(List.of("ANOTHER_TAG", "TAG")),
                        List.of(List.of(0.5, 0.5)),
                        List.of(0)
                ),
                new SequenceParameters(
                        List.of("Hello", "world", "!"),
                        List.of(Set.of("f1"), Set.of("f2"), Set.of("f3")),
                        List.of(
                                Map.of("NOUN", 0.2, "INTERJECTION", 0.75, "VERB", 0.05),
                                Map.of("NOUN", 0.7),
                                Map.of("PUNCT", 0.9)
                        ),
                        List.of("Hello", "world", "!"),
                        List.of(Set.of("f1"), Set.of("f2"), Set.of("f3")),
                        List.of("INTERJECTION", "NOUN", "PUNCT"),
                        List.of(List.of("INTERJECTION", "NOUN", "VERB"), List.of("NOUN"), List.of("PUNCT")),
                        List.of(List.of("INTERJECTION", "NOUN"), List.of("NOUN"), List.of("PUNCT")),
                        List.of(List.of(0.75, 0.2, 0.05), List.of(0.7), List.of(0.9)),
                        List.of(0, 1, 2)
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
                        () -> new TaggedSequence<>(
                                List.of("Hello"),
                                List.of(Set.of("f1"), Set.of("f2")),
                                List.of(Map.of("GREETING", 1.0d))
                        )
                ),
                new ExceptionParameters(
                        IllegalArgumentException.class,
                        "The number of tag scores must be equal to the number of tokens. (tokens: 1, tag scores: 2)",
                        () -> new TaggedSequence<>(
                                List.of("Hello"),
                                List.of(Set.of("f1")),
                                List.of(Map.of("GREETING", 1d), Map.of("SALUTATION", 1d))
                        )
                ),
                new ExceptionParameters(
                        IllegalArgumentException.class,
                        "There must be one or more tokens provided to a tagged sequence.",
                        () -> new TaggedSequence<>(List.of(), List.of(), List.of())
                )
        );
    }

    @Test
    void get() {
        var sequence = new TaggedSequence<>(
                List.of("Hello"),
                List.of(Set.of("f1")),
                List.of(Map.of("TAG", 0.5, "ANOTHER_TAG", 0.5))
        );

        assertIterableEquals(Set.of("f1"), sequence.get(0).features());
        assertEquals(0, sequence.get(0).position());
        assertEquals("ANOTHER_TAG", sequence.get(0).tag());
        assertIterableEquals(List.of("ANOTHER_TAG", "TAG"), sequence.get(0).tag(0));
        assertIterableEquals(List.of("ANOTHER_TAG"), sequence.get(0).tag(1));
        assertIterableEquals(List.of(0.5, 0.5), sequence.get(0).tagScores().stream().map(TagScore::score).toList());
        assertEquals("Hello", sequence.get(0).token());
    }

    @Test
    void get__throwsOnInvalidIndex() {
        var sequence = new TaggedSequence<>(List.of("Hello"), List.of(Set.of("f1")), List.of(Map.of("TAG", 0.5)));

        assertThrows(IndexOutOfBoundsException.class, () -> sequence.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> sequence.get(1));
    }

    @ParameterizedTest
    @MethodSource("sequenceProvider")
    void iterator(SequenceParameters parameters) {
        var sequence = new TaggedSequence<>(parameters.tokens(), parameters.features(), parameters.tagScores());

        var actualBestTags = new ArrayList<String>();
        var actualFeatures = new ArrayList<Set<String>>();
        var actualFirstTwoTags = new ArrayList<List<String>>();
        var actualPositions = new ArrayList<Integer>();
        var actualTags = new ArrayList<List<String>>();
        var actualTagScores = new ArrayList<List<Double>>();
        var actualTokens = new ArrayList<String>();

        for (var token : sequence) {
            actualBestTags.add(token.tag());
            actualFeatures.add(token.features());
            actualFirstTwoTags.add(token.tag(2));
            actualPositions.add(token.position());
            actualTags.add(token.tag(0));
            actualTagScores.add(token.tagScores().stream().map(TagScore::score).toList());
            actualTokens.add(token.token());
        }

        assertIterableEquals(parameters.expectedBestTags(), actualBestTags);
        assertIterableEquals(parameters.expectedFeatures(), actualFeatures);
        assertIterableEquals(parameters.expectedFirstTwoTags(), actualFirstTwoTags);
        assertIterableEquals(parameters.expectedPositions(), actualPositions);
        assertIterableEquals(parameters.expectedTags(), actualTags);
        assertIterableEquals(parameters.expectedTokens(), actualTokens);
        assertIterableEquals(parameters.expectedTagScores(), actualTagScores);
    }

    @ParameterizedTest
    @MethodSource("sequenceProvider")
    void size(SequenceParameters parameters) {
        var sequence = new TaggedSequence<>(parameters.tokens(), parameters.features(), parameters.tagScores());

        assertEquals(parameters.tokens().size(), sequence.size());
    }

    @ParameterizedTest
    @MethodSource("sequenceProvider")
    void stream(SequenceParameters parameters) {
        var sequence = new TaggedSequence<>(parameters.tokens(), parameters.features(), parameters.tagScores());

        assertIterableEquals(
                parameters.expectedFeatures(),
                sequence.stream().map(TaggedPositionedToken::features).toList()
        );
        assertIterableEquals(
                parameters.expectedPositions(),
                sequence.stream().map(TaggedPositionedToken::position).toList()
        );
        assertIterableEquals(parameters.expectedBestTags(), sequence.stream().map(TaggedPositionedToken::tag).toList());
        assertIterableEquals(parameters.expectedTags(), sequence.stream().map(t -> t.tag(0)).toList());
        assertIterableEquals(parameters.expectedFirstTwoTags(), sequence.stream().map(t -> t.tag(2)).toList());
        assertIterableEquals(parameters.expectedTokens(), sequence.stream().map(TaggedPositionedToken::token).toList());
        assertIterableEquals(
                parameters.expectedTagScores(),
                sequence.stream().map(t -> t.tagScores().stream().map(TagScore::score).toList()).toList()
        );
    }
}
