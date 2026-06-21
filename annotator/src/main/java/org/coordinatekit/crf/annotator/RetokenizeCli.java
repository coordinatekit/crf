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
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.ToIntFunction;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

/**
 * Picocli adapter that parses the standard retokenize flags ({@code --input}, {@code --output},
 * {@code --model}, {@code --threshold}) into a {@link RetokenizeConfiguration} and delegates to
 * {@link RetokenizeRunner}.
 *
 * <p>
 * This is the peer of {@link AnnotatorCli} for the retokenize flow: where {@code AnnotatorCli}
 * drives the annotate flow, this adapter drives the retokenize flow. It owns only argument parsing,
 * the {@code --help} / {@code --version} short-circuit, and the exit-2 mapping for bad arguments;
 * the interactive-terminal precondition, the fresh-pass precondition, execution, and the remaining
 * exit codes live in {@link RetokenizeRunner}. A caller using a different command-line framework
 * can skip this adapter, build a {@link RetokenizeConfiguration} directly, and call
 * {@link RetokenizeRunner#run(RetokenizeConfiguration, RetokenizeRunner.ReviewerFactory)}.
 *
 * <p>
 * The two adapters are equal, flat helpers; the library does not own a top-level router. A
 * downstream {@code main} owns the nesting, routing {@code args[0]} to one adapter or the other (or
 * shipping two binaries):
 *
 * <pre>
 * {@code
 * public static void main(String[] arguments) {
 *     String[] rest = Arrays.copyOfRange(arguments, Math.min(1, arguments.length), arguments.length);
 *     int exitCode = switch (arguments.length == 0 ? "" : arguments[0]) {
 *         case "annotate"   -> AnnotatorCli.run(rest, annotatorFactory);
 *         case "retokenize" -> RetokenizeCli.run(rest, reviewerFactory);
 *         default -> {
 *             System.err.println("usage: <tool> {annotate|retokenize} ...");
 *             yield 2;
 *         }
 *     };
 *     System.exit(exitCode);
 * }
 * }
 * </pre>
 *
 * <p>
 * Exit codes:
 *
 * <ul>
 * <li>{@code 0} — review completed (or {@code --help} was requested);</li>
 * <li>{@code 1} — interactive-terminal precondition failed, the terminal could not be opened, the
 * review's fresh-pass precondition was violated (input path equals output, or the output exists and
 * is non-empty), or {@link RetokenizeReviewer#review(Path, Path)} threw an
 * {@link IOException};</li>
 * <li>{@code 2} — picocli rejected the arguments (missing required flag, invalid threshold, unknown
 * option, …).</li>
 * </ul>
 */
@NullMarked
public final class RetokenizeCli {
    private RetokenizeCli() {
        throw new UnsupportedOperationException("RetokenizeCli is a utility class and cannot be instantiated");
    }

    private static int parseAndRun(String[] arguments, ToIntFunction<RetokenizeConfiguration> dispatch) {
        PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        PrintWriter err = new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8), true);

        RetokenizeConfiguration configuration;
        try {
            configuration = parseArguments(arguments, out, err);
        } catch (ParameterException exception) {
            return PicocliSupport.usageError(exception, err);
        }
        if (configuration == null) {
            return 0;
        }
        return dispatch.applyAsInt(configuration);
    }

    /**
     * Parses {@code arguments} via picocli and returns the populated {@link RetokenizeConfiguration}.
     * Returns {@code null} if {@code --help} was requested (in which case usage was printed to
     * {@code out}).
     *
     * @param arguments the raw command-line arguments
     * @param out the writer for help / usage output
     * @param err the writer picocli uses for diagnostic output during parsing
     * @return the parsed configuration, or {@code null} if {@code --help} was requested
     * @throws ParameterException if parsing fails (missing required flag, threshold out of range,
     *         unknown option, …)
     */
    static @Nullable RetokenizeConfiguration parseArguments(String[] arguments, PrintWriter out, PrintWriter err) {
        ParsedArguments parsed = new ParsedArguments();
        CommandLine commandLine = new CommandLine(parsed);
        commandLine.setOut(out);
        commandLine.setErr(err);
        commandLine.parseArgs(arguments);
        if (PicocliSupport.helpOrVersionRequested(commandLine, out)) {
            return null;
        }
        try {
            return RetokenizeConfiguration.builder()
                    .input(Objects.requireNonNull(parsed.input, "input must not be null after parseArgs"))
                    .model(parsed.model)
                    .output(Objects.requireNonNull(parsed.output, "output must not be null after parseArgs"))
                    .threshold(parsed.threshold).build();
        } catch (IllegalArgumentException exception) {
            throw new ParameterException(commandLine, exception.getMessage());
        }
    }

    /**
     * Parses {@code arguments}, builds a {@link RetokenizeConfiguration}, and delegates to
     * {@link RetokenizeRunner#run(RetokenizeConfiguration, RetokenizeRunner.ReviewerFactory)}. Returns
     * a process exit code suitable for {@link System#exit(int)}.
     *
     * @param arguments the raw command-line arguments
     * @param factory the factory that constructs the reviewer from the configuration and terminal
     * @return the process exit code
     */
    public static int run(String[] arguments, RetokenizeRunner.ReviewerFactory factory) {
        Objects.requireNonNull(arguments, "arguments must not be null");
        Objects.requireNonNull(factory, "factory must not be null");
        return parseAndRun(arguments, configuration -> RetokenizeRunner.run(configuration, factory));
    }

    /**
     * Parses {@code arguments}, builds a {@link RetokenizeConfiguration}, and delegates to
     * {@link RetokenizeRunner#run(RetokenizeConfiguration, RetokenizeRunner.ReviewerFactory, Terminal)}
     * against the caller-owned {@code terminal}. This is the testable seam behind
     * {@link #run(String[], RetokenizeRunner.ReviewerFactory)}, which opens a system terminal; the
     * terminal is not closed by this method.
     *
     * @param arguments the raw command-line arguments
     * @param factory the factory that constructs the reviewer from the configuration and terminal
     * @param terminal the JLine terminal to run against
     * @return the process exit code
     */
    static int run(String[] arguments, RetokenizeRunner.ReviewerFactory factory, Terminal terminal) {
        Objects.requireNonNull(arguments, "arguments must not be null");
        Objects.requireNonNull(factory, "factory must not be null");
        Objects.requireNonNull(terminal, "terminal must not be null");
        return parseAndRun(arguments, configuration -> RetokenizeRunner.run(configuration, factory, terminal));
    }

    @Command(name = "retokenize", mixinStandardHelpOptions = true, description = "Walk an XML training-data file, re-tokenize each sequence with the new "
            + "tokenizer, re-tag misaligned sequences via an interactive prompt, and write a corrected XML file.")
    private static final class ParsedArguments {
        @Option(names = {"-i", "--input"}, required = true, description = "XML training-data file to review.")
        @Nullable
        Path input;

        @Option(names = {"-m",
                        "--model"}, description = "Path to a serialized model. Optional; if absent re-tagging runs "
                                + "without tag suggestions.")
        @Nullable
        Path model;

        @Option(names = {"-o", "--output"}, required = true, description = "XML output file; must be absent or empty.")
        @Nullable
        Path output;

        @Option(names = {"-t",
                        "--threshold"}, defaultValue = "0.80", description = "Confidence below which tokens are highlighted (in [0.0, 1.0]; "
                                + "default ${DEFAULT-VALUE}).")
        double threshold;
    }
}
