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

import static org.coordinatekit.crf.core.preprocessing.Feature.createFeature;
import static org.coordinatekit.crf.core.preprocessing.Feature.createFeatureWithValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests {@link Feature}: {@link Feature#compareTo(Feature) natural ordering}, the replace semantics
 * of {@link Feature#withOffset(int)}, {@link Feature#toString()}, the {@code
 * equals}/{@code hashCode} contract with each property varied in isolation, and the
 * {@link Feature#createFeature(String)
 * createFeature}/{@link Feature#createFeatureWithValue(String, String) createFeatureWithValue}
 * factory overloads (including the enum convenience forms) with their null-argument guards.
 */
class FeatureTest {
    private enum SampleName {
        CAP, LOWER
    }

    private static final Feature FEATURE = createFeatureWithValue("TOKEN", "cat").withOffset(1);
    private static final Feature FEATURE_SAME = createFeatureWithValue("TOKEN", "cat").withOffset(1);
    private static final Feature FEATURE_DIFFERENT_OFFSET = createFeatureWithValue("TOKEN", "cat").withOffset(2);
    private static final Feature FEATURE_DIFFERENT_NAME = createFeatureWithValue("SHAPE", "cat").withOffset(1);
    private static final Feature FEATURE_DIFFERENT_VALUE = createFeatureWithValue("TOKEN", "dog").withOffset(1);
    private static final Feature FEATURE_NULL_VALUE = createFeature("TOKEN").withOffset(1);

    record CompareToParameters(String name, Feature left, Feature right, int expectedSign) {}

    static Stream<CompareToParameters> compareTo() {
        return Stream.of(
                new CompareToParameters("equal", FEATURE, FEATURE_SAME, 0),
                new CompareToParameters("offset_dominates", FEATURE, FEATURE_DIFFERENT_OFFSET, -1),
                new CompareToParameters("name_after_offset", FEATURE, FEATURE_DIFFERENT_NAME, 1),
                new CompareToParameters("null_value_first", FEATURE, FEATURE_NULL_VALUE, 1),
                new CompareToParameters("value_after_name", FEATURE, FEATURE_DIFFERENT_VALUE, -1)
        );
    }

    @MethodSource
    @ParameterizedTest
    void compareTo(CompareToParameters parameters) {
        // ASSERT //
        assertEquals(
                parameters.expectedSign(),
                Integer.signum(parameters.left().compareTo(parameters.right())),
                parameters.name()
        );
        if (parameters.expectedSign() != 0) {
            assertEquals(
                    -parameters.expectedSign(),
                    Integer.signum(parameters.right().compareTo(parameters.left())),
                    parameters.name() + " should be antisymmetric"
            );
        }
    }

    @Test
    void compareTo__consistentWithEquals() {
        // ACT & ASSERT //
        assertEquals(0, FEATURE.compareTo(FEATURE_SAME));
        assertEquals(FEATURE, FEATURE_SAME);
        assertNotEquals(0, FEATURE.compareTo(FEATURE_DIFFERENT_VALUE));
        assertNotEquals(FEATURE, FEATURE_DIFFERENT_VALUE);
    }

    record EqualsAndHashCodeParameters(String name, Feature other, boolean expectedEqual) {}

    static Stream<EqualsAndHashCodeParameters> equalsAndHashCode() {
        return Stream.of(
                new EqualsAndHashCodeParameters("same", FEATURE_SAME, true),
                new EqualsAndHashCodeParameters("different_offset", FEATURE_DIFFERENT_OFFSET, false),
                new EqualsAndHashCodeParameters("different_name", FEATURE_DIFFERENT_NAME, false),
                new EqualsAndHashCodeParameters("different_value", FEATURE_DIFFERENT_VALUE, false),
                new EqualsAndHashCodeParameters("null_value", FEATURE_NULL_VALUE, false)
        );
    }

    @MethodSource
    @ParameterizedTest
    void equalsAndHashCode(EqualsAndHashCodeParameters parameters) {
        // ASSERT //
        assertEquals(parameters.expectedEqual(), FEATURE.equals(parameters.other()), parameters.name());
        if (parameters.expectedEqual()) {
            assertEquals(FEATURE.hashCode(), parameters.other().hashCode(), parameters.name());
        } else {
            assertNotEquals(FEATURE.hashCode(), parameters.other().hashCode(), parameters.name());
        }
    }

    @Test
    void equalsHandlesNonFeatures() {
        // noinspection EqualsWithItself
        assertEquals(FEATURE, FEATURE);
        assertNotSame(FEATURE, FEATURE_SAME);
        assertNotEquals(FEATURE, null);
        assertNotEquals(FEATURE, "TOKEN=cat");
    }

    record FactoryParameters(
            String name,
            Feature feature,
            int expectedOffset,
            String expectedName,
            @Nullable String expectedValue
    ) {}

    static Stream<FactoryParameters> factory() {
        return Stream.of(
                new FactoryParameters("name_only", createFeature("CAP"), 0, "CAP", null),
                new FactoryParameters("name_value", createFeatureWithValue("LENGTH", "3"), 0, "LENGTH", "3"),
                new FactoryParameters("name_empty_value", createFeatureWithValue("X", ""), 0, "X", ""),
                new FactoryParameters("enum_name", createFeature(SampleName.CAP), 0, "CAP", null),
                new FactoryParameters(
                        "enum_name_value",
                        createFeatureWithValue(SampleName.CAP, SampleName.LOWER),
                        0,
                        "CAP",
                        "LOWER"
                ),
                new FactoryParameters(
                        "name_enum_value",
                        createFeatureWithValue("LENGTH", SampleName.CAP),
                        0,
                        "LENGTH",
                        "CAP"
                ),
                new FactoryParameters(
                        "enum_name_string_value",
                        createFeatureWithValue(SampleName.CAP, "3"),
                        0,
                        "CAP",
                        "3"
                ),
                new FactoryParameters(
                        "enum_name_null_string_value",
                        createFeatureWithValue(SampleName.CAP, (String) null),
                        0,
                        "CAP",
                        null
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void factory(FactoryParameters parameters) {
        // ASSERT //
        assertEquals(parameters.expectedOffset(), parameters.feature().offset(), parameters.name());
        assertEquals(parameters.expectedName(), parameters.feature().name(), parameters.name());
        assertEquals(parameters.expectedValue(), parameters.feature().value(), parameters.name());
    }

    @Test
    void factory__enumResolvesViaName() {
        // ASSERT //
        assertEquals(createFeature("CAP"), createFeature(SampleName.CAP));
        assertEquals(createFeatureWithValue("CAP", "LOWER"), createFeatureWithValue(SampleName.CAP, SampleName.LOWER));
    }

    record FactoryExceptionParameters(String name, Executable action, String expectedMessage) {}

    @SuppressWarnings({"DataFlowIssue", "NullAway"})
    static Stream<FactoryExceptionParameters> factory__exception() {
        return Stream.of(
                new FactoryExceptionParameters(
                        "name_only",
                        () -> createFeature((String) null),
                        "name must not be null"
                ),
                new FactoryExceptionParameters(
                        "name_value",
                        () -> createFeatureWithValue((String) null, "1"),
                        "name must not be null"
                ),
                new FactoryExceptionParameters(
                        "enum_name",
                        () -> createFeature((Enum<?>) null),
                        "name must not be null"
                ),
                new FactoryExceptionParameters(
                        "enum_value",
                        () -> createFeatureWithValue(SampleName.CAP, (Enum<?>) null),
                        "value must not be null"
                ),
                new FactoryExceptionParameters(
                        "enum_name_enum_value_null_name",
                        () -> createFeatureWithValue((Enum<?>) null, SampleName.CAP),
                        "name must not be null"
                ),
                new FactoryExceptionParameters(
                        "name_enum_value_null_name",
                        () -> createFeatureWithValue((String) null, SampleName.CAP),
                        "name must not be null"
                ),
                new FactoryExceptionParameters(
                        "name_enum_value_null_value",
                        () -> createFeatureWithValue("LENGTH", (Enum<?>) null),
                        "value must not be null"
                ),
                new FactoryExceptionParameters(
                        "enum_name_string_value_null_name",
                        () -> createFeatureWithValue((Enum<?>) null, "3"),
                        "name must not be null"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void factory__exception(FactoryExceptionParameters parameters) {
        // ACT //
        NullPointerException exception = assertThrows(NullPointerException.class, parameters.action());

        // ASSERT //
        assertEquals(parameters.expectedMessage(), exception.getMessage(), parameters.name());
    }

    @Test
    void testToString() {
        assertEquals("Feature[offset=1, name=TOKEN, value=cat]", FEATURE.toString());
        assertEquals("Feature[offset=1, name=TOKEN, value=null]", FEATURE_NULL_VALUE.toString());
    }

    record WithOffsetParameters(String name, Feature start, int offsetToApply, Feature expected) {}

    static Stream<WithOffsetParameters> withOffset() {
        return Stream.of(
                new WithOffsetParameters(
                        "null_value_remains",
                        createFeature("CAP"),
                        2,
                        createFeature("CAP").withOffset(2)
                ),
                new WithOffsetParameters(
                        "replaces_from_negative_offset",
                        createFeatureWithValue("TOKEN", "517").withOffset(-1),
                        3,
                        createFeatureWithValue("TOKEN", "517").withOffset(3)
                ),
                new WithOffsetParameters(
                        "replaces_from_positive_offset",
                        FEATURE,
                        5,
                        createFeatureWithValue("TOKEN", "cat").withOffset(5)
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void withOffset(WithOffsetParameters parameters) {
        // ACT //
        Feature shifted = parameters.start().withOffset(parameters.offsetToApply());

        // ASSERT //
        assertEquals(parameters.expected(), shifted, parameters.name());
    }
}
