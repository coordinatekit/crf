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

import org.jspecify.annotations.NullMarked;

import java.io.PrintWriter;

import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

/**
 * Shared, picocli-specific glue for the annotator's command-line adapters ({@link AnnotatorCli} and
 * {@link RetokenizeCli}).
 *
 * <p>
 * This class is the parser-side peer of {@link TerminalSupport}: where {@code TerminalSupport}
 * holds the parser-free terminal lifecycle, this class holds the type-agnostic picocli mechanics
 * both adapters repeat — the {@code --help} / {@code --version} short-circuit and the exit-2
 * mapping for a rejected argument list. Each adapter still owns its own flags and the mapping from
 * parsed arguments to a configuration, since those differ per command.
 */
@NullMarked
final class PicocliSupport {
    private PicocliSupport() {
        throw new UnsupportedOperationException("PicocliSupport is a utility class and cannot be instantiated");
    }

    /**
     * Prints the usage or version help to {@code out} if either was requested, short-circuiting
     * parsing.
     *
     * @param commandLine the parsed command line to inspect
     * @param out the writer for help / usage output
     * @return {@code true} if usage or version help was requested (and printed), {@code false}
     *         otherwise
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
     * Maps a parse failure to the exit-2 usage diagnostic: prints the failure message and usage to
     * {@code err} and returns the exit code.
     *
     * @param exception the parse failure picocli raised
     * @param err the writer for diagnostic output
     * @return {@code 2}, the process exit code for a rejected argument list
     */
    static int usageError(ParameterException exception, PrintWriter err) {
        err.println(exception.getMessage());
        exception.getCommandLine().usage(err);
        return 2;
    }
}
