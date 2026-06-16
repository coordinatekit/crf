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
package org.coordinatekit.crf.annotator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureAvailabilityTest {
    record OfParameters(String name, boolean keyAvailable, boolean verboseAvailable, FeatureAvailability expected) {}

    @Test
    void accessors() {
        // ASSERT //
        assertTrue(FeatureAvailability.BOTH.keyAvailable());
        assertTrue(FeatureAvailability.BOTH.verboseAvailable());
        assertTrue(FeatureAvailability.KEY_ONLY.keyAvailable());
        assertFalse(FeatureAvailability.KEY_ONLY.verboseAvailable());
        assertFalse(FeatureAvailability.VERBOSE_ONLY.keyAvailable());
        assertTrue(FeatureAvailability.VERBOSE_ONLY.verboseAvailable());
        assertFalse(FeatureAvailability.NONE.keyAvailable());
        assertFalse(FeatureAvailability.NONE.verboseAvailable());
    }

    static Stream<OfParameters> of() {
        return Stream.of(
                new OfParameters("none", false, false, FeatureAvailability.NONE),
                new OfParameters("key_only", true, false, FeatureAvailability.KEY_ONLY),
                new OfParameters("verbose_only", false, true, FeatureAvailability.VERBOSE_ONLY),
                new OfParameters("both", true, true, FeatureAvailability.BOTH)
        );
    }

    @MethodSource
    @ParameterizedTest
    void of(OfParameters parameters) {
        // ACT //
        FeatureAvailability availability = FeatureAvailability
                .of(parameters.keyAvailable(), parameters.verboseAvailable());

        // ASSERT //
        assertEquals(parameters.expected(), availability);
    }
}
