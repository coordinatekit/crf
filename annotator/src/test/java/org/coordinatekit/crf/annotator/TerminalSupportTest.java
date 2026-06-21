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

import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.nonInteractiveTerminal;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.quietTerminal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jline.terminal.Terminal;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Direct tests for the parser-free terminal glue {@link TerminalSupport} shared by
 * {@link AnnotatorRunner} and {@link RetokenizeRunner}: the interactive-terminal precondition, the
 * action-failure mapping, and the terminal lifecycle of the supplier-based entry.
 */
class TerminalSupportTest {
    @Test
    void runInteractive__opensSuppliedTerminalAndRunsAction() {
        // ARRANGE //
        StringWriter sink = new StringWriter();
        PrintWriter err = new PrintWriter(sink);

        // ACT //
        int exitCode = TerminalSupport
                .runInteractive("annotator", "Annotation", AnnotatorTestSupport::quietTerminal, err, terminal -> 0);
        err.flush();

        // ASSERT //
        assertEquals(0, exitCode);
        assertEquals("", sink.toString(), "no diagnostics on success: " + sink);
    }

    @Test
    void runInteractive__terminalOpenFailureReturnsOne() {
        // ARRANGE //
        StringWriter sink = new StringWriter();
        PrintWriter err = new PrintWriter(sink);

        // ACT //
        int exitCode = TerminalSupport.runInteractive("retokenize", "Retokenize", () -> {
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

    @Test
    void runInTerminal__actionIOExceptionReturnsOne() throws IOException {
        // ARRANGE //
        StringWriter sink = new StringWriter();
        PrintWriter err = new PrintWriter(sink);

        // ACT //
        int exitCode;
        try (Terminal terminal = quietTerminal()) {
            exitCode = TerminalSupport.runInTerminal("retokenize", "Retokenize", terminal, err, ignored -> {
                throw new IOException("disk full");
            });
        }
        err.flush();

        // ASSERT //
        assertEquals(1, exitCode);
        assertTrue(
                sink.toString().contains("Retokenize failed: disk full"),
                "stderr should report the action failure: " + sink
        );
    }

    @Test
    void runInTerminal__dumbTerminalRejected() throws IOException {
        // ARRANGE //
        StringWriter sink = new StringWriter();
        PrintWriter err = new PrintWriter(sink);

        // ACT //
        int exitCode;
        try (Terminal terminal = nonInteractiveTerminal()) {
            exitCode = TerminalSupport.runInTerminal("retokenize", "Retokenize", terminal, err, ignored -> {
                throw new AssertionError("action must not run for a dumb terminal");
            });
        }
        err.flush();

        // ASSERT //
        assertEquals(1, exitCode);
        assertTrue(
                sink.toString().contains("requires an interactive terminal"),
                "stderr should report the precondition failure: " + sink
        );
    }

    @Test
    void runInTerminal__successReturnsActionExitCode() throws IOException {
        // ARRANGE //
        StringWriter sink = new StringWriter();
        PrintWriter err = new PrintWriter(sink);

        // ACT //
        int exitCode;
        try (Terminal terminal = quietTerminal()) {
            exitCode = TerminalSupport.runInTerminal("annotator", "Annotation", terminal, err, ignored -> 0);
        }
        err.flush();

        // ASSERT //
        assertEquals(0, exitCode);
        assertEquals("", sink.toString(), "no diagnostics on success: " + sink);
    }
}
