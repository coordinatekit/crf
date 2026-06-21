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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

/**
 * Direct tests for the parser glue {@link PicocliSupport} shared by {@link AnnotatorCli} and
 * {@link RetokenizeCli}: the {@code --help} / {@code --version} short-circuit and the exit-2 usage
 * diagnostic. Driven through a self-contained {@link SampleCommand} so the help and version content
 * can be asserted independently of either real command (neither of which configures a version
 * string).
 */
class PicocliSupportTest {
    @Test
    void helpOrVersionRequested__help() {
        // ARRANGE //
        StringWriter sink = new StringWriter();
        PrintWriter out = new PrintWriter(sink);
        CommandLine commandLine = new CommandLine(new SampleCommand());
        commandLine.parseArgs("--help");

        // ACT //
        boolean handled = PicocliSupport.helpOrVersionRequested(commandLine, out);
        out.flush();

        // ASSERT //
        assertTrue(handled, "--help should be handled as a short-circuit");
        assertTrue(sink.toString().contains("--input"), "usage banner should list the command's options; was: " + sink);
    }

    @Test
    void helpOrVersionRequested__neither() {
        // ARRANGE //
        StringWriter sink = new StringWriter();
        PrintWriter out = new PrintWriter(sink);
        SampleCommand command = new SampleCommand();
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs("--input", "in.txt");

        // ACT //
        boolean handled = PicocliSupport.helpOrVersionRequested(commandLine, out);
        out.flush();

        // ASSERT //
        assertFalse(handled, "a normal parse should not short-circuit");
        assertEquals("in.txt", command.input, "the parsed option should have been bound");
        assertEquals("", sink.toString(), "nothing should be printed when neither help nor version is requested");
    }

    @Test
    void helpOrVersionRequested__version() {
        // ARRANGE //
        StringWriter sink = new StringWriter();
        PrintWriter out = new PrintWriter(sink);
        CommandLine commandLine = new CommandLine(new SampleCommand());
        commandLine.parseArgs("--version");

        // ACT //
        boolean handled = PicocliSupport.helpOrVersionRequested(commandLine, out);
        out.flush();

        // ASSERT //
        assertTrue(handled, "--version should be handled as a short-circuit");
        assertTrue(
                sink.toString().contains("sample 1.0"),
                "version output should contain the configured version; was: " + sink
        );
    }

    @Test
    void usageError__printsMessageAndUsageAndReturnsTwo() {
        // ARRANGE //
        StringWriter sink = new StringWriter();
        PrintWriter err = new PrintWriter(sink);
        CommandLine commandLine = new CommandLine(new SampleCommand());
        ParameterException exception = new ParameterException(commandLine, "boom");

        // ACT //
        int exitCode = PicocliSupport.usageError(exception, err);
        err.flush();

        // ASSERT //
        assertEquals(2, exitCode);
        String diagnostics = sink.toString();
        assertTrue(diagnostics.contains("boom"), "stderr should include the failure message; was: " + diagnostics);
        assertTrue(diagnostics.contains("Usage:"), "stderr should also include the usage banner; was: " + diagnostics);
    }

    @Command(name = "sample", mixinStandardHelpOptions = true, version = "sample 1.0")
    private static final class SampleCommand {
        @Option(names = {"-i", "--input"}, required = true, description = "An input path.")
        @Nullable
        String input;
    }
}
