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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.coordinatekit.crf.core.InputSequence;
import org.coordinatekit.crf.core.PositionedToken;
import org.coordinatekit.crf.core.Sequence;
import org.coordinatekit.crf.core.feature.DefaultFeatureFormat;
import org.coordinatekit.crf.core.feature.FeatureExtractor;
import org.coordinatekit.crf.core.feature.FeatureFormat;

import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Shared helper for locating classpath test resources used across the configuration tests. */
public final class ConfigurationTestSupport {
    private static final FeatureFormat FORMAT = new DefaultFeatureFormat();

    private ConfigurationTestSupport() {}

    /**
     * Returns the URL of the current working directory, the default base location for tests with no
     * resource parameters.
     *
     * @return the current directory URL
     */
    public static URL currentDirectoryUrl() {
        return pathUrl(".");
    }

    /**
     * Returns the URL of {@code path}, resolved to an absolute path.
     *
     * @param path the path to convert
     * @return the path's URL
     */
    public static URL pathUrl(String path) {
        try {
            return Path.of(path).toAbsolutePath().toUri().toURL();
        } catch (MalformedURLException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * Extracts the features of {@code position} and renders them with {@link DefaultFeatureFormat}.
     *
     * @param extractor the extractor to run
     * @param tokens the sequence tokens
     * @param position the position to extract at
     * @return the rendered feature strings
     */
    public static Set<String> renderFeatures(FeatureExtractor extractor, List<String> tokens, int position) {
        Sequence<PositionedToken> sequence = new InputSequence(tokens);
        return extractor.extractAt(sequence, position).stream().map(FORMAT::render).collect(Collectors.toSet());
    }

    /**
     * Returns the directory holding the named classpath resource, for resolving path parameters
     * against.
     *
     * @param absoluteResource the absolute classpath resource name
     * @return the directory containing the resource
     */
    public static Path resourceDirectory(String absoluteResource) {
        try {
            Path parent = Path.of(resourceUrl(absoluteResource).toURI()).getParent();
            assertNotNull(parent, "resource has no parent directory: " + absoluteResource);
            return parent;
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * Returns the URL of the named classpath resource.
     *
     * @param absoluteResource the absolute classpath resource name
     * @return the resource URL
     */
    public static URL resourceUrl(String absoluteResource) {
        URL url = ConfigurationTestSupport.class.getResource(absoluteResource);
        assertNotNull(url, "missing test resource: " + absoluteResource);
        return url;
    }
}
