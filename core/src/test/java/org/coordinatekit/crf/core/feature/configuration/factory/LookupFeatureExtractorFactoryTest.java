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
import static org.coordinatekit.crf.core.feature.configuration.BuiltInFactorySupport.resourceUrl;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.coordinatekit.crf.core.feature.configuration.FeatureExtractorNode;
import org.coordinatekit.crf.core.feature.configuration.FeatureExtractorNodes;
import org.coordinatekit.crf.core.feature.configuration.FeatureConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Tests the {@code lookup} factory tests tokens against a dictionary loaded from an XML file. */
class LookupFeatureExtractorFactoryTest {
    private static final URL BASE = resourceUrl("/org/coordinatekit/crf/core/feature/configuration/states.xml");

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
        FeatureConfigurationException exception = assembleThrows(BASE, node);

        // ASSERT //
        assertEquals(
                "extractor 'lookup' — parameter 'xpath' is not a valid XPath expression: '/states/state['",
                exception.getMessage()
        );
    }

    @Test
    void create__malformedDictionaryThrowsLocatedException() {
        // ARRANGE //
        FeatureExtractorNode node = FeatureExtractorNodes.builder("lookup").parameter("dictionary", "malformed.txt")
                .parameter("xpath", "/states/state").parameter("name", "STATE").build();

        // ACT //
        FeatureConfigurationException exception = assembleThrows(BASE, node);

        // ASSERT //
        String message = exception.getMessage();
        assertTrue(
                message != null && message.contains("is not well-formed XML"),
                "message should report the malformed dictionary; was: " + message
        );
        assertEquals("lookup", exception.extractorType());
        assertTrue(exception.sourceLocation().isEmpty());
    }

    @Test
    void create__missingDictionaryThrowsLocatedException() throws MalformedURLException, URISyntaxException {
        // ARRANGE //
        FeatureExtractorNode node = FeatureExtractorNodes.builder("lookup").parameter("dictionary", "missing.xml")
                .parameter("xpath", "/states/state").parameter("name", "STATE").build();

        // ACT //
        FeatureConfigurationException exception = assembleThrows(BASE, node);

        // ASSERT //
        assertEquals(
                "extractor 'lookup' — parameter 'dictionary' points at a resource that cannot be opened: "
                        + BASE.toURI().resolve("missing.xml").toURL(),
                exception.getMessage()
        );
    }

    static Stream<CreateRenderParameters> create__render() {
        return Stream.of(
                new CreateRenderParameters(
                        "present_emits_name_value",
                        lookup().parameter("name", "STATE").parameter("value", "US")
                                .parameter("absentName", "NON_STATE").build(),
                        List.of("Ohio"),
                        0,
                        Set.of("STATE=US")
                ),
                new CreateRenderParameters(
                        "present_emits_bare_name_without_value",
                        lookup().parameter("name", "STATE").build(),
                        List.of("Ohio"),
                        0,
                        Set.of("STATE")
                ),
                new CreateRenderParameters(
                        "absent_emits_absent_name",
                        lookup().parameter("name", "STATE").parameter("value", "US")
                                .parameter("absentName", "NON_STATE").build(),
                        List.of("Paris"),
                        0,
                        Set.of("NON_STATE")
                ),
                new CreateRenderParameters(
                        "absent_emits_nothing_without_absent_name",
                        lookup().parameter("name", "STATE").parameter("value", "US").build(),
                        List.of("Paris"),
                        0,
                        Set.of()
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void create__render(CreateRenderParameters parameters) {
        // ACT //
        Set<String> actual = render(BASE, parameters.node(), parameters.tokens(), parameters.position());

        // ASSERT //
        assertEquals(parameters.expected(), actual);
    }
}
