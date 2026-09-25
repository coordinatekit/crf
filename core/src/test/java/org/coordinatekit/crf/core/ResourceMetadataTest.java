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
package org.coordinatekit.crf.core;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Guards the native image resource configuration for bundled schemas.
 *
 * <p>
 * This test reads the committed {@code resource-config.json}, extracts each resource pattern, and
 * verifies that it matches a real resource on the classpath. Renaming or moving a schema file will
 * fail this test in {@code ./gradlew test}, catching drift before it reaches a native image build.
 */
@NullMarked
class ResourceMetadataTest {
    private static final String CONFIG_RESOURCE_PATH = "META-INF/native-image/org.coordinatekit.crf/core/resource-config.json";

    @Test
    void resourceConfig__allResourcesExist() throws IOException {
        // ARRANGE //
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        java.io.InputStream configStream = classLoader.getResourceAsStream(CONFIG_RESOURCE_PATH);
        if (configStream == null) {
            throw new AssertionError("Could not load resource-config.json from classpath at " + CONFIG_RESOURCE_PATH);
        }
        String configJson = new String(configStream.readAllBytes(), StandardCharsets.UTF_8);
        JsonNode root = JsonMapper.builder().build().readTree(configJson);
        JsonNode includes = root.at("/resources/includes");

        List<String> missingResources = new ArrayList<>();

        // ACT //
        for (JsonNode include : includes) {
            String pattern = include.get("pattern").asString();
            String resourcePath = stripQuoteMarkers(pattern);

            if (classLoader.getResource(resourcePath) == null) {
                missingResources.add(resourcePath);
            }
        }

        // ASSERT //
        if (!missingResources.isEmpty()) {
            throw new AssertionError(
                    "resource-config.json references " + missingResources.size() + " missing resource(s): "
                            + missingResources
            );
        }
    }

    private static String stripQuoteMarkers(String pattern) {
        String result = pattern;
        if (result.startsWith("\\Q")) {
            result = result.substring(2);
        }
        if (result.endsWith("\\E")) {
            result = result.substring(0, result.length() - 2);
        }
        return result;
    }
}
