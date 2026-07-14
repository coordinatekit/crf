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

import static org.coordinatekit.crf.core.feature.configuration.ConfigurationTestSupport.currentDirectoryUrl;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.coordinatekit.crf.core.feature.DefaultFeatureFormat;
import org.coordinatekit.crf.core.feature.FeatureExtractor;

import java.net.URL;
import java.util.List;
import java.util.Set;

/**
 * Shared helpers for the built-in factory tests: assemble a node tree through the discovered
 * registry and render the extracted features of one position with {@link DefaultFeatureFormat}.
 */
public final class BuiltInFactorySupport {
    private static final FeatureExtractorAssembler ASSEMBLER = new FeatureExtractorAssembler(
            FeatureExtractorFactoryRegistry.load()
    );

    private BuiltInFactorySupport() {}

    /**
     * Assembles {@code node} with the given base location, asserting that assembly fails with a located
     * {@link FeatureConfigurationException}.
     *
     * @param baseLocation the document location that resource parameters resolve against
     * @param node the node tree to assemble
     * @return the thrown exception
     */
    public static FeatureConfigurationException assembleThrows(URL baseLocation, FeatureExtractorNode node) {
        return assertThrows(FeatureConfigurationException.class, () -> ASSEMBLER.assemble(node, baseLocation));
    }

    /**
     * Assembles {@code node} against the current directory, asserting that assembly fails with a
     * located {@link FeatureConfigurationException}.
     *
     * @param node the node tree to assemble
     * @return the thrown exception
     */
    public static FeatureConfigurationException assembleThrows(FeatureExtractorNode node) {
        return assembleThrows(currentDirectoryUrl(), node);
    }

    /**
     * Returns the URL of the named classpath resource.
     *
     * @param absoluteResource the absolute classpath resource name
     * @return the resource URL
     */
    public static URL resourceUrl(String absoluteResource) {
        return ConfigurationTestSupport.resourceUrl(absoluteResource);
    }

    /**
     * Assembles {@code node} with the given base location and renders the features of {@code position}.
     *
     * @param baseLocation the document location that resource parameters resolve against
     * @param node the node tree to assemble
     * @param tokens the sequence tokens
     * @param position the position to extract at
     * @return the rendered feature strings
     */
    public static Set<String> render(URL baseLocation, FeatureExtractorNode node, List<String> tokens, int position) {
        FeatureExtractor extractor = ASSEMBLER.assemble(node, baseLocation).fullFeatureExtractor();
        return ConfigurationTestSupport.renderFeatures(extractor, tokens, position);
    }

    /**
     * Assembles {@code node} against the current directory and renders the features of
     * {@code position}.
     *
     * @param node the node tree to assemble
     * @param tokens the sequence tokens
     * @param position the position to extract at
     * @return the rendered feature strings
     */
    public static Set<String> render(FeatureExtractorNode node, List<String> tokens, int position) {
        return render(currentDirectoryUrl(), node, tokens, position);
    }
}
