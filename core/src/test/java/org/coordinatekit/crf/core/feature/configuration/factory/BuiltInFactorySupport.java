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

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.coordinatekit.crf.core.InputSequence;
import org.coordinatekit.crf.core.PositionedToken;
import org.coordinatekit.crf.core.Sequence;
import org.coordinatekit.crf.core.feature.configuration.ConfigurationTestSupport;
import org.coordinatekit.crf.core.feature.configuration.FeatureExtractorNode;
import org.coordinatekit.crf.core.feature.configuration.FeatureConfigurationException;
import org.coordinatekit.crf.core.feature.configuration.FeatureExtractorAssembler;
import org.coordinatekit.crf.core.feature.configuration.FeatureExtractorFactoryRegistry;
import org.coordinatekit.crf.core.feature.DefaultFeatureFormat;
import org.coordinatekit.crf.core.feature.FeatureExtractor;
import org.coordinatekit.crf.core.feature.FeatureFormat;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared helpers for the built-in factory tests: assemble a node tree through the discovered
 * registry and render the extracted features of one position with {@link DefaultFeatureFormat}.
 */
final class BuiltInFactorySupport {
    private static final FeatureExtractorAssembler ASSEMBLER = new FeatureExtractorAssembler(
            FeatureExtractorFactoryRegistry.load()
    );
    private static final FeatureFormat FORMAT = new DefaultFeatureFormat();

    private BuiltInFactorySupport() {}

    /**
     * Assembles {@code node} with the given base directory, asserting that assembly fails with a
     * located {@link FeatureConfigurationException}.
     *
     * @param baseDirectory the directory that path parameters resolve against
     * @param node the node tree to assemble
     * @return the thrown exception
     */
    static FeatureConfigurationException assembleThrows(Path baseDirectory, FeatureExtractorNode node) {
        return assertThrows(FeatureConfigurationException.class, () -> ASSEMBLER.assemble(node, baseDirectory));
    }

    /**
     * Assembles {@code node} against the current directory, asserting that assembly fails with a
     * located {@link FeatureConfigurationException}.
     *
     * @param node the node tree to assemble
     * @return the thrown exception
     */
    static FeatureConfigurationException assembleThrows(FeatureExtractorNode node) {
        return assembleThrows(Path.of("."), node);
    }

    /**
     * Returns the directory holding the named classpath resource, for resolving path parameters
     * against.
     *
     * @param absoluteResource the absolute classpath resource name
     * @return the directory containing the resource
     */
    static Path resourceDirectory(String absoluteResource) {
        return ConfigurationTestSupport.resourceDirectory(absoluteResource);
    }

    /**
     * Assembles {@code node} with the given base directory and renders the features of
     * {@code position}.
     *
     * @param baseDirectory the directory that path parameters resolve against
     * @param node the node tree to assemble
     * @param tokens the sequence tokens
     * @param position the position to extract at
     * @return the rendered feature strings
     */
    static Set<String> render(Path baseDirectory, FeatureExtractorNode node, List<String> tokens, int position) {
        FeatureExtractor extractor = ASSEMBLER.assemble(node, baseDirectory);
        Sequence<PositionedToken> sequence = new InputSequence(tokens);
        return extractor.extractAt(sequence, position).stream().map(FORMAT::render).collect(Collectors.toSet());
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
    static Set<String> render(FeatureExtractorNode node, List<String> tokens, int position) {
        return render(Path.of("."), node, tokens, position);
    }
}
