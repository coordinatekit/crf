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
package org.coordinatekit.crf.core.feature.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

/**
 * Tests {@link ParameterDescriptor}: the builder's defaults and the cross-field invariants it
 * enforces at {@link ParameterDescriptor.Builder#build() build}.
 */
class ParameterDescriptorTest {
    @Test
    void builder__appliesDefaults() {
        // ACT //
        ParameterDescriptor descriptor = ParameterDescriptor.builder("length", ParameterKind.INTEGER).build();

        // ASSERT //
        assertEquals("length", descriptor.name());
        assertEquals(ParameterKind.INTEGER, descriptor.kind());
        assertFalse(descriptor.required());
        assertNull(descriptor.defaultValue());
        assertEquals("", descriptor.description());
        assertEquals(Set.of(), descriptor.allowedValues());
        assertEquals(Integer.MIN_VALUE, descriptor.minimumValue());
        assertEquals(Integer.MAX_VALUE, descriptor.maximumValue());
    }

    record BuilderExceptionParameters(
            String name,
            Executable action,
            Class<? extends Exception> expectedClass,
            String expectedMessage
    ) {}

    static Stream<BuilderExceptionParameters> builder__exception() {
        return Stream.of(
                new BuilderExceptionParameters(
                        "requiredWithDefault",
                        () -> ParameterDescriptor.builder("name", ParameterKind.STRING).required(true)
                                .defaultValue("PREFIX").build(),
                        IllegalStateException.class,
                        "parameter 'name' cannot be both required and have a default value"
                ),
                new BuilderExceptionParameters(
                        "enumerationWithoutAllowedValues",
                        () -> ParameterDescriptor.builder("mode", ParameterKind.ENUMERATION).build(),
                        IllegalStateException.class,
                        "enumeration parameter 'mode' must declare at least one allowed value"
                ),
                new BuilderExceptionParameters(
                        "allowedValuesOnNonEnumeration",
                        () -> ParameterDescriptor.builder("name", ParameterKind.STRING).allowedValues(Set.of("a"))
                                .build(),
                        IllegalStateException.class,
                        "allowed values apply only to enumeration parameters, but 'name' is STRING"
                ),
                new BuilderExceptionParameters(
                        "enumerationDefaultOutsideAllowedValues",
                        () -> ParameterDescriptor.builder("mode", ParameterKind.ENUMERATION)
                                .allowedValues(Set.of("fast", "slow")).defaultValue("medium").build(),
                        IllegalStateException.class,
                        "default value 'medium' of enumeration parameter 'mode' is not among the allowed values"
                                + " [fast, slow]"
                ),
                new BuilderExceptionParameters(
                        "boundsOnNonInteger",
                        () -> ParameterDescriptor.builder("name", ParameterKind.STRING).minimumValue(0).build(),
                        IllegalStateException.class,
                        "bounds apply only to integer parameters, but 'name' is STRING"
                ),
                new BuilderExceptionParameters(
                        "minimumExceedsMaximum",
                        () -> ParameterDescriptor.builder("length", ParameterKind.INTEGER).minimumValue(5)
                                .maximumValue(1).build(),
                        IllegalStateException.class,
                        "minimum 5 of parameter 'length' exceeds its maximum 1"
                ),
                new BuilderExceptionParameters(
                        "defaultBelowMinimum",
                        () -> ParameterDescriptor.builder("before", ParameterKind.INTEGER).minimumValue(0)
                                .defaultValue("-1").build(),
                        IllegalStateException.class,
                        "default value '-1' of parameter 'before' must be >= 0"
                ),
                new BuilderExceptionParameters(
                        "defaultOutsideBothBounds",
                        () -> ParameterDescriptor.builder("length", ParameterKind.INTEGER).minimumValue(1)
                                .maximumValue(3).defaultValue("5").build(),
                        IllegalStateException.class,
                        "default value '5' of parameter 'length' must be between 1 and 3"
                ),
                new BuilderExceptionParameters(
                        "defaultNotAnInteger",
                        () -> ParameterDescriptor.builder("length", ParameterKind.INTEGER).minimumValue(1)
                                .defaultValue("many").build(),
                        IllegalStateException.class,
                        "default value 'many' of integer parameter 'length' is not an integer"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void builder__exception(BuilderExceptionParameters parameters) {
        // ACT //
        Exception exception = assertThrows(parameters.expectedClass(), parameters.action());

        // ASSERT //
        assertEquals(parameters.expectedMessage(), exception.getMessage());
    }

    @Test
    void builder__retainsBounds() {
        // ACT //
        ParameterDescriptor descriptor = ParameterDescriptor.builder("before", ParameterKind.INTEGER).minimumValue(0)
                .maximumValue(10).defaultValue("2").build();

        // ASSERT //
        assertEquals(0, descriptor.minimumValue());
        assertEquals(10, descriptor.maximumValue());
        assertEquals("2", descriptor.defaultValue());
    }

    @Test
    void builder__retainsEnumerationAllowedValues() {
        // ACT //
        ParameterDescriptor descriptor = ParameterDescriptor.builder("mode", ParameterKind.ENUMERATION)
                .allowedValues(Set.of("fast", "slow")).defaultValue("fast").description("the mode").build();

        // ASSERT //
        assertEquals(Set.of("fast", "slow"), descriptor.allowedValues());
        assertEquals("fast", descriptor.defaultValue());
        assertEquals("the mode", descriptor.description());
    }
}
