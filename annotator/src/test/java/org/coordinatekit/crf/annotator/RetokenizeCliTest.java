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
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.words;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.writeInput;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.writeSequences;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.coordinatekit.crf.annotator.AnnotatorTestSupport.PunctuationTokenizer;
import org.coordinatekit.crf.annotator.AnnotatorTestSupport.RetokenizeTestOptions;
import org.coordinatekit.crf.annotator.terminal.TerminalTaggingInterface;
import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
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
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import picocli.CommandLine.ParameterException;

class RetokenizeCliTest {
    record ParseExceptionParameters(
            String name,
            List<String> arguments,
            Class<? extends Exception> expectedClass,
            String expectedMessageSubstring
    ) {}

    record ThresholdAcceptedParameters(String name, String threshold, double expected) {}

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
        RetokenizeCli.Options options = parse(
                "--input",
                tempDirectory.resolve("in.xml").toString(),
                "--output",
                tempDirectory.resolve("out.xml").toString()
        );

        // ASSERT //
        assertNotNull(options);
        assertEquals(tempDirectory.resolve("in.xml"), options.input());
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
        RetokenizeCli.Options longOptions = parse(
                "--input",
                input,
                "--output",
                output,
                "--model",
                model,
                "--threshold",
                "0.5"
        );
        RetokenizeCli.Options shortOptions = parse("-i", input, "-o", output, "-m", model, "-t", "0.5");

        // ASSERT //
        assertNotNull(longOptions);
        assertEquals(longOptions, shortOptions);
        assertEquals(tempDirectory.resolve("model.bin"), longOptions.model());
        assertEquals(0.5, longOptions.threshold());
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
        RetokenizeCli.Options options = parse(
                "--input",
                tempDirectory.resolve("in.xml").toString(),
                "--output",
                tempDirectory.resolve("out.xml").toString(),
                "--threshold",
                parameters.threshold()
        );

        // ASSERT //
        assertNotNull(options);
        assertEquals(parameters.expected(), options.threshold(), "inclusive boundary must be accepted and round-trip");
    }

    @Test
    void run__dumbTerminalRejected(@TempDir Path tempDirectory) {
        // ARRANGE //
        String[] arguments = {"-i", tempDirectory.resolve("in.xml").toString(), "-o",
                        tempDirectory.resolve("out.xml").toString()};
        RetokenizeCli.ReviewerFactory factory = (options, terminal) -> {
            throw new AssertionError("factory should not be invoked");
        };

        // ACT //
        int exitCode = RetokenizeCli.run(arguments, factory, AnnotatorTestSupport::rejectedTerminal);

        // ASSERT //
        assertEquals(1, exitCode);
        assertTrue(
                capturedErr.toString(StandardCharsets.UTF_8).contains("interactive terminal"),
                "expected stderr to contain precondition message, was: " + capturedErr.toString(StandardCharsets.UTF_8)
        );
    }

    @Test
    void run__happyPathRewritesMisalignedSequence(@TempDir Path tempDirectory) throws IOException {
        // ARRANGE //
        Path inputFile = writeInput(tempDirectory, words(List.of("Smith,", "Jones"), List.of("NN", "NN")));
        Path outputFile = tempDirectory.resolve("out.xml");
        StringWriter errSink = new StringWriter();
        try (Terminal terminal = dumbTerminal("A\n")) {
            RetokenizeCli.Options options = new RetokenizeTestOptions(inputFile, null, outputFile, 0.80);

            // ACT //
            int exitCode = RetokenizeCli.run(options, reviewerFactory(), terminal, new PrintWriter(errSink));

            // ASSERT //
            assertEquals(0, exitCode);
            assertTrue(errSink.toString().isBlank(), "happy path should not write to err; was: " + errSink);
        }
        List<TrainingSequence<String>> written = readOutput(outputFile);
        assertEquals(1, written.size());
        assertEquals(List.of("Smith", ",", "Jones"), tokensOf(written.getFirst()), "accepted sequence re-tokenized");
        assertEquals("Smith, Jones", written.getFirst().surface(), "the re-tokenized surface round-trips");
    }

    @Test
    void run__helpFlagPrintsUsageAndReturnsZero() {
        // ACT //
        int exitCode = RetokenizeCli.run(new String[] {"--help"}, (options, terminal) -> {
            throw new AssertionError("factory should not be invoked when --help is requested");
        });

        // ASSERT //
        assertEquals(0, exitCode);
        assertTrue(
                capturedOut.toString(StandardCharsets.UTF_8).contains("--input"),
                "help output should include the usage banner"
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
        String[] arguments = {"-i", inputFile.toString(), "-o", outputFile.toString()};

        // ACT //
        int exitCode = RetokenizeCli.run(arguments, reviewerFactory(), AnnotatorTestSupport::quietTerminal);

        // ASSERT //
        assertEquals(1, exitCode);
        String stderr = capturedErr.toString(StandardCharsets.UTF_8);
        assertTrue(stderr.contains("Retokenize failed:"), "expected stderr to report the failure; was: " + stderr);
        assertTrue(stderr.contains("must be absent or empty"), "expected stderr to explain the precondition");
    }

    @Test
    void run__reviewIOExceptionReturnsExitCodeOne(@TempDir Path tempDirectory) {
        // ARRANGE //
        Path missingInput = tempDirectory.resolve("does-not-exist.xml");
        String[] arguments = {"-i", missingInput.toString(), "-o", tempDirectory.resolve("out.xml").toString()};

        // ACT //
        int exitCode = RetokenizeCli.run(arguments, reviewerFactory(), AnnotatorTestSupport::quietTerminal);

        // ASSERT //
        assertEquals(1, exitCode);
        String stderr = capturedErr.toString(StandardCharsets.UTF_8);
        assertTrue(stderr.contains("Retokenize failed:"), "expected stderr to report the failure; was: " + stderr);
        assertTrue(
                stderr.contains(missingInput.getFileName().toString()),
                "stderr should identify the missing input; was: " + stderr
        );
    }

    private static RetokenizeCli.Options parse(String... arguments) {
        StringWriter sink = new StringWriter();
        return RetokenizeCli.parseArguments(arguments, new PrintWriter(sink), new PrintWriter(sink));
    }

    private static RetokenizeCli.ReviewerFactory reviewerFactory() {
        return (parsedOptions, sharedTerminal) -> {
            TerminalTaggingInterface<String, String> ui = TerminalTaggingInterface.<String, String>builder()
                    .tagProvider(TAG_PROVIDER).terminal(sharedTerminal).threshold(parsedOptions.threshold()).build();
            return RetokenizeReviewer.<String, String>builder().tagProvider(TAG_PROVIDER).taggingInterface(ui)
                    .terminal(sharedTerminal).tokenizer(new PunctuationTokenizer()).build();
        };
    }
}
