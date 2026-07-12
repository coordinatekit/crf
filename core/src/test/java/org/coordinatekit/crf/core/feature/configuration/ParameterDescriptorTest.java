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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
 * enforces at {@link ParameterDescriptor.Builder#build() build}, and the hand-written
 * {@link ParameterDescriptor#equals(Object)}, {@link ParameterDescriptor#hashCode()}, and
 * {@link ParameterDescriptor#toString()}.
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
                        "required_with_default",
                        () -> ParameterDescriptor.builder("name", ParameterKind.STRING).required(true)
                                .defaultValue("PREFIX").build(),
                        IllegalStateException.class,
                        "parameter 'name' cannot be both required and have a default value"
                ),
                new BuilderExceptionParameters(
                        "enumeration_without_allowed_values",
                        () -> ParameterDescriptor.builder("mode", ParameterKind.ENUMERATION).build(),
                        IllegalStateException.class,
                        "enumeration parameter 'mode' must declare at least one allowed value"
                ),
                new BuilderExceptionParameters(
                        "allowed_values_on_non_enumeration",
                        () -> ParameterDescriptor.builder("name", ParameterKind.STRING).allowedValues(Set.of("a"))
                                .build(),
                        IllegalStateException.class,
                        "allowed values apply only to enumeration parameters, but 'name' is STRING"
                ),
                new BuilderExceptionParameters(
                        "enumeration_default_outside_allowed_values",
                        () -> ParameterDescriptor.builder("mode", ParameterKind.ENUMERATION)
                                .allowedValues(Set.of("fast", "slow")).defaultValue("medium").build(),
                        IllegalStateException.class,
                        "default value 'medium' of enumeration parameter 'mode' is not among the allowed values"
                                + " [fast, slow]"
                ),
                new BuilderExceptionParameters(
                        "bounds_on_non_integer",
                        () -> ParameterDescriptor.builder("name", ParameterKind.STRING).minimumValue(0).build(),
                        IllegalStateException.class,
                        "bounds apply only to integer parameters, but 'name' is STRING"
                ),
                new BuilderExceptionParameters(
                        "minimum_exceeds_maximum",
                        () -> ParameterDescriptor.builder("length", ParameterKind.INTEGER).minimumValue(5)
                                .maximumValue(1).build(),
                        IllegalStateException.class,
                        "minimum 5 of parameter 'length' exceeds its maximum 1"
                ),
                new BuilderExceptionParameters(
                        "default_below_minimum",
                        () -> ParameterDescriptor.builder("before", ParameterKind.INTEGER).minimumValue(0)
                                .defaultValue("-1").build(),
                        IllegalStateException.class,
                        "default value '-1' of parameter 'before' must be >= 0"
                ),
                new BuilderExceptionParameters(
                        "default_outside_both_bounds",
                        () -> ParameterDescriptor.builder("length", ParameterKind.INTEGER).minimumValue(1)
                                .maximumValue(3).defaultValue("5").build(),
                        IllegalStateException.class,
                        "default value '5' of parameter 'length' must be between 1 and 3"
                ),
                new BuilderExceptionParameters(
                        "default_not_an_integer",
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
    void builder__retainsConfiguredValues() {
        // ARRANGE //
        ParameterDescriptor bounded = ParameterDescriptor.builder("before", ParameterKind.INTEGER).minimumValue(0)
                .maximumValue(10).defaultValue("2").description("lookback tokens").build();
        ParameterDescriptor enumerated = ParameterDescriptor.builder("mode", ParameterKind.ENUMERATION)
                .allowedValues(Set.of("fast", "slow")).defaultValue("fast").description("the mode").build();

        // ASSERT //
        assertEquals(0, bounded.minimumValue());
        assertEquals(10, bounded.maximumValue());
        assertEquals("2", bounded.defaultValue());
        assertEquals("lookback tokens", bounded.description());

        assertEquals(Set.of("fast", "slow"), enumerated.allowedValues());
        assertEquals("fast", enumerated.defaultValue());
        assertEquals("the mode", enumerated.description());
    }

    record EqualsParameters(
            String name,
            ParameterDescriptor first,
            ParameterDescriptor second,
            boolean expectedEqual
    ) {}

    static Stream<EqualsParameters> equals__comparesAllFields() {
        ParameterDescriptor base = ParameterDescriptor.builder("name", ParameterKind.STRING).build();
        ParameterDescriptor integerBase = ParameterDescriptor.builder("length", ParameterKind.INTEGER).build();
        ParameterDescriptor enumerationBase = ParameterDescriptor.builder("mode", ParameterKind.ENUMERATION)
                .allowedValues(Set.of("fast")).build();
        return Stream.of(
                new EqualsParameters(
                        "identical_fields",
                        base,
                        ParameterDescriptor.builder("name", ParameterKind.STRING).build(),
                        true
                ),
                new EqualsParameters(
                        "differs_by_name",
                        base,
                        ParameterDescriptor.builder("other", ParameterKind.STRING).build(),
                        false
                ),
                new EqualsParameters(
                        "differs_by_kind",
                        base,
                        ParameterDescriptor.builder("name", ParameterKind.BOOLEAN).build(),
                        false
                ),
                new EqualsParameters(
                        "differs_by_required",
                        base,
                        ParameterDescriptor.builder("name", ParameterKind.STRING).required(true).build(),
                        false
                ),
                new EqualsParameters(
                        "differs_by_defaultValue",
                        base,
                        ParameterDescriptor.builder("name", ParameterKind.STRING).defaultValue("x").build(),
                        false
                ),
                new EqualsParameters(
                        "differs_by_description",
                        base,
                        ParameterDescriptor.builder("name", ParameterKind.STRING).description("d").build(),
                        false
                ),
                new EqualsParameters(
                        "differs_by_minimumValue",
                        integerBase,
                        ParameterDescriptor.builder("length", ParameterKind.INTEGER).minimumValue(0).build(),
                        false
                ),
                new EqualsParameters(
                        "differs_by_maximumValue",
                        integerBase,
                        ParameterDescriptor.builder("length", ParameterKind.INTEGER).maximumValue(100).build(),
                        false
                ),
                new EqualsParameters(
                        "differs_by_allowedValues",
                        enumerationBase,
                        ParameterDescriptor.builder("mode", ParameterKind.ENUMERATION)
                                .allowedValues(Set.of("fast", "slow")).build(),
                        false
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void equals__comparesAllFields(EqualsParameters parameters) {
        // ACT & ASSERT //
        assertEquals(parameters.expectedEqual(), parameters.first().equals(parameters.second()));
    }

    @Test
    void equals__hashCodeConsistentForEqualInstances() {
        // ARRANGE //
        ParameterDescriptor first = ParameterDescriptor.builder("length", ParameterKind.INTEGER).minimumValue(0)
                .maximumValue(10).defaultValue("5").description("desc").build();
        ParameterDescriptor second = ParameterDescriptor.builder("length", ParameterKind.INTEGER).minimumValue(0)
                .maximumValue(10).defaultValue("5").description("desc").build();

        // ACT & ASSERT //
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @SuppressWarnings({"AssertBetweenInconvertibleTypes", "MisorderedAssertEqualsArguments"})
    @Test
    void equals__rejectsForeignType() {
        // ARRANGE //
        ParameterDescriptor descriptor = ParameterDescriptor.builder("name", ParameterKind.STRING).build();

        // ACT & ASSERT //
        assertNotEquals(descriptor, "name");
    }

    @Test
    void equals__rejectsNull() {
        // ARRANGE //
        ParameterDescriptor descriptor = ParameterDescriptor.builder("name", ParameterKind.STRING).build();

        // ACT & ASSERT //
        assertNotEquals(null, descriptor);
    }

    @Test
    void toString__rendersAllFields() {
        // ARRANGE //
        ParameterDescriptor descriptor = ParameterDescriptor.builder("length", ParameterKind.INTEGER).minimumValue(0)
                .maximumValue(10).defaultValue("5").description("token length").build();

        // ACT //
        String actual = descriptor.toString();

        // ASSERT //
        assertEquals(
                "ParameterDescriptor[name=length, kind=INTEGER, allowedValues=[], defaultValue=5,"
                        + " description=token length, maximumValue=10, minimumValue=0, required=false]",
                actual
        );
    }
}
