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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.stream.Stream;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParameterException;

/**
 * Direct tests for the {@link CliSupport} seam shared by {@code AnnotatorCli} and
 * {@code RetokenizeCli}, covering branches not reached transitively through the CLI tests: the
 * {@code NaN} and inclusive boundary cases of threshold validation, the {@code --version}
 * short-circuit, and a terminal that fails to open.
 */
class CliSupportTest {
    record ValidateThresholdParameters(String name, double threshold, boolean valid) {}

    @Test
    void helpOrVersionRequested__help() {
        // ARRANGE //
        StringWriter sink = new StringWriter();
        PrintWriter writer = new PrintWriter(sink);
        CommandLine commandLine = new CommandLine(new ParsedCommand());
        commandLine.parseArgs("--help");

        // ACT //
        boolean handled = CliSupport.helpOrVersionRequested(commandLine, writer);
        writer.flush();

        // ASSERT //
        assertTrue(handled, "a --help request must be reported as handled");
        assertTrue(sink.toString().contains("Usage:"), "the usage banner should be printed: " + sink);
    }

    @Test
    void helpOrVersionRequested__neither() {
        // ARRANGE //
        StringWriter sink = new StringWriter();
        PrintWriter writer = new PrintWriter(sink);
        CommandLine commandLine = new CommandLine(new ParsedCommand());
        commandLine.parseArgs();

        // ACT //
        boolean handled = CliSupport.helpOrVersionRequested(commandLine, writer);
        writer.flush();

        // ASSERT //
        assertFalse(handled, "neither help nor version requested");
        assertEquals("", sink.toString(), "nothing is printed when neither help nor version is requested");
    }

    @Test
    void helpOrVersionRequested__version() {
        // ARRANGE //
        StringWriter sink = new StringWriter();
        PrintWriter writer = new PrintWriter(sink);
        CommandLine commandLine = new CommandLine(new ParsedCommand());
        commandLine.parseArgs("--version");

        // ACT //
        boolean handled = CliSupport.helpOrVersionRequested(commandLine, writer);
        writer.flush();

        // ASSERT //
        assertTrue(handled, "a --version request must be reported as handled");
        assertTrue(sink.toString().contains("test 1.0"), "the version string should be printed: " + sink);
    }

    @Test
    void runInteractive__terminalOpenFailureReturnsOne() {
        // ARRANGE //
        StringWriter sink = new StringWriter();
        PrintWriter err = new PrintWriter(sink);

        // ACT //
        int exitCode = CliSupport.runInteractive("retokenize", "Retokenize", () -> {
            throw new IOException("no tty");
        }, err, terminal -> {
            throw new AssertionError("action must not run when the terminal cannot open");
        });
        err.flush();

        // ASSERT //
        assertEquals(1, exitCode);
        assertTrue(
                sink.toString().contains("Failed to open terminal:"),
                "stderr should report the terminal-open failure: " + sink
        );
    }

    static Stream<ValidateThresholdParameters> validateThreshold() {
        return Stream.of(
                new ValidateThresholdParameters("lower_bound", 0.0, true),
                new ValidateThresholdParameters("midpoint", 0.5, true),
                new ValidateThresholdParameters("upper_bound", 1.0, true),
                new ValidateThresholdParameters("not_a_number", Double.NaN, false),
                new ValidateThresholdParameters("below_zero", -0.1, false),
                new ValidateThresholdParameters("above_one", 1.1, false)
        );
    }

    @MethodSource
    @ParameterizedTest
    void validateThreshold(ValidateThresholdParameters parameters) {
        // ARRANGE //
        CommandLine commandLine = new CommandLine(new ParsedCommand());

        // ACT & ASSERT //
        if (parameters.valid()) {
            assertDoesNotThrow(() -> CliSupport.validateThreshold(commandLine, parameters.threshold()));
        } else {
            ParameterException exception = assertThrows(
                    ParameterException.class,
                    () -> CliSupport.validateThreshold(commandLine, parameters.threshold())
            );
            assertEquals("threshold must be in [0.0, 1.0], got: " + parameters.threshold(), exception.getMessage());
        }
    }

    @Command(name = "test", mixinStandardHelpOptions = true, version = "test 1.0")
    private static final class ParsedCommand {}
}
