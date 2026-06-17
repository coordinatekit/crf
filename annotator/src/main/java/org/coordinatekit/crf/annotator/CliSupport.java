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

import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

/**
 * Shared glue for the annotator's peer CLI helpers ({@link AnnotatorCli} and
 * {@link RetokenizeCli}).
 *
 * <p>
 * Both peers open an interactive system terminal, reject JLine "dumb" terminal types (which signal
 * a non-TTY context such as CI scripts, piped input, or {@code nohup}-style backgrounding), run
 * their work inside the terminal, and map an open or run failure to exit code {@code 1}. That
 * skeleton lives here so each peer only supplies its command-specific labels and the action to run.
 */
@NullMarked
final class CliSupport {
    private CliSupport() {
        throw new UnsupportedOperationException("CliSupport is a utility class and cannot be instantiated");
    }

    /**
     * Handles the {@code --help} / {@code --version} short-circuit shared by both CLI helpers: if
     * either was requested, prints the corresponding output to {@code out} and returns {@code true} so
     * the caller can stop before constructing options.
     *
     * @param commandLine the parsed command line
     * @param out the writer for usage / version output
     * @return {@code true} if usage or version help was requested and printed, {@code false} otherwise
     */
    static boolean helpOrVersionRequested(CommandLine commandLine, PrintWriter out) {
        if (commandLine.isUsageHelpRequested()) {
            commandLine.usage(out);
            return true;
        }
        if (commandLine.isVersionHelpRequested()) {
            commandLine.printVersionHelp(out);
            return true;
        }
        return false;
    }

    /**
     * Opens a terminal via {@code supplier}, enforces the interactive-terminal precondition, and runs
     * {@code action} inside it.
     *
     * <p>
     * A failure to open the terminal maps to exit {@code 1} with a {@code "Failed to open terminal: …"}
     * message. JLine "dumb" terminal types are rejected with
     * {@code commandLabel + " requires an interactive terminal; got terminal type: …"} and exit
     * {@code 1}. An {@link IOException} thrown by {@code action} maps to exit {@code 1} with
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
            String type = terminal.getType();
            if (Terminal.TYPE_DUMB.equals(type) || Terminal.TYPE_DUMB_COLOR.equals(type)) {
                err.println(commandLabel + " requires an interactive terminal; got terminal type: " + type);
                return 1;
            }
            return action.run(terminal);
        } catch (IOException exception) {
            err.println(failureLabel + " failed: " + exception.getMessage());
            return 1;
        }
    }

    /**
     * Validates that a parsed threshold lies in the closed interval {@code [0.0, 1.0]}, the rule shared
     * by both CLI helpers.
     *
     * @param commandLine the parsed command line, used to attach the parameter exception
     * @param threshold the parsed threshold value
     * @throws ParameterException if {@code threshold} is {@code NaN} or outside {@code [0.0, 1.0]}
     */
    static void validateThreshold(CommandLine commandLine, double threshold) {
        if (Double.isNaN(threshold) || threshold < 0.0 || threshold > 1.0) {
            throw new ParameterException(commandLine, "threshold must be in [0.0, 1.0], got: " + threshold);
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
