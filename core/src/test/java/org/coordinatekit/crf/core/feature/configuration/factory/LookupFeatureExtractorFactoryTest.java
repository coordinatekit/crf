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
import static org.coordinatekit.crf.core.feature.configuration.factory.BuiltInFactorySupport.resourceDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.coordinatekit.crf.core.feature.configuration.FeatureExtractorNode;
import org.coordinatekit.crf.core.feature.configuration.FeatureExtractorNodes;
import org.coordinatekit.crf.core.feature.configuration.FeatureConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Tests the {@code lookup} factory tests tokens against a dictionary loaded from an XML file. */
class LookupFeatureExtractorFactoryTest {
    private static final Path BASE_DIRECTORY = resourceDirectory(
            "/org/coordinatekit/crf/core/feature/configuration/states.xml"
    );

    private static FeatureExtractorNodes.Builder lookup() {
        return FeatureExtractorNodes.builder("lookup").parameter("dictionary", "states.xml")
                .parameter("xpath", "/states/state");
    }

    @Test
    void create__invalidXPathThrowsLocatedException() {
        // ARRANGE //
        FeatureExtractorNode node = FeatureExtractorNodes.builder("lookup").parameter("dictionary", "states.xml")
                .parameter("xpath", "/states/state[").parameter("name", "STATE").build();

        // ACT //
        FeatureConfigurationException exception = assembleThrows(BASE_DIRECTORY, node);

        // ASSERT //
        assertEquals(
                "extractor 'lookup' at /lookup — parameter 'xpath' is not a valid XPath expression: '/states/state['",
                exception.getMessage()
        );
    }

    @Test
    void create__malformedDictionaryThrowsLocatedException() {
        // ARRANGE //
        FeatureExtractorNode node = FeatureExtractorNodes.builder("lookup").parameter("dictionary", "malformed.txt")
                .parameter("xpath", "/states/state").parameter("name", "STATE").build();

        // ACT //
        FeatureConfigurationException exception = assembleThrows(BASE_DIRECTORY, node);

        // ASSERT //
        String message = exception.getMessage();
        assertTrue(
                message != null && message.contains("is not well-formed XML"),
                "message should report the malformed dictionary; was: " + message
        );
        assertEquals("lookup", exception.extractorType());
        assertEquals("/lookup", exception.location());
    }

    @Test
    void create__missingDictionaryThrowsLocatedException() {
        // ARRANGE //
        FeatureExtractorNode node = FeatureExtractorNodes.builder("lookup").parameter("dictionary", "missing.xml")
                .parameter("xpath", "/states/state").parameter("name", "STATE").build();

        // ACT //
        FeatureConfigurationException exception = assembleThrows(BASE_DIRECTORY, node);

        // ASSERT //
        assertEquals(
                "extractor 'lookup' at /lookup — parameter 'dictionary' points at a file that does not exist: "
                        + BASE_DIRECTORY.resolve("missing.xml"),
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
                        "presentEmitsNameValue",
                        lookup().parameter("name", "STATE").parameter("value", "US")
                                .parameter("absentName", "NON_STATE").build(),
                        List.of("Ohio"),
                        0,
                        Set.of("STATE=US")
                ),
                new RenderParameters(
                        "presentEmitsBareNameWithoutValue",
                        lookup().parameter("name", "STATE").build(),
                        List.of("Ohio"),
                        0,
                        Set.of("STATE")
                ),
                new RenderParameters(
                        "absentEmitsAbsentName",
                        lookup().parameter("name", "STATE").parameter("value", "US")
                                .parameter("absentName", "NON_STATE").build(),
                        List.of("Paris"),
                        0,
                        Set.of("NON_STATE")
                ),
                new RenderParameters(
                        "absentEmitsNothingWithoutAbsentName",
                        lookup().parameter("name", "STATE").parameter("value", "US").build(),
                        List.of("Paris"),
                        0,
                        Set.of()
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void create__render(RenderParameters parameters) {
        // ACT //
        Set<String> actual = render(BASE_DIRECTORY, parameters.node(), parameters.tokens(), parameters.position());

        // ASSERT //
        assertEquals(parameters.expected(), actual);
    }
}
