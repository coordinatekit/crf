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

import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.MALFORMED_XML;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.interactiveTerminal;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.nonInteractiveTerminal;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.quietTerminal;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.readOutput;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.reviewerFactory;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.tokensOf;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.words;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.writeInput;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.writeSequences;
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
 * Tests the parser-free retokenize invocation: the interactive-terminal precondition, the
 * fresh-pass precondition, execution, and the exit-code mapping driven through the
 * caller-owned-terminal seam
 * {@link RetokenizeRunner#run(RetokenizeConfiguration, RetokenizeRunner.ReviewerFactory, Terminal)}.
 */
class RetokenizeRunnerTest {
    @RegisterExtension
    final CapturedStandardStreams streams = new CapturedStandardStreams();

    @Test
    void run__dumbTerminalRejected(@TempDir Path tempDirectory) throws IOException {
        // ARRANGE //
        RetokenizeConfiguration configuration = RetokenizeConfiguration.builder().input(tempDirectory.resolve("in.xml"))
                .output(tempDirectory.resolve("out.xml")).build();
        RetokenizeRunner.ReviewerFactory factory = (parsed, terminal) -> {
            throw new AssertionError("factory should not be invoked");
        };

        // ACT //
        int exitCode;
        try (Terminal terminal = nonInteractiveTerminal()) {
            exitCode = RetokenizeRunner.run(configuration, factory, terminal);
        }

        // ASSERT //
        assertEquals(1, exitCode);
        assertTrue(
                streams.err().contains("interactive terminal"),
                "expected stderr to contain precondition message, was: " + streams.err()
        );
    }

    @Test
    void run__happyPathRewritesMisalignedSequence(@TempDir Path tempDirectory) throws IOException {
        // ARRANGE //
        Path inputFile = writeInput(tempDirectory, words(List.of("Smith,", "Jones"), List.of("NN", "NN")));
        Path outputFile = tempDirectory.resolve("out.xml");
        RetokenizeConfiguration configuration = RetokenizeConfiguration.builder().input(inputFile).output(outputFile)
                .threshold(0.80).build();

        // ACT //
        try (Terminal terminal = interactiveTerminal("A\n")) {
            int exitCode = RetokenizeRunner.run(configuration, reviewerFactory(), terminal);

            // ASSERT //
            assertEquals(0, exitCode);
            assertTrue(streams.err().isBlank(), "happy path should not write to err; was: " + streams.err());
        }
        List<TrainingSequence<String>> written = readOutput(outputFile);
        assertEquals(1, written.size());
        assertEquals(List.of("Smith", ",", "Jones"), tokensOf(written.getFirst()), "accepted sequence re-tokenized");
        assertEquals("Smith, Jones", written.getFirst().surface(), "the re-tokenized surface round-trips");
    }

    @Test
    void run__malformedInputReturnsExitCodeOne(@TempDir Path tempDirectory) throws IOException {
        // ARRANGE //
        Path inputFile = tempDirectory.resolve("in.xml");
        Files.writeString(inputFile, MALFORMED_XML, StandardCharsets.UTF_8);
        RetokenizeConfiguration configuration = RetokenizeConfiguration.builder().input(inputFile)
                .output(tempDirectory.resolve("out.xml")).build();

        // ACT //
        int exitCode;
        try (Terminal terminal = quietTerminal()) {
            exitCode = RetokenizeRunner.run(configuration, reviewerFactory(), terminal);
        }

        // ASSERT //
        assertEquals(1, exitCode);
        String stderr = streams.err();
        assertTrue(stderr.contains("Retokenize failed:"), "expected stderr to report the failure; was: " + stderr);
        assertTrue(
                stderr.contains("XMLStreamException"),
                "expected stderr to identify the parse-failure path, not the precondition path; was: " + stderr
        );
    }

    @Test
    void run__nonEmptyOutputRejected(@TempDir Path tempDirectory) throws IOException {
        // ARRANGE //
        Path inputFile = writeInput(tempDirectory, words(List.of("Smith,", "Jones"), List.of("NN", "NN")));
        Path outputFile = writeSequences(
                tempDirectory.resolve("out.xml"),
                words(List.of("already", "here"), List.of("NN", "NN"))
        );
        RetokenizeConfiguration configuration = RetokenizeConfiguration.builder().input(inputFile).output(outputFile)
                .build();

        // ACT //
        int exitCode;
        try (Terminal terminal = quietTerminal()) {
            exitCode = RetokenizeRunner.run(configuration, reviewerFactory(), terminal);
        }

        // ASSERT //
        assertEquals(1, exitCode);
        String stderr = streams.err();
        assertTrue(stderr.contains("Retokenize failed:"), "expected stderr to report the failure; was: " + stderr);
        assertTrue(stderr.contains("must be absent or empty"), "expected stderr to explain the precondition");
    }

    @Test
    void run__reviewIOExceptionReturnsExitCodeOne(@TempDir Path tempDirectory) throws IOException {
        // ARRANGE //
        Path missingInput = tempDirectory.resolve("does-not-exist.xml");
        RetokenizeConfiguration configuration = RetokenizeConfiguration.builder().input(missingInput)
                .output(tempDirectory.resolve("out.xml")).build();

        // ACT //
        int exitCode;
        try (Terminal terminal = quietTerminal()) {
            exitCode = RetokenizeRunner.run(configuration, reviewerFactory(), terminal);
        }

        // ASSERT //
        assertEquals(1, exitCode);
        String stderr = streams.err();
        assertTrue(stderr.contains("Retokenize failed:"), "expected stderr to report the failure; was: " + stderr);
        assertTrue(
                stderr.contains(missingInput.getFileName().toString()),
                "stderr should identify the missing input; was: " + stderr
        );
    }
}
