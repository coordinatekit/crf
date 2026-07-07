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
package org.coordinatekit.crf.core.feature.configuration.factory;

import static org.coordinatekit.crf.core.feature.configuration.factory.BuiltInFactorySupport.assembleThrows;
import static org.coordinatekit.crf.core.feature.configuration.factory.BuiltInFactorySupport.render;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.coordinatekit.crf.core.feature.configuration.FeatureExtractorNode;
import org.coordinatekit.crf.core.feature.configuration.FeatureExtractorNodes;
import org.coordinatekit.crf.core.feature.configuration.FeatureConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Tests the {@code sequenceLength} factory emits features for the lengths the sequence has and
 * lacks.
 */
class SequenceLengthFeatureExtractorFactoryTest {
    @Test
    void create__limitBelowMinimumThrowsLocatedException() {
        // ARRANGE //
        FeatureExtractorNode node = FeatureExtractorNodes.builder("sequenceLength").parameter("limit", "0")
                .parameter("hasName", "HAS").build();

        // ACT //
        FeatureConfigurationException exception = assembleThrows(node);

        // ASSERT //
        assertEquals(
                "extractor 'sequenceLength' at /sequenceLength — parameter 'limit' expects an integer >= 1 but got"
                        + " '0'",
                exception.getMessage()
        );
    }

    record RenderParameters(
            String name,
            FeatureExtractorNode node,
            List<String> tokens,
            int position,
            Set<String> expected
    ) {}

    static Stream<RenderParameters> create__render() {
        return Stream.of(
                new RenderParameters(
                        "distinguishesHadAndLackedLengths",
                        FeatureExtractorNodes.builder("sequenceLength").parameter("limit", "3")
                                .parameter("hasName", "HAS").parameter("lacksName", "LACKS").build(),
                        List.of("a", "bb"),
                        0,
                        Set.of("HAS=1", "HAS=2", "LACKS=3")
                ),
                new RenderParameters(
                        "emitsOnlyHadLengthsWhenLacksNameUnset",
                        FeatureExtractorNodes.builder("sequenceLength").parameter("limit", "3")
                                .parameter("hasName", "HAS").build(),
                        List.of("a", "bb"),
                        0,
                        Set.of("HAS=1", "HAS=2")
                ),
                new RenderParameters(
                        "emitsOnlyLackedLengthsWhenHasNameUnset",
                        FeatureExtractorNodes.builder("sequenceLength").parameter("limit", "3")
                                .parameter("lacksName", "LACKS").build(),
                        List.of("a", "bb"),
                        0,
                        Set.of("LACKS=3")
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void create__render(RenderParameters parameters) {
        // ACT //
        Set<String> actual = render(parameters.node(), parameters.tokens(), parameters.position());

        // ASSERT //
        assertEquals(parameters.expected(), actual);
    }

    @Test
    void create__requiresAtLeastOneOfHasNameOrLacksName() {
        // ARRANGE //
        FeatureExtractorNode node = FeatureExtractorNodes.builder("sequenceLength").parameter("limit", "3").build();

        // ACT //
        FeatureConfigurationException exception = assembleThrows(node);

        // ASSERT //
        assertEquals(
                "extractor 'sequenceLength' at /sequenceLength — at least one of 'hasName' or 'lacksName' must be"
                        + " set",
                exception.getMessage()
        );
    }
}
