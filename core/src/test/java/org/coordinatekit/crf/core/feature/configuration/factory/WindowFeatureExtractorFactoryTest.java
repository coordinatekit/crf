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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.coordinatekit.crf.core.feature.configuration.FeatureExtractorNode;
import org.coordinatekit.crf.core.feature.configuration.FeatureExtractorNodes;
import org.coordinatekit.crf.core.feature.configuration.FeatureConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Tests the {@code window} factory stamps its child's features with their window offset. */
class WindowFeatureExtractorFactoryTest {
    record CreateExceptionParameters(
            String name,
            Executable action,
            Class<? extends Exception> expectedClass,
            String expectedMessage
    ) {}

    static Stream<CreateExceptionParameters> create__exception() {
        return Stream.of(
                new CreateExceptionParameters(
                        "negative_before",
                        () -> render(
                                FeatureExtractorNodes.builder("window").parameter("before", "-1")
                                        .child(FeatureExtractorNodes.builder("length").build()).build(),
                                List.of("a"),
                                0
                        ),
                        FeatureConfigurationException.class,
                        "extractor 'window' — parameter 'before' expects an integer >= 0 but got '-1'"
                ),
                new CreateExceptionParameters(
                        "negative_after",
                        () -> render(
                                FeatureExtractorNodes.builder("window").parameter("after", "-1")
                                        .child(FeatureExtractorNodes.builder("length").build()).build(),
                                List.of("a"),
                                0
                        ),
                        FeatureConfigurationException.class,
                        "extractor 'window' — parameter 'after' expects an integer >= 0 but got '-1'"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void create__exception(CreateExceptionParameters parameters) {
        // ACT //
        Exception exception = assertThrows(parameters.expectedClass(), parameters.action());

        // ASSERT //
        assertEquals(parameters.expectedMessage(), exception.getMessage());
    }

    static Stream<CreateRenderParameters> create__render() {
        return Stream.of(
                new CreateRenderParameters(
                        "excludes_current_token_when_configured",
                        FeatureExtractorNodes.builder("window").parameter("before", "1").parameter("after", "1")
                                .parameter("includeCurrentToken", "false")
                                .child(FeatureExtractorNodes.builder("length").build()).build(),
                        List.of("a", "bb", "ccc"),
                        1,
                        Set.of("PREV_1__LENGTH=1", "NEXT_1__LENGTH=3")
                ),
                new CreateRenderParameters(
                        "skips_neighbor_after_sequence_end",
                        FeatureExtractorNodes.builder("window").parameter("before", "1").parameter("after", "1")
                                .child(FeatureExtractorNodes.builder("length").build()).build(),
                        List.of("a", "bb", "ccc"),
                        2,
                        Set.of("PREV_1__LENGTH=2", "LENGTH=3")
                ),
                new CreateRenderParameters(
                        "skips_neighbor_before_sequence_start",
                        FeatureExtractorNodes.builder("window").parameter("before", "1").parameter("after", "1")
                                .child(FeatureExtractorNodes.builder("length").build()).build(),
                        List.of("a", "bb", "ccc"),
                        0,
                        Set.of("LENGTH=1", "NEXT_1__LENGTH=2")
                ),
                new CreateRenderParameters(
                        "stamps_neighbor_offsets",
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
    void create__render(CreateRenderParameters parameters) {
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
                "extractor 'window' — at least one of a non-zero 'before', a non-zero 'after',"
                        + " or 'includeCurrentToken' must be set",
                exception.getMessage()
        );
    }
}
