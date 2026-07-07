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

/** Tests the {@code position} factory emits features derived from a token's position. */
class PositionFeatureExtractorFactoryTest {
    private static FeatureExtractorNode allNames() {
        return FeatureExtractorNodes.builder("position").parameter("firstName", "FIRST").parameter("lastName", "LAST")
                .parameter("fromStartName", "START").parameter("fromEndName", "END").build();
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
                        "emitsOnlyConfiguredNames",
                        FeatureExtractorNodes.builder("position").parameter("firstName", "FIRST")
                                .parameter("fromStartName", "START").build(),
                        List.of("a", "bb", "ccc"),
                        0,
                        Set.of("FIRST", "START=0")
                ),
                new RenderParameters(
                        "firstTokenGetsFirstAndDistances",
                        allNames(),
                        List.of("a", "bb", "ccc"),
                        0,
                        Set.of("FIRST", "START=0", "END=2")
                ),
                new RenderParameters(
                        "lastTokenGetsLastAndDistances",
                        allNames(),
                        List.of("a", "bb", "ccc"),
                        2,
                        Set.of("LAST", "START=2", "END=0")
                ),
                new RenderParameters(
                        "middleTokenGetsOnlyDistances",
                        allNames(),
                        List.of("a", "bb", "ccc"),
                        1,
                        Set.of("START=1", "END=1")
                ),
                new RenderParameters(
                        "singleTokenIsBothFirstAndLast",
                        allNames(),
                        List.of("a"),
                        0,
                        Set.of("FIRST", "LAST", "START=0", "END=0")
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
    void create__requiresAtLeastOneFeatureName() {
        // ARRANGE //
        FeatureExtractorNode node = FeatureExtractorNodes.builder("position").build();

        // ACT //
        FeatureConfigurationException exception = assembleThrows(node);

        // ASSERT //
        assertEquals(
                "extractor 'position' at /position — at least one of 'firstName', 'lastName', 'fromStartName',"
                        + " or 'fromEndName' must be set",
                exception.getMessage()
        );
    }
}
