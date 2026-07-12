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

import org.coordinatekit.crf.core.feature.configuration.FeatureExtractorNode;
import org.coordinatekit.crf.core.feature.configuration.FeatureExtractorNodes;
import org.coordinatekit.crf.core.feature.configuration.FeatureConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Tests the {@code pattern} factory tests tokens against a regular expression. */
class PatternFeatureExtractorFactoryTest {
    @Test
    void create__invalidRegexThrowsLocatedException() {
        // ARRANGE //
        FeatureExtractorNode node = FeatureExtractorNodes.builder("pattern").parameter("regex", "[0-9")
                .parameter("name", "IS_NUMBER").build();

        // ACT //
        FeatureConfigurationException exception = assembleThrows(node);

        // ASSERT //
        assertEquals(
                "extractor 'pattern' — parameter 'regex' is not a valid regular expression: '[0-9'",
                exception.getMessage()
        );
    }

    static Stream<CreateRenderParameters> create__render() {
        return Stream.of(
                new CreateRenderParameters(
                        "match_emits_name",
                        FeatureExtractorNodes.builder("pattern").parameter("regex", "[0-9]+")
                                .parameter("name", "IS_NUMBER").parameter("notMatchedName", "NOT_NUMBER").build(),
                        List.of("42"),
                        0,
                        Set.of("IS_NUMBER")
                ),
                new CreateRenderParameters(
                        "match_emits_name_value",
                        FeatureExtractorNodes.builder("pattern").parameter("regex", "[A-Z]+").parameter("name", "SHAPE")
                                .parameter("value", "CAPS").build(),
                        List.of("ABC"),
                        0,
                        Set.of("SHAPE=CAPS")
                ),
                new CreateRenderParameters(
                        "no_match_emits_not_matched_name",
                        FeatureExtractorNodes.builder("pattern").parameter("regex", "[0-9]+")
                                .parameter("name", "IS_NUMBER").parameter("notMatchedName", "NOT_NUMBER").build(),
                        List.of("word"),
                        0,
                        Set.of("NOT_NUMBER")
                ),
                new CreateRenderParameters(
                        "respects_case_insensitivity",
                        FeatureExtractorNodes.builder("pattern").parameter("regex", "[a-z]+").parameter("name", "ALPHA")
                                .parameter("caseSensitive", "false").build(),
                        List.of("ABC"),
                        0,
                        Set.of("ALPHA")
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
}
