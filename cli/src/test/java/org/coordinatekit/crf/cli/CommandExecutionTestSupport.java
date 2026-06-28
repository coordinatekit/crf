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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import picocli.CommandLine;

/**
 * Shared execution contract for the {@code annotate} and {@code retokenize} subcommands: the exit
 * codes picocli produces for help, a startup failure, a rejected argument list, and the version
 * flag. These cases never touch the concrete configuration type, so they live here once; each
 * command's configuration parsing is tested in its own subclass. Subclasses supply the command
 * under test via {@link #newCommand}. The input path value is irrelevant to every case here (each
 * fails at parsing or at service resolution, before any file is read), so a neutral literal is
 * used.
 */
abstract class CommandExecutionTestSupport {
    record ExitTwoParameters(String name, List<String> arguments, List<String> expectedErrorSubstrings) {}

    private record Execution(int exitCode, String out, String err) {}

    static void assertMessageContains(Throwable exception, String... fragments) {
        String message = exception.getMessage();
        assertNotNull(message, "expected a detail message");
        for (String fragment : fragments) {
            assertTrue(message.contains(fragment), "expected message to contain \"" + fragment + "\"; was: " + message);
        }
    }

    private Execution execute(String... arguments) {
        CommandLine commandLine = new CommandLine(newCommand(ResolvedServices.builder()));
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        commandLine.setOut(new PrintWriter(out));
        commandLine.setErr(new PrintWriter(err));
        int exitCode = commandLine.execute(arguments);
        return new Execution(exitCode, out.toString(), err.toString());
    }

    @Test
    void execute__helpFlagPrintsUsageAndReturnsZero() {
        // ACT //
        Execution execution = execute("--help");

        // ASSERT //
        assertEquals(0, execution.exitCode());
        assertTrue(execution.out().contains("--input"), "help should list the options; was: " + execution.out());
    }

    @Test
    void execute__missingComponentsReturnsExitCodeOne() {
        // ACT //
        Execution execution = execute("--input", "input", "--output", "output.xml");

        // ASSERT //
        assertEquals(1, execution.exitCode());
        assertTrue(
                execution.err().contains("TagProvider"),
                "stderr should guide the user to register a TagProvider; was: " + execution.err()
        );
    }

    static Stream<ExitTwoParameters> execute__rejectsBadArguments() {
        return Stream.of(
                new ExitTwoParameters("missing_input", List.of("--output", "output.xml"), List.of("--input")),
                new ExitTwoParameters("missing_output", List.of("--input", "input"), List.of("--output")),
                new ExitTwoParameters(
                        "threshold_negative",
                        List.of("--input", "input", "--output", "output.xml", "--threshold", "-0.1"),
                        List.of("threshold must be in [0.0, 1.0], got: -0.1")
                ),
                new ExitTwoParameters(
                        "threshold_above_one",
                        List.of("--input", "input", "--output", "output.xml", "--threshold", "1.1"),
                        List.of("threshold must be in [0.0, 1.0], got: 1.1")
                ),
                new ExitTwoParameters(
                        "unknown_flag",
                        List.of("--input", "input", "--output", "output.xml", "--bogus", "value"),
                        List.of("bogus", "Unknown option")
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void execute__rejectsBadArguments(ExitTwoParameters parameters) {
        // ACT //
        Execution execution = execute(parameters.arguments().toArray(String[]::new));

        // ASSERT //
        assertEquals(2, execution.exitCode());
        for (String expected : parameters.expectedErrorSubstrings()) {
            assertTrue(
                    execution.err().contains(expected),
                    "expected stderr to contain '" + expected + "'; was: " + execution.err()
            );
        }
    }

    @Test
    void execute__unknownTaggerLoaderNameFailsFast() {
        // ACT //
        // mallet is the only loader bundled on the CLI classpath, so an unknown --tagger-loader name fails
        // during resolution (before the tag provider is read), proving the flag reaches the services
        // builder.
        Execution execution = execute("--input", "input", "--output", "output.xml", "--tagger-loader", "nope");

        // ASSERT //
        assertEquals(1, execution.exitCode());
        assertTrue(execution.err().contains("nope"), "stderr should name the unknown loader; was: " + execution.err());
    }

    @Test
    void execute__versionFlagPrintsVersionAndReturnsZero() {
        // ACT //
        Execution execution = execute("--version");

        // ASSERT //
        assertEquals(0, execution.exitCode());
        assertFalse(execution.out().isBlank(), "version output should not be blank");
        assertTrue(
                execution.out().contains("crf (development build)"),
                "version output should be the documented loose-classpath fallback; was: " + execution.out()
        );
    }

    abstract Callable<Integer> newCommand(ResolvedServices.Builder servicesBuilder);
}
