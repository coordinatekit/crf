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
package org.coordinatekit.crf.verification;

import org.coordinatekit.crf.cli.CrfLauncher;

import java.util.List;

/**
 * Runs the {@code crf} command line from {@code cli} and fails on any nonzero exit code.
 *
 * <p>
 * {@code --help} on the root command renders the banner, which reads the five art resources named
 * in {@code cli}'s {@code resource-config.json}. {@code --version} and each subcommand's
 * {@code --help} walk the option model picocli builds by reflection over the members that
 * {@code picocli-codegen} registers.
 */
final class CliChecks {
    private CliChecks() {}

    /**
     * Runs {@code crf} with each argument list, letting it print its usage to standard output.
     *
     * @throws IllegalStateException if an invocation exits with a nonzero code
     */
    static void run() {
        List<String[]> invocations = List.of(
                new String[] {"--help"},
                new String[] {"--version"},
                new String[] {"annotate", "--help"},
                new String[] {"retokenize", "--help"}
        );
        for (String[] arguments : invocations) {
            int exitCode = CrfLauncher.run(arguments);
            if (exitCode != 0) {
                throw new IllegalStateException(
                        "crf " + String.join(" ", arguments) + " exited with " + exitCode + "."
                );
            }
        }
    }
}
