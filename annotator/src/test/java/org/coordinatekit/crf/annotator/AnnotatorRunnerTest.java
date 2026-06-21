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

import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.annotatorFactory;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.interactiveTerminal;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.nonInteractiveTerminal;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.quietTerminal;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.readOutput;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.tokensOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.jline.terminal.Terminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests the parser-free annotate invocation: the interactive-terminal precondition, execution, and
 * the exit-code mapping driven through the caller-owned-terminal seam
 * {@link AnnotatorRunner#run(AnnotatorConfiguration, AnnotatorRunner.AnnotatorFactory, Terminal)}.
 */
class AnnotatorRunnerTest {
    @RegisterExtension
    final CapturedStandardStreams streams = new CapturedStandardStreams();

    @Test
    void run__dumbTerminalRejected(@TempDir Path tempDirectory) throws IOException {
        // ARRANGE //
        AnnotatorConfiguration configuration = AnnotatorConfiguration.builder().input(tempDirectory.resolve("in.txt"))
                .output(tempDirectory.resolve("out.xml")).build();
        AnnotatorRunner.AnnotatorFactory factory = (parsed, terminal) -> {
            throw new AssertionError("factory should not be invoked");
        };

        // ACT //
        int exitCode;
        try (Terminal terminal = nonInteractiveTerminal()) {
            exitCode = AnnotatorRunner.run(configuration, factory, terminal);
        }

        // ASSERT //
        assertEquals(1, exitCode);
        assertTrue(
                streams.err().contains("interactive terminal"),
                "expected stderr to contain precondition message, was: " + streams.err()
        );
    }

    @Test
    void run__happyPathWritesAcceptedSequences(@TempDir Path tempDirectory) throws IOException {
        // ARRANGE //
        Path inputFile = tempDirectory.resolve("in.txt");
        Path outputFile = tempDirectory.resolve("out.xml");
        Files.writeString(inputFile, "the quick brown\nfox jumps over\nthe lazy dog\n", StandardCharsets.UTF_8);
        AnnotatorConfiguration configuration = AnnotatorConfiguration.builder().input(inputFile).output(outputFile)
                .threshold(0.80).build();

        // ACT //
        try (Terminal terminal = interactiveTerminal("A\nA\nX\n")) {
            int exitCode = AnnotatorRunner.run(configuration, annotatorFactory(), terminal);

            // ASSERT //
            assertEquals(0, exitCode);
        }
        List<TrainingSequence<String>> written = readOutput(outputFile);
        assertEquals(2, written.size());
        assertEquals(List.of("the", "quick", "brown"), tokensOf(written.get(0)));
        assertEquals(List.of("fox", "jumps", "over"), tokensOf(written.get(1)));
    }

    @Test
    void run__ioExceptionReturnsExitCodeOne(@TempDir Path tempDirectory) throws IOException {
        // ARRANGE //
        Path missingInput = tempDirectory.resolve("does-not-exist.txt");
        AnnotatorConfiguration configuration = AnnotatorConfiguration.builder().input(missingInput)
                .output(tempDirectory.resolve("out.xml")).build();

        // ACT //
        int exitCode;
        try (Terminal terminal = quietTerminal()) {
            exitCode = AnnotatorRunner.run(configuration, annotatorFactory(), terminal);
        }

        // ASSERT //
        assertEquals(1, exitCode);
        String stderr = streams.err();
        assertTrue(stderr.contains("Annotation failed:"), "expected stderr to report the failure; was: " + stderr);
        assertTrue(stderr.contains(missingInput.toString()), "expected stderr to include the underlying message");
    }
}
