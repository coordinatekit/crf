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
package org.coordinatekit.crf.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class InputSequenceTest {
    record ExceptionParameters(Class<? extends Throwable> expectedException, String message, Executable executable) {}

    record SequenceParameters(List<String> input, List<String> expectedTokens, List<Integer> expectedPositions) {}

    static Stream<SequenceParameters> sequenceProvider() {
        return Stream.of(
                new SequenceParameters(List.of("Hello"), List.of("Hello"), List.of(0)),
                new SequenceParameters(List.of("Hello", "world", "!"), List.of("Hello", "world", "!"), List.of(0, 1, 2))
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
                        "There must be one or more tokens provided to an input sequence.",
                        () -> new InputSequence(List.of())
                )
        );
    }

    @Test
    void get() {
        var sequence = new InputSequence(List.of("Hello"));

        assertEquals(0, sequence.get(0).position());
        assertEquals("Hello", sequence.get(0).token());
    }

    @Test
    void get__throwsOnInvalidIndex() {
        var sequence = new InputSequence(List.of("Hello"));

        assertThrows(IndexOutOfBoundsException.class, () -> sequence.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> sequence.get(1));
    }

    @ParameterizedTest
    @MethodSource("sequenceProvider")
    void iterator(SequenceParameters parameters) {
        var sequence = new InputSequence(parameters.input());

        var actualPositions = new ArrayList<Integer>();
        var actualTokens = new ArrayList<String>();

        for (var token : sequence) {
            actualPositions.add(token.position());
            actualTokens.add(token.token());
        }

        assertIterableEquals(parameters.expectedPositions(), actualPositions);
        assertIterableEquals(parameters.expectedTokens(), actualTokens);
    }

    @ParameterizedTest
    @MethodSource("sequenceProvider")
    void size(SequenceParameters parameters) {
        var sequence = new InputSequence(parameters.input());

        assertEquals(parameters.input().size(), sequence.size());
    }

    @ParameterizedTest
    @MethodSource("sequenceProvider")
    void stream(SequenceParameters parameters) {
        var sequence = new InputSequence(parameters.input());

        assertIterableEquals(parameters.expectedPositions(), sequence.stream().map(PositionedToken::position).toList());
        assertIterableEquals(parameters.expectedTokens(), sequence.stream().map(PositionedToken::token).toList());
    }
}
