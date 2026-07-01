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
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests {@link Feature}: its accessors, {@link Feature#compareTo(Feature) natural ordering}, the
 * replace semantics of {@link Feature#withOffset(int)}, {@link Feature#toString()}, and the
 * {@code equals}/{@code hashCode} contract with each property varied in isolation. Instances are
 * built through the {@link Features} factory rather than the package-private constructor.
 */
class FeatureTest {
    private static final Feature FEATURE = Features.of("TOKEN", "cat").withOffset(1);
    private static final Feature FEATURE_SAME = Features.of("TOKEN", "cat").withOffset(1);
    private static final Feature FEATURE_DIFFERENT_OFFSET = Features.of("TOKEN", "cat").withOffset(2);
    private static final Feature FEATURE_DIFFERENT_NAME = Features.of("SHAPE", "cat").withOffset(1);
    private static final Feature FEATURE_DIFFERENT_VALUE = Features.of("TOKEN", "dog").withOffset(1);
    private static final Feature FEATURE_NULL_VALUE = Features.of("TOKEN").withOffset(1);

    record AccessorsParameters(
            String name,
            Feature feature,
            int expectedOffset,
            String expectedName,
            @Nullable String expectedValue
    ) {}

    static Stream<AccessorsParameters> accessors() {
        return Stream.of(
                new AccessorsParameters("with_value", FEATURE, 1, "TOKEN", "cat"),
                new AccessorsParameters("null_value", FEATURE_NULL_VALUE, 1, "TOKEN", null)
        );
    }

    @MethodSource
    @ParameterizedTest
    void accessors(AccessorsParameters parameters) {
        // ASSERT //
        assertEquals(parameters.expectedOffset(), parameters.feature().offset(), parameters.name());
        assertEquals(parameters.expectedName(), parameters.feature().name(), parameters.name());
        assertEquals(parameters.expectedValue(), parameters.feature().value(), parameters.name());
    }

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

    @Test
    void testToString() {
        assertEquals("Feature[offset=1, name=TOKEN, value=cat]", FEATURE.toString());
        assertEquals("Feature[offset=1, name=TOKEN, value=null]", FEATURE_NULL_VALUE.toString());
    }

    @Test
    void withOffset() {
        Feature shifted = FEATURE.withOffset(5);

        // Replaces rather than accumulates: the prior offset of 1 is discarded.
        assertEquals(5, shifted.offset());
        assertEquals("TOKEN", shifted.name());
        assertEquals("cat", shifted.value());
        assertEquals(Features.of("TOKEN", "cat").withOffset(5), shifted);
    }
}
