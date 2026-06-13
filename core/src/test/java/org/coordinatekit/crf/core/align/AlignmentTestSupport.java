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
package org.coordinatekit.crf.core.align;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.coordinatekit.crf.core.StringTagProvider;
import org.coordinatekit.crf.core.io.XmlTrainingData;
import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.coordinatekit.crf.core.preprocessing.WhitespaceTokenizer;
import org.junit.jupiter.api.function.Executable;

import java.util.Collections;
import java.util.List;

/** Shared fixtures and helpers for the {@code core/align} unit tests. */
final class AlignmentTestSupport {
    private AlignmentTestSupport() {}

    static void assertThrowsWithMessage(ExceptionCase exceptionCase) {
        Exception exception = assertThrows(exceptionCase.expectedClass(), exceptionCase.action());
        assertEquals(exceptionCase.expectedMessage(), exception.getMessage());
    }

    static AlignmentDetector<String> defaultDetector() {
        return new AlignmentDetector<>(new WhitespaceTokenizer(), new XmlTrainingData<>(new StringTagProvider("0")));
    }

    static TrainingSequence<String> sequenceOf(String... tokens) {
        return TrainingSequence.ofTokens(List.of(tokens), Collections.nCopies(tokens.length, "0"));
    }

    record ExceptionCase(
            String name,
            Executable action,
            Class<? extends Exception> expectedClass,
            String expectedMessage
    ) {}
}
