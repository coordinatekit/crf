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
package org.coordinatekit.crf.core.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XmlTrainingDataConfigurationTest {

    @Test
    void builder__storesConfiguredValues() {
        // ARRANGE & ACT //
        XmlTrainingDataConfiguration configuration = XmlTrainingDataConfiguration.builder()
                .rootElementName("AddressCollection").targetNamespace("https://example.org/tags").build();

        // ASSERT //
        assertEquals("AddressCollection", configuration.rootElementName());
        assertEquals("https://example.org/tags", configuration.targetNamespace());
    }

    record RootElementNameExceptionParameters(
            String name,
            Executable action,
            Class<? extends Exception> expectedClass,
            String expectedMessage
    ) {}

    @SuppressWarnings({"DataFlowIssue", "NullAway"})
    static Stream<RootElementNameExceptionParameters> rootElementName__exception() {
        return Stream.of(
                new RootElementNameExceptionParameters(
                        "reserved_sequence",
                        () -> XmlTrainingDataConfiguration.builder().rootElementName("Sequence"),
                        IllegalArgumentException.class,
                        "rootElementName may not be a reserved structural element name (Sequence, Excluded)"
                ),
                new RootElementNameExceptionParameters(
                        "reserved_excluded",
                        () -> XmlTrainingDataConfiguration.builder().rootElementName("Excluded"),
                        IllegalArgumentException.class,
                        "rootElementName may not be a reserved structural element name (Sequence, Excluded)"
                ),
                new RootElementNameExceptionParameters(
                        "blank",
                        () -> XmlTrainingDataConfiguration.builder().rootElementName("   "),
                        IllegalArgumentException.class,
                        "rootElementName may not be blank"
                ),
                new RootElementNameExceptionParameters(
                        "empty",
                        () -> XmlTrainingDataConfiguration.builder().rootElementName(""),
                        IllegalArgumentException.class,
                        "rootElementName may not be blank"
                ),
                new RootElementNameExceptionParameters(
                        "null",
                        () -> XmlTrainingDataConfiguration.builder().rootElementName(null),
                        NullPointerException.class,
                        "rootElementName may not be null"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void rootElementName__exception(RootElementNameExceptionParameters parameters) {
        // ACT //
        Exception exception = assertThrows(parameters.expectedClass(), parameters.action());

        // ASSERT //
        assertEquals(parameters.expectedMessage(), exception.getMessage());
    }
}
