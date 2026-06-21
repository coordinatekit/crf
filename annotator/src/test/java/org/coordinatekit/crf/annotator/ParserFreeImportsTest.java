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
package org.coordinatekit.crf.annotator;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Guards the parser-free seam: the configuration and runner classes must not import any
 * command-line parser, so a downstream can drive them from its own framework. Reads each class's
 * source and asserts none of its {@code import} statements reference {@code picocli}. (Javadoc
 * prose may still mention picocli when describing the adapter.)
 */
class ParserFreeImportsTest {
    static Stream<String> parserFreeClass__importsNoPicocli() {
        return Stream.of("AnnotatorConfiguration", "RetokenizeConfiguration", "AnnotatorRunner", "RetokenizeRunner");
    }

    @MethodSource
    @ParameterizedTest
    void parserFreeClass__importsNoPicocli(String className) throws IOException {
        // ARRANGE //
        Path source = sourceFile(className);

        // ACT //
        List<String> picocliImports = Files.readAllLines(source, StandardCharsets.UTF_8).stream().map(String::trim)
                .filter(line -> line.startsWith("import ") && line.contains("picocli")).toList();

        // ASSERT //
        assertTrue(
                picocliImports.isEmpty(),
                className + " is part of the parser-free seam and must not import picocli, but found: " + picocliImports
        );
    }

    private static Path sourceFile(String className) {
        String relativePath = "org/coordinatekit/crf/annotator/" + className + ".java";
        List<Path> candidates = List
                .of(Path.of("src/main/java", relativePath), Path.of("annotator/src/main/java", relativePath));
        return candidates.stream().filter(Files::exists).findFirst().orElseThrow(
                () -> new AssertionError("could not locate source for " + className + " under any of " + candidates)
        );
    }
}
