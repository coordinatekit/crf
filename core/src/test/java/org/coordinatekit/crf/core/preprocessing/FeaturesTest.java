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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * Tests {@link Features}: the {@code of} overloads (including the enum convenience forms) and their
 * null-argument guards, the replace semantics of {@link Feature#withOffset(int)}, and the
 * natural-order comparator with its consistency with {@code equals}.
 */
class FeaturesTest {
    private enum SampleName {
        CAP, LOWER
    }

    record NaturalOrderParameters(String name, Feature left, Feature right, int expectedSign) {}

    record OfExceptionParameters(String name, Executable action, String expectedMessage) {}

    record OfParameters(
            String name,
            Feature feature,
            int expectedOffset,
            String expectedName,
            @Nullable String expectedValue
    ) {}

    record WithOffsetParameters(String name, Feature start, int offsetToApply, Feature expected) {}

    static Stream<NaturalOrderParameters> naturalOrder() {
        return Stream.of(
                new NaturalOrderParameters("equal", Features.of("A", "1"), Features.of("A", "1"), 0),
                new NaturalOrderParameters(
                        "offset_first",
                        Features.of("B").withOffset(-1),
                        Features.of("A").withOffset(1),
                        -1
                ),
                new NaturalOrderParameters("name_after_offset", Features.of("A"), Features.of("B"), -1),
                new NaturalOrderParameters("null_value_first", Features.of("A"), Features.of("A", "1"), -1),
                new NaturalOrderParameters("value_after_name", Features.of("A", "1"), Features.of("A", "2"), -1)
        );
    }

    @MethodSource
    @ParameterizedTest
    void naturalOrder(NaturalOrderParameters parameters) {
        // ACT //
        int result = Features.naturalOrder().compare(parameters.left(), parameters.right());

        // ASSERT //
        assertEquals(parameters.expectedSign(), Integer.signum(result), parameters.name());
        if (parameters.expectedSign() != 0) {
            assertEquals(
                    -parameters.expectedSign(),
                    Integer.signum(Features.naturalOrder().compare(parameters.right(), parameters.left())),
                    parameters.name() + " should be antisymmetric"
            );
        }
    }

    @Test
    void naturalOrder__consistentWithEquals() {
        // ARRANGE //
        Feature first = Features.of("A", "1");
        Feature second = Features.of("A", "1");

        // ACT & ASSERT //
        assertEquals(0, Features.naturalOrder().compare(first, second));
        assertEquals(first, second);
        assertNotEquals(0, Features.naturalOrder().compare(first, Features.of("A", "2")));
        assertNotEquals(first, Features.of("A", "2"));
    }

    static Stream<OfParameters> of() {
        return Stream.of(
                new OfParameters("name_only", Features.of("CAP"), 0, "CAP", null),
                new OfParameters("name_value", Features.of("LENGTH", "3"), 0, "LENGTH", "3"),
                new OfParameters("name_empty_value", Features.of("X", ""), 0, "X", ""),
                new OfParameters("enum_name", Features.of(SampleName.CAP), 0, "CAP", null),
                new OfParameters("enum_name_value", Features.of(SampleName.CAP, SampleName.LOWER), 0, "CAP", "LOWER")
        );
    }

    @MethodSource
    @ParameterizedTest
    void of(OfParameters parameters) {
        // ASSERT //
        assertEquals(parameters.expectedOffset(), parameters.feature().offset(), parameters.name());
        assertEquals(parameters.expectedName(), parameters.feature().name(), parameters.name());
        assertEquals(parameters.expectedValue(), parameters.feature().value(), parameters.name());
    }

    @SuppressWarnings({"DataFlowIssue", "NullAway"})
    static Stream<OfExceptionParameters> of__exception() {
        return Stream.of(
                new OfExceptionParameters("name_only", () -> Features.of((String) null), "name must not be null"),
                new OfExceptionParameters("name_value", () -> Features.of(null, "1"), "name must not be null"),
                new OfExceptionParameters("enum_name", () -> Features.of((Enum<?>) null), "name must not be null"),
                new OfExceptionParameters(
                        "enum_value",
                        () -> Features.of(SampleName.CAP, null),
                        "value must not be null"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void of__exception(OfExceptionParameters parameters) {
        // ACT //
        NullPointerException exception = assertThrows(NullPointerException.class, parameters.action());

        // ASSERT //
        assertEquals(parameters.expectedMessage(), exception.getMessage(), parameters.name());
    }

    static Stream<WithOffsetParameters> withOffset() {
        return Stream.of(
                new WithOffsetParameters("null_value_remains", Features.of("CAP"), 2, Features.of("CAP").withOffset(2)),
                new WithOffsetParameters(
                        "replaces_rather_than_accumulates",
                        Features.of("TOKEN", "517").withOffset(-1),
                        3,
                        Features.of("TOKEN", "517").withOffset(3)
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void withOffset(WithOffsetParameters parameters) {
        // ACT //
        Feature actual = parameters.start().withOffset(parameters.offsetToApply());

        // ASSERT //
        assertEquals(parameters.expected(), actual, parameters.name());
    }
}
