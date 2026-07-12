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

import static org.coordinatekit.crf.core.feature.configuration.BuiltInFactorySupport.assembleThrows;
import static org.coordinatekit.crf.core.feature.configuration.BuiltInFactorySupport.render;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.coordinatekit.crf.core.feature.configuration.FeatureExtractorNodes;
import org.coordinatekit.crf.core.feature.configuration.FeatureConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Tests the {@code composite} factory unions the features of all its children. */
class CompositeFeatureExtractorFactoryTest {
    static Stream<CreateRenderParameters> create__render() {
        return Stream.of(
                new CreateRenderParameters(
                        "single_child_passes_through",
                        FeatureExtractorNodes.builder("composite")
                                .child(FeatureExtractorNodes.builder("length").build()).build(),
                        List.of("cats"),
                        0,
                        Set.of("LENGTH=4")
                ),
                new CreateRenderParameters(
                        "unions_child_features",
                        FeatureExtractorNodes.builder("composite")
                                .child(FeatureExtractorNodes.builder("length").build())
                                .child(FeatureExtractorNodes.builder("suffix").parameter("length", "2").build())
                                .child(FeatureExtractorNodes.builder("prefix").parameter("length", "2").build())
                                .build(),
                        List.of("cats"),
                        0,
                        Set.of("LENGTH=4", "SUFFIX=ts", "PREFIX=ca")
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void create__render(CreateRenderParameters parameters) {
        // ACT //
        Set<String> actual = render(parameters.node(), parameters.tokens(), parameters.position());

        // ASSERT //
        assertEquals(parameters.expected(), actual);
    }

    @Test
    void create__requiresAtLeastOneChild() {
        // ACT //
        FeatureConfigurationException exception = assembleThrows(FeatureExtractorNodes.builder("composite").build());

        // ASSERT //
        assertEquals("extractor 'composite' — expected at least 1 child but got 0", exception.getMessage());
    }
}
