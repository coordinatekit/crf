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

/** Tests the {@code window} factory stamps its child's features with their window offset. */
class WindowFeatureExtractorFactoryTest {
    record NegativeBoundParameters(String name, FeatureExtractorNode node, String expectedMessage) {}

    static Stream<NegativeBoundParameters> create__exception() {
        return Stream.of(
                new NegativeBoundParameters(
                        "negativeBefore",
                        FeatureExtractorNodes.builder("window").parameter("before", "-1")
                                .child(FeatureExtractorNodes.builder("length").build()).build(),
                        "extractor 'window' at /window — parameter 'before' expects an integer >= 0 but got '-1'"
                ),
                new NegativeBoundParameters(
                        "negativeAfter",
                        FeatureExtractorNodes.builder("window").parameter("after", "-1")
                                .child(FeatureExtractorNodes.builder("length").build()).build(),
                        "extractor 'window' at /window — parameter 'after' expects an integer >= 0 but got '-1'"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void create__exception(NegativeBoundParameters parameters) {
        // ACT //
        FeatureConfigurationException exception = assembleThrows(parameters.node());

        // ASSERT //
        assertEquals(parameters.expectedMessage(), exception.getMessage());
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
                        "excludesCurrentTokenWhenConfigured",
                        FeatureExtractorNodes.builder("window").parameter("before", "1").parameter("after", "1")
                                .parameter("includeCurrentToken", "false")
                                .child(FeatureExtractorNodes.builder("length").build()).build(),
                        List.of("a", "bb", "ccc"),
                        1,
                        Set.of("PREV_1__LENGTH=1", "NEXT_1__LENGTH=3")
                ),
                new RenderParameters(
                        "skipsNeighborAfterSequenceEnd",
                        FeatureExtractorNodes.builder("window").parameter("before", "1").parameter("after", "1")
                                .child(FeatureExtractorNodes.builder("length").build()).build(),
                        List.of("a", "bb", "ccc"),
                        2,
                        Set.of("PREV_1__LENGTH=2", "LENGTH=3")
                ),
                new RenderParameters(
                        "skipsNeighborBeforeSequenceStart",
                        FeatureExtractorNodes.builder("window").parameter("before", "1").parameter("after", "1")
                                .child(FeatureExtractorNodes.builder("length").build()).build(),
                        List.of("a", "bb", "ccc"),
                        0,
                        Set.of("LENGTH=1", "NEXT_1__LENGTH=2")
                ),
                new RenderParameters(
                        "stampsNeighborOffsets",
                        FeatureExtractorNodes.builder("window").parameter("before", "1").parameter("after", "1")
                                .child(FeatureExtractorNodes.builder("length").build()).build(),
                        List.of("a", "bb", "ccc"),
                        1,
                        Set.of("PREV_1__LENGTH=1", "LENGTH=2", "NEXT_1__LENGTH=3")
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
    void create__requiresNonEmptyWindow() {
        // ARRANGE //
        FeatureExtractorNode node = FeatureExtractorNodes.builder("window").parameter("before", "0")
                .parameter("after", "0").parameter("includeCurrentToken", "false")
                .child(FeatureExtractorNodes.builder("length").build()).build();

        // ACT //
        FeatureConfigurationException exception = assembleThrows(node);

        // ASSERT //
        assertEquals(
                "extractor 'window' at /window — at least one of a non-zero 'before', a non-zero 'after',"
                        + " or 'includeCurrentToken' must be set",
                exception.getMessage()
        );
    }
}
