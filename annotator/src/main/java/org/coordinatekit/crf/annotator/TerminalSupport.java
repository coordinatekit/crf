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

import org.jline.terminal.Terminal;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Shared, parser-free terminal glue for the annotator's runners ({@link AnnotatorRunner} and
 * {@link RetokenizeRunner}).
 *
 * <p>
 * Both runners open an interactive terminal, reject JLine "dumb" terminal types (which signal a
 * non-TTY context such as CI scripts, piped input, or {@code nohup}-style backgrounding), run their
 * work inside the terminal, and map an open or run failure to exit code {@code 1}. That skeleton
 * lives here so each runner only supplies its command-specific labels and the action to run.
 *
 * <p>
 * This class imports no command-line parser; the picocli commands in the {@code cli} module own all
 * parsing. {@link #runInteractive} owns the terminal lifecycle for the common case, while
 * {@link #runInTerminal} runs the precondition and the action against a terminal the caller already
 * owns.
 */
@NullMarked
final class TerminalSupport {
    private TerminalSupport() {
        throw new UnsupportedOperationException("TerminalSupport is a utility class and cannot be instantiated");
    }

    /**
     * Opens a terminal via {@code supplier}, runs {@code action} inside it through
     * {@link #runInTerminal}, and closes the terminal it opened.
     *
     * <p>
     * A failure to open the terminal maps to exit {@code 1} with a {@code "Failed to open terminal: …"}
     * message. The interactive-terminal precondition and the action's failure mapping are delegated to
     * {@link #runInTerminal}; a failure to close the opened terminal maps to exit {@code 1} with
     * {@code failureLabel + " failed: …"}.
     *
     * @param commandLabel the command name used in the interactive-terminal precondition message
     * @param failureLabel the label prefixing a run-failure message
     * @param supplier supplies the JLine terminal to run against
     * @param err the writer for diagnostic output
     * @param action the work to run inside the opened terminal
     * @return {@code 0} on success, {@code 1} on an open failure, a rejected terminal, or a thrown
     *         {@link IOException}
     */
    static int runInteractive(
            String commandLabel,
            String failureLabel,
            TerminalSupplier supplier,
            PrintWriter err,
            InteractiveAction action
    ) {
        Terminal terminal;
        try {
            terminal = supplier.get();
        } catch (IOException exception) {
            err.println("Failed to open terminal: " + exception.getMessage());
            return 1;
        }

        try (terminal) {
            return runInTerminal(commandLabel, failureLabel, terminal, err, action);
        } catch (IOException exception) {
            err.println(failureLabel + " failed: " + exception.getMessage());
            return 1;
        }
    }

    /**
     * Enforces the interactive-terminal precondition on the already-open {@code terminal}, then runs
     * {@code action} inside it. The terminal is neither opened nor closed here; the caller owns its
     * lifecycle.
     *
     * <p>
     * JLine "dumb" terminal types are rejected with
     * {@code commandLabel + " requires an interactive terminal; got terminal type: …"} and exit
     * {@code 1}. An {@link IOException} thrown by {@code action} maps to exit {@code 1} with
     * {@code failureLabel + " failed: …"}.
     *
     * @param commandLabel the command name used in the interactive-terminal precondition message
     * @param failureLabel the label prefixing a run-failure message
     * @param terminal the already-open JLine terminal to run against
     * @param err the writer for diagnostic output
     * @param action the work to run inside the terminal
     * @return {@code 0} on success, {@code 1} on a rejected terminal or a thrown {@link IOException}
     */
    static int runInTerminal(
            String commandLabel,
            String failureLabel,
            Terminal terminal,
            PrintWriter err,
            InteractiveAction action
    ) {
        String type = terminal.getType();
        if (Terminal.TYPE_DUMB.equals(type) || Terminal.TYPE_DUMB_COLOR.equals(type)) {
            err.println(commandLabel + " requires an interactive terminal; got terminal type: " + type);
            return 1;
        }
        try {
            return action.run(terminal);
        } catch (IOException exception) {
            err.println(failureLabel + " failed: " + exception.getMessage());
            return 1;
        }
    }

    /** The command-specific work run inside an opened, interactive terminal. */
    @FunctionalInterface
    interface InteractiveAction {
        /**
         * Runs the command's work against the opened terminal.
         *
         * @param terminal the opened, interactive terminal
         * @return the process exit code
         * @throws IOException if the work fails
         */
        int run(Terminal terminal) throws IOException;
    }

    /** Supplies the JLine {@link Terminal} an interactive command runs against. */
    @FunctionalInterface
    interface TerminalSupplier {
        /**
         * Returns a terminal to run against.
         *
         * @return the terminal
         * @throws IOException if the terminal cannot be opened
         */
        Terminal get() throws IOException;
    }
}
