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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.coordinatekit.crf.core.feature.FeatureExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Tests {@link AssembledFeatureExtractors}: the factory carries both accessors and requires the
 * full feature extractor.
 */
class AssembledFeatureExtractorsTest {
    private static final FeatureExtractor FULL = (sequence, position) -> Set.of();
    private static final FeatureExtractor KEY = (sequence, position) -> Set.of();

    record OfEmptyKeyParameters(String name, Supplier<AssembledFeatureExtractors> factory) {}

    static Stream<OfEmptyKeyParameters> of__emptyKey() {
        return Stream.of(
                new OfEmptyKeyParameters("no_key_argument", () -> AssembledFeatureExtractors.of(FULL)),
                new OfEmptyKeyParameters("null_key_argument", () -> AssembledFeatureExtractors.of(FULL, null))
        );
    }

    @MethodSource
    @ParameterizedTest
    void of__emptyKey(OfEmptyKeyParameters parameters) {
        // ACT //
        AssembledFeatureExtractors assembled = parameters.factory().get();

        // ASSERT //
        assertSame(FULL, assembled.fullFeatureExtractor());
        assertTrue(assembled.keyFeatureExtractor().isEmpty());
    }

    record OfExceptionParameters(String name, Executable action, String expectedMessage) {}

    @SuppressWarnings({"DataFlowIssue", "NullAway"})
    static Stream<OfExceptionParameters> of__exception() {
        return Stream.of(
                new OfExceptionParameters(
                        "null_full_feature_extractor_one_argument",
                        () -> AssembledFeatureExtractors.of(null),
                        "fullFeatureExtractor must not be null"
                ),
                new OfExceptionParameters(
                        "null_full_feature_extractor_two_argument",
                        () -> AssembledFeatureExtractors.of(null, KEY),
                        "fullFeatureExtractor must not be null"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void of__exception(OfExceptionParameters parameters) {
        // ACT //
        Exception exception = assertThrows(NullPointerException.class, parameters.action());

        // ASSERT //
        assertEquals(parameters.expectedMessage(), exception.getMessage());
    }

    @Test
    void of__exposesBothExtractors() {
        // ACT //
        AssembledFeatureExtractors assembled = AssembledFeatureExtractors.of(FULL, KEY);

        // ASSERT //
        assertSame(FULL, assembled.fullFeatureExtractor());
        assertSame(KEY, assembled.keyFeatureExtractor().orElseThrow());
    }
}
