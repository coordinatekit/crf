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
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.readOutput;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import picocli.CommandLine.ParameterException;

/**
 * Tests the picocli adapter concerns: parsing the standard flags into an
 * {@link AnnotatorConfiguration}, mapping parse failures to exit {@code 2}, and the {@code --help}
 * short-circuit to exit {@code 0}. The end-to-end parse→runner wiring is exercised through the
 * caller-owned-terminal seam
 * {@link AnnotatorCli#run(String[], AnnotatorRunner.AnnotatorFactory, Terminal)}; the
 * interactive-terminal precondition and the standalone execution paths are covered by
 * {@link AnnotatorRunnerTest}.
 */
class AnnotatorCliTest {
    record ParseExceptionParameters(
            String name,
            List<String> arguments,
            Class<? extends Exception> expectedClass,
            String expectedMessageSubstring
    ) {}

    /**
     * Mirrors {@link RetokenizeCliTest}'s record: the threshold is supplied as a raw argument string
     * (this is the parsing seam) and the expected parsed {@code double}. The configuration tests use a
     * differently-shaped record because they pass {@code double}s to the builder directly.
     */
    record ThresholdAcceptedParameters(String name, String threshold, double expected) {}

    @RegisterExtension
    final CapturedStandardStreams streams = new CapturedStandardStreams();

    @Test
    void parseArguments__defaults(@TempDir Path tempDirectory) {
        // ACT //
        AnnotatorConfiguration configuration = parse(
                "--input",
                tempDirectory.resolve("in.txt").toString(),
                "--output",
                tempDirectory.resolve("out.xml").toString()
        );

        // ASSERT //
        assertNotNull(configuration);
        assertEquals(tempDirectory.resolve("in.txt"), configuration.input());
        assertEquals(tempDirectory.resolve("out.xml"), configuration.output());
        assertNull(configuration.model());
        assertEquals(AnnotatorConfiguration.DEFAULT_THRESHOLD, configuration.threshold());
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
                        List.of("--input", "in.txt"),
                        ParameterException.class,
                        "--output"
                ),
                new ParseExceptionParameters(
                        "threshold_negative",
                        List.of("--input", "in.txt", "--output", "out.xml", "--threshold", "-0.1"),
                        ParameterException.class,
                        "threshold must be in [0.0, 1.0], got: -0.1"
                ),
                new ParseExceptionParameters(
                        "threshold_above_one",
                        List.of("--input", "in.txt", "--output", "out.xml", "--threshold", "1.1"),
                        ParameterException.class,
                        "threshold must be in [0.0, 1.0], got: 1.1"
                ),
                new ParseExceptionParameters(
                        "unknown_flag",
                        List.of("--input", "in.txt", "--output", "out.xml", "--bogus", "value"),
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
        String input = tempDirectory.resolve("in.txt").toString();
        String output = tempDirectory.resolve("out.xml").toString();
        String model = tempDirectory.resolve("model.bin").toString();

        // ACT //
        AnnotatorConfiguration longConfiguration = parse(
                "--input",
                input,
                "--output",
                output,
                "--model",
                model,
                "--threshold",
                "0.5"
        );
        AnnotatorConfiguration shortConfiguration = parse("-i", input, "-o", output, "-m", model, "-t", "0.5");

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
        AnnotatorConfiguration configuration = parse(
                "--input",
                tempDirectory.resolve("in.txt").toString(),
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
        int exitCode = AnnotatorCli.run(new String[] {"--help"}, (configuration, terminal) -> {
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
        int exitCode = AnnotatorCli.run(new String[] {"--input", "in.txt"}, (configuration, terminal) -> {
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
        int exitCode = AnnotatorCli.run(new String[] {"--version"}, (configuration, terminal) -> {
            throw new AssertionError("factory should not be invoked when --version is requested");
        });

        // ASSERT //
        assertEquals(0, exitCode);
    }

    @Test
    void run__wiresParsedConfigurationThroughRunner(@TempDir Path tempDirectory) throws IOException {
        // ARRANGE //
        Path inputFile = tempDirectory.resolve("in.txt");
        Path outputFile = tempDirectory.resolve("out.xml");
        Files.writeString(inputFile, "the quick brown\nfox jumps over\n", StandardCharsets.UTF_8);
        String[] arguments = {"--input", inputFile.toString(), "--output", outputFile.toString(), "--threshold", "0.5"};
        AtomicReference<AnnotatorConfiguration> seen = new AtomicReference<>();
        AnnotatorRunner.AnnotatorFactory factory = (configuration, terminal) -> {
            seen.set(configuration);
            return annotatorFactory().create(configuration, terminal);
        };

        // ACT //
        try (Terminal terminal = interactiveTerminal("A\nA\n")) {
            int exitCode = AnnotatorCli.run(arguments, factory, terminal);

            // ASSERT //
            assertEquals(0, exitCode);
        }
        assertNotNull(seen.get(), "factory should have been invoked");
        assertEquals(inputFile, seen.get().input());
        assertEquals(outputFile, seen.get().output());
        assertEquals(0.5, seen.get().threshold());
        assertEquals(2, readOutput(outputFile).size());
    }

    private static AnnotatorConfiguration parse(String... arguments) {
        StringWriter sink = new StringWriter();
        return AnnotatorCli.parseArguments(arguments, new PrintWriter(sink), new PrintWriter(sink));
    }
}
