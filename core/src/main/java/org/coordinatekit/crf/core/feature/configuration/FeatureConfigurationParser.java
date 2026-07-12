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

import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;

/**
 * The ServiceLoader SPI that turns the bytes of a configuration file into a
 * {@link FeatureExtractorNode} tree to be assembled into a feature extractor.
 *
 * <p>
 * {@link FeatureConfiguration} resolves a single registered parser, falling back to
 * {@link XmlFeatureConfigurationParser} when none is registered. The primary read is a {@link URL},
 * so a configuration can be read off the classpath and over the network as well as the filesystem,
 * and the {@link SourceLocation} URI is always the same locator the bytes are read from;
 * {@link #parse(Path)} is a filesystem convenience that routes through {@link #parse(URL)}.
 *
 * @see XmlFeatureConfigurationParser
 */
public interface FeatureConfigurationParser {
    /**
     * Opens {@code url} and parses the tree from it.
     *
     * <p>
     * Reading performs I/O per the URL's scheme: a network read for {@code http(s):}, a JAR-relative
     * read for {@code jar:}, and so on.
     *
     * @param url the URL to parse
     * @return the root of the parsed node tree
     * @throws UncheckedIOException if the URL cannot be opened or read
     * @throws FeatureConfigurationParseException if the source is not well-formed in this parser's
     *         format
     */
    FeatureExtractorNode parse(URL url);

    /**
     * Opens {@code file} and parses the tree from it, carrying {@code file}'s URI in the resulting
     * tree's {@link SourceLocation}.
     *
     * @param file the file to parse
     * @return the root of the parsed node tree
     * @throws UncheckedIOException if the file cannot be opened or read
     * @throws FeatureConfigurationParseException if the file is not well-formed in this parser's format
     */
    default FeatureExtractorNode parse(Path file) {
        try {
            return parse(file.toUri().toURL());
        } catch (MalformedURLException exception) {
            throw new UncheckedIOException("could not read feature configuration: " + file, exception);
        }
    }
}
