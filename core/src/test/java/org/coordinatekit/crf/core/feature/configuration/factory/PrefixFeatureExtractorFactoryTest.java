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
 * Tests the {@code prefix} factory renders {@code NAME=<prefix>} for the leading characters.
 *
 * <p>
 * This is an intentional behavioral mirror of {@link SuffixFeatureExtractorFactoryTest}; keep the
 * two symmetric.
 */
class PrefixFeatureExtractorFactoryTest {
    @Test
    void create__lengthBelowMinimumThrowsLocatedException() {
        // ARRANGE //
        FeatureExtractorNode node = FeatureExtractorNodes.builder("prefix").parameter("length", "0").build();

        // ACT //
        FeatureConfigurationException exception = assembleThrows(node);

        // ASSERT //
        assertEquals(
                "extractor 'prefix' at /prefix — parameter 'length' expects an integer >= 1 but got '0'",
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
                        "customName",
                        FeatureExtractorNodes.builder("prefix").parameter("name", "PREFIX2").parameter("length", "2")
                                .build(),
                        List.of("517"),
                        0,
                        Set.of("PREFIX2=51")
                ),
                new RenderParameters(
                        "defaultNameTakesLeadingCharacters",
                        FeatureExtractorNodes.builder("prefix").parameter("length", "3").build(),
                        List.of("abcdef"),
                        0,
                        Set.of("PREFIX=abc")
                ),
                new RenderParameters(
                        "emitsShortTokensByDefault",
                        FeatureExtractorNodes.builder("prefix").parameter("length", "3").build(),
                        List.of("ab"),
                        0,
                        Set.of("PREFIX=ab")
                ),
                new RenderParameters(
                        "excludesShortTokensWhenConfigured",
                        FeatureExtractorNodes.builder("prefix").parameter("length", "4")
                                .parameter("includeIfLessThanLength", "false").build(),
                        List.of("ab"),
                        0,
                        Set.of()
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
