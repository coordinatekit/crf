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

import java.net.URISyntaxException;
import java.nio.file.Path;

/** Shared helper for locating classpath test resources used across the configuration tests. */
public final class ConfigurationTestSupport {
    private ConfigurationTestSupport() {}

    /**
     * Returns the directory holding the named classpath resource, for resolving path parameters
     * against.
     *
     * @param absoluteResource the absolute classpath resource name
     * @return the directory containing the resource
     */
    public static Path resourceDirectory(String absoluteResource) {
        try {
            var url = ConfigurationTestSupport.class.getResource(absoluteResource);
            assertNotNull(url, "missing test resource: " + absoluteResource);
            Path parent = Path.of(url.toURI()).getParent();
            assertNotNull(parent, "resource has no parent directory: " + absoluteResource);
            return parent;
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
