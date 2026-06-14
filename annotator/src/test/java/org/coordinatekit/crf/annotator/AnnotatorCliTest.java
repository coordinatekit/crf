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

import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.TAG_PROVIDER;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.dumbTerminal;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.readOutput;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.tokensOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.coordinatekit.crf.annotator.AnnotatorTestSupport.TestOptions;
import org.coordinatekit.crf.annotator.terminal.TerminalTaggingInterface;
import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.coordinatekit.crf.core.preprocessing.WhitespaceTokenizer;
import org.jline.terminal.Terminal;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import picocli.CommandLine.ParameterException;

class AnnotatorCliTest {
    record ParseExceptionParameters(
            String name,
            List<String> arguments,
            Class<? extends Exception> expectedClass,
            String expectedMessageSubstring
    ) {}

    private final ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
    private final ByteArrayOutputStream capturedOut = new ByteArrayOutputStream();
    private @Nullable PrintStream originalErr;
    private @Nullable PrintStream originalOut;

    @BeforeEach
    void redirectStandardStreams() {
        originalOut = System.out;
        originalErr = System.err;
        System.setOut(new PrintStream(capturedOut, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(capturedErr, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStandardStreams() {
        if (originalOut != null) {
            System.setOut(originalOut);
        }
        if (originalErr != null) {
            System.setErr(originalErr);
        }
    }

    @Test
    void parseArguments__defaults(@TempDir Path tempDirectory) {
        // ACT //
        AnnotatorCli.Options options = parse(
                "--input",
                tempDirectory.resolve("in.txt").toString(),
                "--output",
                tempDirectory.resolve("out.xml").toString()
        );

        // ASSERT //
        assertNotNull(options);
        assertEquals(tempDirectory.resolve("in.txt"), options.input());
        assertEquals(tempDirectory.resolve("out.xml"), options.output());
        assertNull(options.model());
        assertEquals(0.80, options.threshold());
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
        AnnotatorCli.Options longOptions = parse(
                "--input",
                input,
                "--output",
                output,
                "--model",
                model,
                "--threshold",
                "0.5"
        );
        AnnotatorCli.Options shortOptions = parse("-i", input, "-o", output, "-m", model, "-t", "0.5");

        // ASSERT //
        assertEquals(longOptions, shortOptions);
        assertNotNull(longOptions);
        assertEquals(tempDirectory.resolve("model.bin"), longOptions.model());
        assertEquals(0.5, longOptions.threshold());
    }

    @Test
    void run__annotateIOExceptionReturnsExitCodeOne(@TempDir Path tempDirectory) {
        // ARRANGE //
        Path missingInput = tempDirectory.resolve("does-not-exist.txt");
        String[] arguments = {"-i", missingInput.toString(), "-o", tempDirectory.resolve("out.xml").toString()};

        // ACT //
        int exitCode = AnnotatorCli.run(arguments, annotatorFactory(), AnnotatorTestSupport::quietTerminal);

        // ASSERT //
        assertEquals(1, exitCode);
        String stderr = capturedErr.toString(StandardCharsets.UTF_8);
        assertTrue(stderr.contains("Annotation failed:"), "expected stderr to report the failure; was: " + stderr);
        assertTrue(stderr.contains(missingInput.toString()), "expected stderr to include the underlying message");
    }

    @Test
    void run__dumbTerminalRejected(@TempDir Path tempDirectory) {
        // ARRANGE //
        String[] arguments = {"-i", tempDirectory.resolve("in.txt").toString(), "-o",
                        tempDirectory.resolve("out.xml").toString()};
        AnnotatorCli.AnnotatorFactory factory = (options, terminal) -> {
            throw new AssertionError("factory should not be invoked");
        };

        // ACT //
        int exitCode = AnnotatorCli.run(arguments, factory, AnnotatorTestSupport::rejectedTerminal);

        // ASSERT //
        assertEquals(1, exitCode);
        assertTrue(
                capturedErr.toString(StandardCharsets.UTF_8).contains("interactive terminal"),
                "expected stderr to contain precondition message, was: " + capturedErr.toString(StandardCharsets.UTF_8)
        );
    }

    @Test
    void run__happyPathWritesAcceptedSequences(@TempDir Path tempDirectory) throws IOException {
        // ARRANGE //
        Path inputFile = tempDirectory.resolve("in.txt");
        Path outputFile = tempDirectory.resolve("out.xml");
        Files.writeString(inputFile, "the quick brown\nfox jumps over\nthe lazy dog\n", StandardCharsets.UTF_8);
        try (Terminal terminal = dumbTerminal("A\nA\nX\n")) {
            AnnotatorCli.Options options = new TestOptions(inputFile, null, outputFile, 0.80);

            // ACT //
            int exitCode = AnnotatorCli.run(options, annotatorFactory(), terminal);

            // ASSERT //
            assertEquals(0, exitCode);
        }
        List<TrainingSequence<String>> written = readOutput(outputFile);
        assertEquals(2, written.size());
        assertEquals(List.of("the", "quick", "brown"), tokensOf(written.get(0)));
        assertEquals(List.of("fox", "jumps", "over"), tokensOf(written.get(1)));
    }

    @Test
    void run__helpFlagPrintsUsageAndReturnsZero() {
        // ACT //
        int exitCode = AnnotatorCli.run(new String[] {"--help"}, (options, terminal) -> {
            throw new AssertionError("factory should not be invoked when --help is requested");
        });

        // ASSERT //
        assertEquals(0, exitCode);
    }

    private static AnnotatorCli.AnnotatorFactory annotatorFactory() {
        return (parsedOptions, sharedTerminal) -> {
            TerminalTaggingInterface<String, String> ui = TerminalTaggingInterface.<String, String>builder()
                    .tagProvider(TAG_PROVIDER).terminal(sharedTerminal).threshold(parsedOptions.threshold()).build();
            return Annotator.<String, String>builder().tagProvider(TAG_PROVIDER).taggingInterface(ui)
                    .terminal(sharedTerminal).tokenizer(new WhitespaceTokenizer()).build();
        };
    }

    private static AnnotatorCli.Options parse(String... arguments) {
        StringWriter sink = new StringWriter();
        return AnnotatorCli.parseArguments(arguments, new PrintWriter(sink), new PrintWriter(sink));
    }
}
