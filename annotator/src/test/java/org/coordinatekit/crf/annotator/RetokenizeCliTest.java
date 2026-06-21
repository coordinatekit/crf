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

import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.interactiveTerminal;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.readOutput;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.reviewerFactory;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.words;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.writeInput;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jline.terminal.Terminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import picocli.CommandLine.ParameterException;

/**
 * Tests the picocli adapter concerns: parsing the standard flags into a
 * {@link RetokenizeConfiguration}, mapping parse failures to exit {@code 2}, and the {@code --help}
 * short-circuit to exit {@code 0}. The end-to-end parse→runner wiring is exercised through the
 * caller-owned-terminal seam
 * {@link RetokenizeCli#run(String[], RetokenizeRunner.ReviewerFactory, Terminal)}; the
 * interactive-terminal precondition, the fresh-pass precondition, and the standalone execution
 * paths are covered by {@link RetokenizeRunnerTest}.
 */
class RetokenizeCliTest {
    record ParseExceptionParameters(
            String name,
            List<String> arguments,
            Class<? extends Exception> expectedClass,
            String expectedMessageSubstring
    ) {}

    /**
     * The threshold is supplied as a raw argument string (this is the parsing seam) and the expected
     * parsed {@code double}. The configuration tests use a differently-shaped record because they pass
     * {@code double}s to the builder directly.
     */
    record ThresholdAcceptedParameters(String name, String threshold, double expected) {}

    @RegisterExtension
    final CapturedStandardStreams streams = new CapturedStandardStreams();

    @Test
    void parseArguments__defaults(@TempDir Path tempDirectory) {
        // ACT //
        RetokenizeConfiguration configuration = parse(
                "--input",
                tempDirectory.resolve("in.xml").toString(),
                "--output",
                tempDirectory.resolve("out.xml").toString()
        );

        // ASSERT //
        assertNotNull(configuration);
        assertEquals(tempDirectory.resolve("in.xml"), configuration.input());
        assertEquals(tempDirectory.resolve("out.xml"), configuration.output());
        assertNull(configuration.model());
        assertEquals(RetokenizeConfiguration.DEFAULT_THRESHOLD, configuration.threshold());
    }

    static Stream<ParseExceptionParameters> parseArguments__exception() {
        return Stream.of(
                new ParseExceptionParameters(
                        "missing_input",
                        List.of("--output", "out.xml"),
                        ParameterException.class,
                        "--input"
                ),
                new ParseExceptionParameters(
                        "missing_output",
                        List.of("--input", "in.xml"),
                        ParameterException.class,
                        "--output"
                ),
                new ParseExceptionParameters(
                        "threshold_negative",
                        List.of("--input", "in.xml", "--output", "out.xml", "--threshold", "-0.1"),
                        ParameterException.class,
                        "threshold must be in [0.0, 1.0], got: -0.1"
                ),
                new ParseExceptionParameters(
                        "threshold_above_one",
                        List.of("--input", "in.xml", "--output", "out.xml", "--threshold", "1.1"),
                        ParameterException.class,
                        "threshold must be in [0.0, 1.0], got: 1.1"
                ),
                new ParseExceptionParameters(
                        "unknown_flag",
                        List.of("--input", "in.xml", "--output", "out.xml", "--bogus", "value"),
                        ParameterException.class,
                        "--bogus"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void parseArguments__exception(ParseExceptionParameters parameters) {
        // ACT //
        Exception exception = assertThrows(
                parameters.expectedClass(),
                () -> parse(parameters.arguments().toArray(String[]::new))
        );

        // ASSERT //
        String message = exception.getMessage();
        assertNotNull(message, "exception message must not be null");
        assertTrue(
                message.contains(parameters.expectedMessageSubstring()),
                "expected message to contain '" + parameters.expectedMessageSubstring() + "' but was: " + message
        );
    }

    @Test
    void parseArguments__longAndShortFlagsParseIdentically(@TempDir Path tempDirectory) {
        // ARRANGE //
        String input = tempDirectory.resolve("in.xml").toString();
        String output = tempDirectory.resolve("out.xml").toString();
        String model = tempDirectory.resolve("model.bin").toString();

        // ACT //
        RetokenizeConfiguration longConfiguration = parse(
                "--input",
                input,
                "--output",
                output,
                "--model",
                model,
                "--threshold",
                "0.5"
        );
        RetokenizeConfiguration shortConfiguration = parse("-i", input, "-o", output, "-m", model, "-t", "0.5");

        // ASSERT //
        assertNotNull(longConfiguration);
        assertNotNull(shortConfiguration);
        assertEquals(longConfiguration.input(), shortConfiguration.input());
        assertEquals(longConfiguration.model(), shortConfiguration.model());
        assertEquals(longConfiguration.output(), shortConfiguration.output());
        assertEquals(longConfiguration.threshold(), shortConfiguration.threshold());
        assertEquals(tempDirectory.resolve("model.bin"), longConfiguration.model());
        assertEquals(0.5, longConfiguration.threshold());
    }

    static Stream<ThresholdAcceptedParameters> parseArguments__thresholdBoundariesAccepted() {
        return Stream.of(
                new ThresholdAcceptedParameters("lower_bound", "0.0", 0.0),
                new ThresholdAcceptedParameters("upper_bound", "1.0", 1.0)
        );
    }

    @MethodSource
    @ParameterizedTest
    void parseArguments__thresholdBoundariesAccepted(
            ThresholdAcceptedParameters parameters,
            @TempDir Path tempDirectory
    ) {
        // ACT //
        RetokenizeConfiguration configuration = parse(
                "--input",
                tempDirectory.resolve("in.xml").toString(),
                "--output",
                tempDirectory.resolve("out.xml").toString(),
                "--threshold",
                parameters.threshold()
        );

        // ASSERT //
        assertNotNull(configuration);
        assertEquals(
                parameters.expected(),
                configuration.threshold(),
                "inclusive boundary must be accepted and round-trip"
        );
    }

    @Test
    void run__helpFlagPrintsUsageAndReturnsZero() {
        // ACT //
        int exitCode = RetokenizeCli.run(new String[] {"--help"}, (configuration, terminal) -> {
            throw new AssertionError("factory should not be invoked when --help is requested");
        });

        // ASSERT //
        assertEquals(0, exitCode);
        assertTrue(
                streams.out().contains("--input"),
                "help output should include the usage banner; was: " + streams.out()
        );
    }

    @Test
    void run__parseFailureReturnsExitCodeTwo() {
        // ACT //
        int exitCode = RetokenizeCli.run(new String[] {"--input", "in.xml"}, (configuration, terminal) -> {
            throw new AssertionError("factory should not be invoked when parsing fails");
        });

        // ASSERT //
        assertEquals(2, exitCode);
        assertTrue(
                streams.err().contains("--output"),
                "expected stderr to name the missing required flag; was: " + streams.err()
        );
        assertTrue(
                streams.err().contains("Usage:"),
                "expected stderr to also emit the usage banner; was: " + streams.err()
        );
    }

    @Test
    void run__versionFlagReturnsZero() {
        // ACT //
        int exitCode = RetokenizeCli.run(new String[] {"--version"}, (configuration, terminal) -> {
            throw new AssertionError("factory should not be invoked when --version is requested");
        });

        // ASSERT //
        assertEquals(0, exitCode);
    }

    @Test
    void run__wiresParsedConfigurationThroughRunner(@TempDir Path tempDirectory) throws IOException {
        // ARRANGE //
        Path inputFile = writeInput(tempDirectory, words(List.of("Smith,", "Jones"), List.of("NN", "NN")));
        Path outputFile = tempDirectory.resolve("out.xml");
        String[] arguments = {"--input", inputFile.toString(), "--output", outputFile.toString(), "--threshold", "0.5"};
        AtomicReference<RetokenizeConfiguration> seen = new AtomicReference<>();
        RetokenizeRunner.ReviewerFactory factory = (configuration, terminal) -> {
            seen.set(configuration);
            return reviewerFactory().create(configuration, terminal);
        };

        // ACT //
        try (Terminal terminal = interactiveTerminal("A\n")) {
            int exitCode = RetokenizeCli.run(arguments, factory, terminal);

            // ASSERT //
            assertEquals(0, exitCode);
        }
        assertNotNull(seen.get(), "factory should have been invoked");
        assertEquals(inputFile, seen.get().input());
        assertEquals(outputFile, seen.get().output());
        assertEquals(0.5, seen.get().threshold());
        assertEquals(1, readOutput(outputFile).size());
    }

    private static RetokenizeConfiguration parse(String... arguments) {
        StringWriter sink = new StringWriter();
        return RetokenizeCli.parseArguments(arguments, new PrintWriter(sink), new PrintWriter(sink));
    }
}
