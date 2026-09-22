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
package org.coordinatekit.crf.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guards the committed native image resource metadata against a renamed or moved banner resource.
 *
 * <p>
 * A native image can serve only the resources its resource configuration matches. This test reads
 * the committed {@code resource-config.json}, recovers each pattern's literal path from its
 * {@code \Q...\E} quoting, and asserts the classpath still has a resource at that path. The build's
 * own JVM tests load banner art by name ({@link BannerHeaderRendererTest}), which would not catch a
 * stale pattern that no longer matches anything.
 */
class ResourceMetadataTest {
    private static final String RESOURCE_CONFIG_PATH = "/META-INF/native-image/org.coordinatekit.crf/cli/resource-config.json";

    private static String literalPath(String pattern) {
        assertTrue(
                pattern.startsWith("\\Q") && pattern.endsWith("\\E"),
                "expected a \\Q...\\E literal pattern, was: " + pattern
        );
        return pattern.substring(2, pattern.length() - 2);
    }

    private static List<String> patterns() throws IOException {
        try (InputStream in = ResourceMetadataTest.class.getResourceAsStream(RESOURCE_CONFIG_PATH)) {
            Objects.requireNonNull(in, "missing resource: " + RESOURCE_CONFIG_PATH);
            JsonNode root = JsonMapper.builder().build().readTree(in.readAllBytes());
            List<String> patterns = new ArrayList<>();
            for (JsonNode include : root.get("resources").get("includes")) {
                patterns.add(include.get("pattern").asString());
            }
            return patterns;
        }
    }

    @Test
    void resourceConfig__namesResourcesThatExist() throws IOException {
        // ARRANGE //
        List<String> patterns = patterns();

        // ACT //
        List<String> missing = patterns.stream()
                .map(ResourceMetadataTest::literalPath)
                .filter(path -> ResourceMetadataTest.class.getResource("/" + path) == null)
                .toList();

        // ASSERT //
        assertTrue(missing.isEmpty(), "resource-config.json names resource(s) not found on the classpath: " + missing);
    }

    @Test
    void resourceConfig__isNotEmpty() throws IOException {
        // ACT //
        List<String> patterns = patterns();

        // ASSERT //
        assertTrue(patterns.size() >= 3, "expected at least the crf-* and cli-brand entries, was: " + patterns);
    }
}
