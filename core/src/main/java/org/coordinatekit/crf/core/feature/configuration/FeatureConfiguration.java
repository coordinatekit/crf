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

import org.coordinatekit.crf.core.spi.AmbiguousServiceException;
import org.coordinatekit.crf.core.spi.CrfServices;

import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Objects;

/**
 * The façade that turns a configuration file into an {@link AssembledFeatureExtractors} pair:
 * resolves the {@link FeatureConfigurationParser}, parses the file into a
 * {@link FeatureExtractorNode} tree, and assembles that tree into the full and key extractors.
 *
 * <p>
 * {@link #load(Path)} loads from the filesystem; {@link #load(URL)} loads from the filesystem,
 * classpath, or network, resolving {@code RESOURCE} parameters against the document's own location;
 * {@link #load(URL, URL)} is the escape hatch for a configuration whose resources live at a
 * different base than the document itself.
 */
public final class FeatureConfiguration {
    private FeatureConfiguration() {
        throw new UnsupportedOperationException("FeatureConfiguration is a utility class");
    }

    /**
     * Loads the feature extractor described by {@code file}, resolving {@code RESOURCE} parameters
     * against the file's own location.
     *
     * @param file the configuration file to load
     * @return the assembled full and key feature extractors
     * @throws AmbiguousServiceException if more than one {@link FeatureConfigurationParser} is
     *         registered
     * @throws FeatureConfigurationParseException if the file is not well-formed in the resolved
     *         parser's format
     * @throws FeatureConfigurationException if the parsed tree's content is invalid
     */
    public static AssembledFeatureExtractors load(Path file) {
        Objects.requireNonNull(file, "file must not be null");
        FeatureConfigurationParser parser = CrfServices
                .resolve(FeatureConfigurationParser.class, null, new XmlFeatureConfigurationParser());
        FeatureExtractorNode root = parser.parse(file);
        URL base;
        try {
            base = file.toAbsolutePath().toUri().toURL();
        } catch (MalformedURLException exception) {
            throw new UncheckedIOException(exception);
        }
        return new FeatureExtractorAssembler(FeatureExtractorFactoryRegistry.load()).assemble(root, base);
    }

    /**
     * Loads the feature extractor described by {@code url}, resolving {@code RESOURCE} parameters
     * against {@code url} itself.
     *
     * @param url the configuration URL to load
     * @return the assembled full and key feature extractors
     * @throws AmbiguousServiceException if more than one {@link FeatureConfigurationParser} is
     *         registered
     * @throws FeatureConfigurationParseException if the URL's content is not well-formed in the
     *         resolved parser's format
     * @throws FeatureConfigurationException if the parsed tree's content is invalid
     */
    public static AssembledFeatureExtractors load(URL url) {
        return load(url, url);
    }

    /**
     * Loads the feature extractor described by {@code url}, resolving {@code RESOURCE} parameters
     * against {@code baseLocation}.
     *
     * @param url the configuration URL to load
     * @param baseLocation the document location that {@code RESOURCE} parameters resolve against
     * @return the assembled full and key feature extractors
     * @throws AmbiguousServiceException if more than one {@link FeatureConfigurationParser} is
     *         registered
     * @throws FeatureConfigurationParseException if the URL's content is not well-formed in the
     *         resolved parser's format
     * @throws FeatureConfigurationException if the parsed tree's content is invalid
     */
    public static AssembledFeatureExtractors load(URL url, URL baseLocation) {
        Objects.requireNonNull(url, "url must not be null");
        Objects.requireNonNull(baseLocation, "baseLocation must not be null");
        FeatureConfigurationParser parser = CrfServices
                .resolve(FeatureConfigurationParser.class, null, new XmlFeatureConfigurationParser());
        FeatureExtractorNode root = parser.parse(url);
        return new FeatureExtractorAssembler(FeatureExtractorFactoryRegistry.load()).assemble(root, baseLocation);
    }
}
