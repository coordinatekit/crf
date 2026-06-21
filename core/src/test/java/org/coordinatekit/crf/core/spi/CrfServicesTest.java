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
package org.coordinatekit.crf.core.spi;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.coordinatekit.crf.core.preprocessing.FeatureExtractor;
import org.coordinatekit.crf.core.preprocessing.WhitespaceTokenizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Tests {@link CrfServices}: the canonical defaults (the {@link WhitespaceTokenizer} tokenizer
 * default and the absent feature extractor, tagger loader, and tag provider) and that an explicit
 * override wins. The discovery path is covered by the command tests.
 */
class CrfServicesTest {
    record EmptyParameters(String name, Supplier<Optional<?>> accessor) {}

    @Test
    void featureExtractor__explicitWins() {
        // ARRANGE //
        FeatureExtractor<String> featureExtractor = (sequence, position) -> Set.of();

        // ACT //
        Optional<FeatureExtractor<String>> result = CrfServices.featureExtractor(featureExtractor);

        // ASSERT //
        assertSame(featureExtractor, result.orElseThrow());
    }

    static Stream<EmptyParameters> isEmptyWhenNothingRegistered() {
        return Stream.of(
                new EmptyParameters("featureExtractor", CrfServices::featureExtractor),
                new EmptyParameters("tagProvider", CrfServices::tagProvider),
                new EmptyParameters("taggerLoader", CrfServices::taggerLoader)
        );
    }

    @MethodSource
    @ParameterizedTest
    void isEmptyWhenNothingRegistered(EmptyParameters parameters) {
        // ACT & ASSERT //
        assertTrue(
                parameters.accessor().get().isEmpty(),
                parameters.name() + " should be empty when nothing is registered"
        );
    }

    @Test
    void tokenizer__defaultsToWhitespace() {
        // ACT & ASSERT //
        assertInstanceOf(WhitespaceTokenizer.class, CrfServices.tokenizer(null));
    }
}
