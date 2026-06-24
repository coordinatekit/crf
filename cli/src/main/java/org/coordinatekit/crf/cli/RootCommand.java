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

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * The top-level {@code crf} command that hosts the {@code annotate} and {@code retokenize}
 * subcommands.
 *
 * <p>
 * It carries no flags of its own beyond the standard help and version options. Invoked with no
 * subcommand it prints usage to standard error and exits {@code 2}; picocli routes a recognized
 * subcommand to its own {@link Callable} instead.
 */
@NullMarked
@Command(name = "crf", mixinStandardHelpOptions = true, versionProvider = CrfVersionProvider.class, synopsisSubcommandLabel = "<command>", description = "Interactive tools for building and maintaining CRF training data.")
final class RootCommand implements Callable<Integer> {
    @Spec
    @Nullable
    CommandSpec spec;

    @Override
    public Integer call() {
        CommandLine commandLine = Objects.requireNonNull(spec, "spec must be injected by picocli").commandLine();
        commandLine.usage(commandLine.getErr());
        return CommandLine.ExitCode.USAGE;
    }
}
