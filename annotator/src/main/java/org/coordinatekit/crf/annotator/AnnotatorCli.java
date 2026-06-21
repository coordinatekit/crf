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
 * Picocli adapter that parses the standard annotator flags ({@code --input}, {@code --output},
 * {@code --model}, {@code --threshold}) into an {@link AnnotatorConfiguration} and delegates to
 * {@link AnnotatorRunner}.
 *
 * <p>
 * This class owns only argument parsing, the {@code --help} / {@code --version} short-circuit, and
 * the exit-2 mapping for bad arguments; the interactive-terminal precondition, execution, and the
 * remaining exit codes live in {@link AnnotatorRunner}. A caller using a different command-line
 * framework can skip this adapter, build an {@link AnnotatorConfiguration} directly, and call
 * {@link AnnotatorRunner#run(AnnotatorConfiguration, AnnotatorRunner.AnnotatorFactory)}.
 *
 * <p>
 * Example downstream {@code main}:
 *
 * <pre>
 * {@code
 * public static void main(String[] arguments) {
 *     int exitCode = AnnotatorCli.run(arguments, (configuration, terminal) -> {
 *         TerminalTaggingInterface<MyFeature, MyTag> ui =
 *                 TerminalTaggingInterface.<MyFeature, MyTag>builder()
 *                         .tagProvider(new MyTagProvider())
 *                         .terminal(terminal)
 *                         .threshold(configuration.threshold())
 *                         .build();
 *         return Annotator.<MyFeature, MyTag>builder()
 *                 .tagProvider(new MyTagProvider())
 *                 .tokenizer(new WhitespaceTokenizer())
 *                 .taggingInterface(ui)
 *                 .terminal(terminal)
 *                 .build();
 *     });
 *     System.exit(exitCode);
 * }
 * }
 * </pre>
 *
 * <p>
 * Exit codes:
 *
 * <ul>
 * <li>{@code 0} — annotation completed (or {@code --help} was requested);</li>
 * <li>{@code 1} — interactive-terminal precondition failed, the terminal could not be opened, or
 * {@link Annotator#annotate(Path, Path)} threw an {@link IOException};</li>
 * <li>{@code 2} — picocli rejected the arguments (missing required flag, invalid threshold, unknown
 * option, …).</li>
 * </ul>
 */
@NullMarked
public final class AnnotatorCli {
    private AnnotatorCli() {
        throw new UnsupportedOperationException("AnnotatorCli is a utility class and cannot be instantiated");
    }

    private static int parseAndRun(String[] arguments, ToIntFunction<AnnotatorConfiguration> dispatch) {
        PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        PrintWriter err = new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8), true);

        AnnotatorConfiguration configuration;
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
     * Parses {@code arguments} via picocli and returns the populated {@link AnnotatorConfiguration}.
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
    static @Nullable AnnotatorConfiguration parseArguments(String[] arguments, PrintWriter out, PrintWriter err) {
        ParsedArguments parsed = new ParsedArguments();
        CommandLine commandLine = new CommandLine(parsed);
        commandLine.setOut(out);
        commandLine.setErr(err);
        commandLine.parseArgs(arguments);
        if (PicocliSupport.helpOrVersionRequested(commandLine, out)) {
            return null;
        }
        try {
            return AnnotatorConfiguration.builder()
                    .input(Objects.requireNonNull(parsed.input, "input must not be null after parseArgs"))
                    .model(parsed.model)
                    .output(Objects.requireNonNull(parsed.output, "output must not be null after parseArgs"))
                    .threshold(parsed.threshold).build();
        } catch (IllegalArgumentException exception) {
            throw new ParameterException(commandLine, exception.getMessage());
        }
    }

    /**
     * Parses {@code arguments}, builds an {@link AnnotatorConfiguration}, and delegates to
     * {@link AnnotatorRunner#run(AnnotatorConfiguration, AnnotatorRunner.AnnotatorFactory)}. Returns a
     * process exit code suitable for {@link System#exit(int)}.
     *
     * @param arguments the raw command-line arguments
     * @param factory the factory that constructs the annotator from the configuration and terminal
     * @return the process exit code
     */
    public static int run(String[] arguments, AnnotatorRunner.AnnotatorFactory factory) {
        Objects.requireNonNull(arguments, "arguments must not be null");
        Objects.requireNonNull(factory, "factory must not be null");
        return parseAndRun(arguments, configuration -> AnnotatorRunner.run(configuration, factory));
    }

    /**
     * Parses {@code arguments}, builds an {@link AnnotatorConfiguration}, and delegates to
     * {@link AnnotatorRunner#run(AnnotatorConfiguration, AnnotatorRunner.AnnotatorFactory, Terminal)}
     * against the caller-owned {@code terminal}. This is the testable seam behind
     * {@link #run(String[], AnnotatorRunner.AnnotatorFactory)}, which opens a system terminal; the
     * terminal is not closed by this method.
     *
     * @param arguments the raw command-line arguments
     * @param factory the factory that constructs the annotator from the configuration and terminal
     * @param terminal the JLine terminal to run against
     * @return the process exit code
     */
    static int run(String[] arguments, AnnotatorRunner.AnnotatorFactory factory, Terminal terminal) {
        Objects.requireNonNull(arguments, "arguments must not be null");
        Objects.requireNonNull(factory, "factory must not be null");
        Objects.requireNonNull(terminal, "terminal must not be null");
        return parseAndRun(arguments, configuration -> AnnotatorRunner.run(configuration, factory, terminal));
    }

    @Command(name = "annotator", mixinStandardHelpOptions = true, description = "Walk an input file line-by-line, tag each sequence via an interactive "
            + "prompt, and append accepted sequences to an XML training-data file.")
    private static final class ParsedArguments {
        @Option(names = {"-i",
                        "--input"}, required = true, description = "Plain-text input file (UTF-8), one sequence per line.")
        @Nullable
        Path input;

        @Option(names = {"-m",
                        "--model"}, description = "Path to a serialized model. Optional; if absent the annotator "
                                + "runs without tag suggestions.")
        @Nullable
        Path model;

        @Option(names = {"-o", "--output"}, required = true, description = "XML output file; created or appended.")
        @Nullable
        Path output;

        @Option(names = {"-t",
                        "--threshold"}, defaultValue = "0.80", description = "Confidence below which tokens are highlighted (in [0.0, 1.0]; "
                                + "default ${DEFAULT-VALUE}).")
        double threshold;
    }
}
