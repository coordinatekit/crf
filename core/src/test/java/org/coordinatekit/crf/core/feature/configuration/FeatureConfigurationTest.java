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
package org.coordinatekit.crf.core.feature.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.coordinatekit.crf.core.feature.FeatureExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Tests {@link FeatureConfiguration}: it resolves the registered parser through
 * {@link org.coordinatekit.crf.core.spi.CrfServices}, resolves a file's or URL's {@code RESOURCE}
 * parameters against the document's own location (or an explicit override), and a content mistake
 * in the loaded tree surfaces the located {@link FeatureConfigurationException} the source location
 * flowed into during assembly.
 */
class FeatureConfigurationTest {
    private static Path facadeDirectory() {
        return ConfigurationTestSupport
                .resourceDirectory("/org/coordinatekit/crf/core/feature/configuration/facade/length.xml");
    }

    private static Path keyedDirectory() {
        return ConfigurationTestSupport
                .resourceDirectory("/org/coordinatekit/crf/core/feature/configuration/keyed/features.xml");
    }

    private static Path locatedErrorDirectory() {
        return ConfigurationTestSupport
                .resourceDirectory("/org/coordinatekit/crf/core/feature/configuration/located-error/features.xml");
    }

    @Test
    void load__explicitBaseLocationOverridesDocumentLocation(@TempDir Path directory) throws IOException {
        // ARRANGE //
        Path file = directory.resolve("features.xml");
        Files.writeString(
                file,
                "<featureExtractors xmlns=\"https://coordinatekit.org/schema/crf/feature-configuration\">"
                        + "<extractor type=\"lookup\">" + "<parameter name=\"name\" value=\"STATE\"/>"
                        + "<parameter name=\"value\" value=\"US\"/>"
                        + "<parameter name=\"dictionary\" value=\"states.xml\"/>"
                        + "<parameter name=\"xpath\" value=\"/states/state\"/>" + "</extractor></featureExtractors>"
        );
        URL url = file.toUri().toURL();
        URL baseLocation = ConfigurationTestSupport
                .resourceUrl("/org/coordinatekit/crf/core/feature/configuration/features.xml");
        FeatureExtractor extractor = FeatureConfiguration.load(url, baseLocation).fullFeatureExtractor();

        // ACT //
        Set<String> rendered = ConfigurationTestSupport.renderFeatures(extractor, List.of("Ohio"), 0);

        // ASSERT //
        assertEquals(Set.of("STATE=US"), rendered);
    }

    @Test
    void load__fileAssembles() {
        // ARRANGE //
        FeatureExtractor extractor = FeatureConfiguration.load(facadeDirectory().resolve("length.xml"))
                .fullFeatureExtractor();

        // ACT //
        Set<String> rendered = ConfigurationTestSupport.renderFeatures(extractor, List.of("hi"), 0);

        // ASSERT //
        assertEquals(Set.of("LENGTH=2"), rendered);
    }

    @Test
    void load__keylessFileYieldsEmptyKeyExtractor() {
        // ACT //
        AssembledFeatureExtractors assembled = FeatureConfiguration.load(facadeDirectory().resolve("length.xml"));

        // ASSERT //
        assertTrue(assembled.keyFeatureExtractor().isEmpty());
    }

    @Test
    void load__keyMarkerYieldsDistinctFullAndKeyExtractors() {
        // ARRANGE //
        AssembledFeatureExtractors assembled = FeatureConfiguration.load(keyedDirectory().resolve("features.xml"));

        // ACT //
        Set<String> fullRendered = ConfigurationTestSupport
                .renderFeatures(assembled.fullFeatureExtractor(), List.of("ab", "cde"), 1);
        Set<String> keyRendered = ConfigurationTestSupport
                .renderFeatures(assembled.keyFeatureExtractor().orElseThrow(), List.of("ab", "cde"), 1);

        // ASSERT //
        assertEquals(Set.of("LENGTH=3", "PREFIX2=cd", "PREV_1__LENGTH=2", "PREV_1__PREFIX2=ab"), fullRendered);
        assertEquals(Set.of("LENGTH=3", "PREFIX2=cd"), keyRendered);
    }

    @Test
    void load__locatedContentErrorFlowsThroughAssembly() {
        // ARRANGE //
        Path file = locatedErrorDirectory().resolve("features.xml");

        // ACT //
        FeatureConfigurationException exception = assertThrows(
                FeatureConfigurationException.class,
                () -> FeatureConfiguration.load(file)
        );

        // ASSERT //
        String renderedLocation = exception.sourceLocation().map(SourceLocation::toString).orElse("");
        assertTrue(
                renderedLocation.matches(".*features\\.xml:\\d+(:\\d+)?")
                        && renderedLocation.contains(File.separator + "features.xml:"),
                "sourceLocation should read the absolute path, e.g. /path/to/features.xml:<line>; was: "
                        + renderedLocation
        );
        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(
                message.contains("unknown parameter 'beofre' (did you mean 'before'?)"),
                "message should report the unknown parameter with a suggestion; was: " + message
        );
    }

    @Test
    void load__urlResolvesSiblingResourcesAgainstDocumentLocation() {
        // ARRANGE //
        URL url = ConfigurationTestSupport
                .resourceUrl("/org/coordinatekit/crf/core/feature/configuration/features.xml");
        FeatureExtractor extractor = FeatureConfiguration.load(url).fullFeatureExtractor();

        // ACT //
        Set<String> rendered = ConfigurationTestSupport.renderFeatures(extractor, List.of("Ohio"), 0);

        // ASSERT //
        assertTrue(
                rendered.contains("STATE=US"),
                "dictionary=\"states.xml\" should resolve as a sibling of the classpath document; was: " + rendered
        );
    }

    @Test
    void load__urlVariantCarriesSourceName(@TempDir Path directory) throws IOException {
        // ARRANGE //
        Path file = directory.resolve("features.xml");
        Files.writeString(
                file,
                "<featureExtractors xmlns=\"https://coordinatekit.org/schema/crf/feature-configuration\"><extractor/></featureExtractors>"
        );
        URL url = file.toUri().toURL();

        // ACT //
        FeatureConfigurationParseException exception = assertThrows(
                FeatureConfigurationParseException.class,
                () -> FeatureConfiguration.load(url)
        );

        // ASSERT //
        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(message.contains("features.xml:"), "message should carry the source name; was: " + message);
    }
}
