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

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import picocli.CommandLine;

/**
 * The entry point for the {@code crf} command-line tool.
 *
 * <p>
 * {@link #main(String[])} runs the tool and exits the process with the resulting code;
 * {@link #run(String[])} does the same work but returns the code instead of exiting, so it can be
 * embedded or tested. Both route {@code annotate} and {@code retokenize} as picocli subcommands and
 * resolve the domain services (tag provider, tokenizer, feature extractor, model loader) through
 * {@link java.util.ServiceLoader}.
 *
 * <p>
 * The tool ships no services of its own: a downstream puts its implementations on the classpath as
 * {@code META-INF/services} providers — at minimum a {@code TagProvider}, plus the {@code mallet}
 * module when {@code --model} is used. With none registered, a run fails fast with guidance rather
 * than producing garbage. Per-slot precedence is
 * {@code explicit > single service implementation > built-in default}; see
 * {@link ResolvedServices}.
 *
 * <p>
 * Exit codes follow the subcommands: {@code 0} on success or {@code --help}, {@code 1} on a startup
 * or run failure, {@code 2} on a rejected argument list (including an unknown or missing
 * subcommand).
 */
@NullMarked
public final class CrfLauncher {
    /**
     * The CRF wordmark: its figlet art and the cyan accent the "CRF" name is painted in. The
     * "CoordinateKit" half of the banner and the brand colors are fixed inside {@link Banner}; this is
     * the only product-specific piece, so a sibling tool reuses {@code Banner} unchanged and supplies
     * its own product here.
     */
    private static final Banner.Product CRF_PRODUCT = Banner.Product
            .fromResources(CrfLauncher.class, "banner/crf-big.txt", "banner/crf-small.txt", 91, 199, 227);

    private CrfLauncher() {
        throw new UnsupportedOperationException("CrfLauncher is a utility class and cannot be instantiated");
    }

    /**
     * Builds the {@code crf} command line over {@code servicesBuilder}. Package-private so tests can
     * install captured writers or explicit services before executing.
     *
     * @param servicesBuilder the builder each subcommand resolves its services from
     * @return the configured picocli command line
     */
    static CommandLine commandLine(ResolvedServices.Builder servicesBuilder) {
        // Every subcommand shares this one builder, which is safe: picocli runs at most one subcommand
        // per invocation, and each subcommand calls resolve() to read its own immutable set of services.
        CommandLine commandLine = new CommandLine(new RootCommand());
        // Show the brand banner above the root usage (crf --help and bare crf). Only the root command's
        // section map is touched, so subcommand help and --version are unaffected.
        Map<String, CommandLine.IHelpSectionRenderer> sections = commandLine.getHelpSectionMap();
        sections.put(CommandLine.Model.UsageMessageSpec.SECTION_KEY_HEADER, new Banner(CRF_PRODUCT));
        commandLine.setHelpSectionMap(sections);
        return commandLine.addSubcommand(new AnnotatorCommand(servicesBuilder))
                .addSubcommand(new RetokenizeCommand(servicesBuilder));
    }

    /**
     * Runs the tool and terminates the JVM with the resulting exit code.
     *
     * @param arguments the raw command-line arguments
     */
    public static void main(String[] arguments) {
        System.exit(run(arguments));
    }

    /**
     * Runs the tool against {@code arguments} and returns the process exit code without terminating the
     * JVM.
     *
     * @param arguments the raw command-line arguments
     * @return the process exit code
     */
    public static int run(String[] arguments) {
        Objects.requireNonNull(arguments, "arguments must not be null");
        CommandLine commandLine = commandLine(ResolvedServices.builder());
        // Force UTF-8 on the console writers so non-ASCII tokens render regardless of the platform
        // default charset; the writers wrap the (possibly redirected) standard streams.
        commandLine.setOut(new PrintWriter(System.out, true, StandardCharsets.UTF_8));
        commandLine.setErr(new PrintWriter(System.err, true, StandardCharsets.UTF_8));
        return commandLine.execute(arguments);
    }
}
