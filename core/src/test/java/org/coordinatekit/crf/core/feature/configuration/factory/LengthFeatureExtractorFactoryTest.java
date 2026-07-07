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

import static org.coordinatekit.crf.core.feature.configuration.factory.BuiltInFactorySupport.render;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.coordinatekit.crf.core.feature.configuration.FeatureExtractorNode;
import org.coordinatekit.crf.core.feature.configuration.FeatureExtractorNodes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Tests the {@code length} factory renders {@code NAME=<characterLength>} for a token. */
class LengthFeatureExtractorFactoryTest {
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
                        "default_length",
                        FeatureExtractorNodes.builder("length").build(),
                        List.of("abc"),
                        0,
                        Set.of("LENGTH=3")
                ),
                new RenderParameters(
                        "custom_name",
                        FeatureExtractorNodes.builder("length").parameter("name", "CHARS").build(),
                        List.of("hello"),
                        0,
                        Set.of("CHARS=5")
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
}
