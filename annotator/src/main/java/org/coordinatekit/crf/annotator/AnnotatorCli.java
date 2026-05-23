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
import org.jline.terminal.TerminalBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

/**
 * Picocli helper that parses the standard annotator flags ({@code --input}, {@code --output},
 * {@code --model}, {@code --threshold}) and hands the parsed options plus a system {@link Terminal}
 * to a user-supplied {@link AnnotatorFactory}.
 *
 * <p>
 * Downstream consumers write a small {@code main} that delegates to
 * {@link #run(String[], AnnotatorFactory)}; the factory wires the typed beans (tag provider,
 * tokenizer, feature extractor, optional CRF tagger) into an {@link Annotator}. The CLI handles
 * argument parsing, help output, the interactive-terminal precondition, and exit codes; the factory
 * only has to construct the annotator.
 *
 * <p>
 * Example downstream {@code main}:
 *
 * <pre>
 * {@code
 * public static void main(String[] arguments) {
 *     int exitCode = AnnotatorCli.run(arguments, (options, terminal) -> {
 *         JLineTaggingInterface<MyFeature, MyTag> ui =
 *                 JLineTaggingInterface.<MyFeature, MyTag>builder()
 *                         .tagProvider(new MyTagProvider())
 *                         .terminal(terminal)
 *                         .threshold(options.threshold())
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

    /**
     * Parses {@code arguments} via picocli, validates the threshold range, and returns the populated
     * {@link Options}. Returns {@code null} if {@code --help} was requested (in which case usage was
     * printed to {@code out}).
     *
     * @param arguments the raw command-line arguments
     * @param out the writer for help / usage output
     * @param err the writer picocli uses for diagnostic output during parsing
     * @return the parsed options, or {@code null} if {@code --help} was requested
     * @throws ParameterException if parsing fails (missing required flag, threshold out of range,
     *         unknown option, …)
     */
    static @Nullable Options parseArguments(String[] arguments, PrintWriter out, PrintWriter err) {
        ParsedArguments parsed = new ParsedArguments();
        CommandLine commandLine = new CommandLine(parsed);
        commandLine.setOut(out);
        commandLine.setErr(err);
        commandLine.parseArgs(arguments);
        if (commandLine.isUsageHelpRequested()) {
            commandLine.usage(out);
            return null;
        }
        if (commandLine.isVersionHelpRequested()) {
            commandLine.printVersionHelp(out);
            return null;
        }
        if (Double.isNaN(parsed.threshold) || parsed.threshold < 0.0 || parsed.threshold > 1.0) {
            throw new ParameterException(commandLine, "threshold must be in [0.0, 1.0], got: " + parsed.threshold);
        }
        return new DefaultOptions(
                Objects.requireNonNull(parsed.input, "input must not be null after parseArgs"),
                parsed.model,
                Objects.requireNonNull(parsed.output, "output must not be null after parseArgs"),
                parsed.threshold
        );
    }

    /**
     * Invokes {@code factory} with the supplied {@code options} and {@code terminal} and runs the
     * resulting annotator. The terminal is not closed by this method; the caller owns its lifecycle.
     *
     * @param options the parsed CLI options
     * @param factory the factory that constructs the annotator from options and terminal
     * @param terminal the JLine terminal to hand to the factory
     * @return {@code 0} on success
     * @throws IOException if {@link Annotator#annotate(Path, Path)} fails
     */
    static int run(Options options, AnnotatorFactory factory, Terminal terminal) throws IOException {
        Annotator<?, ?> annotator = factory.create(options, terminal);
        annotator.annotate(options.input(), options.output());
        return 0;
    }

    /**
     * Parses {@code arguments}, opens an interactive system terminal, and runs the annotator produced
     * by {@code factory}. Returns a process exit code suitable for {@link System#exit(int)}.
     *
     * <p>
     * The interactive-terminal precondition rejects JLine "dumb" terminal types, which JLine returns
     * when stdin/stdout are not attached to a real TTY — as happens under CI scripts, piped input, or
     * {@code nohup}-style backgrounding. A non-interactive context is treated as a hard failure rather
     * than a silent write of garbage to the output XML.
     *
     * @param arguments the raw command-line arguments
     * @param factory the factory that constructs the annotator from options and terminal
     * @return the process exit code
     */
    public static int run(String[] arguments, AnnotatorFactory factory) {
        return run(arguments, factory, () -> TerminalBuilder.builder().system(true).build());
    }

    /**
     * Parses {@code arguments}, opens a terminal via {@code terminalSupplier}, and runs the annotator
     * produced by {@code factory}. This is the testable seam behind
     * {@link #run(String[], AnnotatorFactory)}, which supplies a system terminal.
     *
     * @param arguments the raw command-line arguments
     * @param factory the factory that constructs the annotator from options and terminal
     * @param terminalSupplier supplies the JLine terminal to run against
     * @return the process exit code
     */
    static int run(String[] arguments, AnnotatorFactory factory, TerminalSupplier terminalSupplier) {
        Objects.requireNonNull(arguments, "arguments must not be null");
        Objects.requireNonNull(factory, "factory must not be null");
        Objects.requireNonNull(terminalSupplier, "terminalSupplier must not be null");

        PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        PrintWriter err = new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8), true);

        Options options;
        try {
            options = parseArguments(arguments, out, err);
        } catch (ParameterException exception) {
            err.println(exception.getMessage());
            exception.getCommandLine().usage(err);
            return 2;
        }
        if (options == null) {
            return 0;
        }

        Terminal terminal;
        try {
            terminal = terminalSupplier.get();
        } catch (IOException exception) {
            err.println("Failed to open terminal: " + exception.getMessage());
            return 1;
        }

        try (terminal) {
            String type = terminal.getType();
            if (Terminal.TYPE_DUMB.equals(type) || Terminal.TYPE_DUMB_COLOR.equals(type)) {
                err.println("annotator requires an interactive terminal; got terminal type: " + type);
                return 1;
            }
            return run(options, factory, terminal);
        } catch (IOException exception) {
            err.println("Annotation failed: " + exception.getMessage());
            return 1;
        }
    }

    /**
     * Factory the downstream {@code main} supplies to wire its typed beans (tag provider, tokenizer,
     * feature extractor, optional CRF tagger) into an {@link Annotator}.
     */
    @FunctionalInterface
    public interface AnnotatorFactory {
        /**
         * Constructs an annotator from the parsed CLI options and the JLine terminal opened by the CLI.
         *
         * @param options the parsed CLI options
         * @param terminal the JLine terminal to install on the annotator's tagging interface; ownership
         *        remains with the CLI
         * @return a configured annotator ready to {@link Annotator#annotate(Path, Path) annotate}
         */
        Annotator<?, ?> create(Options options, Terminal terminal);
    }

    /** Parsed CLI options handed to the downstream {@link AnnotatorFactory}. */
    public interface Options {
        /**
         * Returns the path to the plain-text input file (UTF-8), one sequence per line.
         *
         * @return the input file path
         */
        Path input();

        /**
         * Returns the path to a serialized model, or {@code null} if {@code --model} was not supplied. The
         * downstream factory decides how to materialize a {@link org.coordinatekit.crf.core.tag.CrfTagger
         * CrfTagger} from this path.
         *
         * @return the model path, or {@code null} if no model was supplied
         */
        @Nullable
        Path model();

        /**
         * Returns the path to the XML output file; created or appended.
         *
         * @return the output file path
         */
        Path output();

        /**
         * Returns the confidence threshold below which token rows are highlighted on the sequence screen.
         *
         * @return the threshold, in the closed interval {@code [0.0, 1.0]}
         */
        double threshold();
    }

    /**
     * Supplies the JLine {@link Terminal} that
     * {@link #run(String[], AnnotatorFactory, TerminalSupplier)} runs against.
     */
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

    private record DefaultOptions(Path input, @Nullable Path model, Path output, double threshold) implements Options {}

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
