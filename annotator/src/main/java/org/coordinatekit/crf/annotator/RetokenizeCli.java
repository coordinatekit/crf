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
 * Picocli helper that parses the standard retokenize flags ({@code --input}, {@code --output},
 * {@code --model}, {@code --threshold}) and hands the parsed options plus a system {@link Terminal}
 * to a user-supplied {@link ReviewerFactory}.
 *
 * <p>
 * This is the peer of {@link AnnotatorCli} for the retokenize flow: where {@code AnnotatorCli}
 * drives {@link Annotator#annotate(Path, Path)}, this helper drives
 * {@link RetokenizeReviewer#review(Path, Path)}. The two are equal, flat CLI helpers; the library
 * does not own a top-level router. A downstream {@code main} owns the nesting, routing
 * {@code args[0]} to one helper or the other (or shipping two binaries).
 *
 * <p>
 * Example downstream {@code main} that owns the nesting:
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
 * The {@code reviewerFactory} wires the typed beans (tag provider, tokenizer, feature extractor,
 * optional CRF tagger) into a {@link RetokenizeReviewer}, wiring the <em>same tokenizer</em> into
 * both the reviewer and any {@link org.coordinatekit.crf.core.tag.CrfTagger CrfTagger}, as
 * {@link RetokenizeReviewer}'s contract requires. The CLI handles argument parsing, help output,
 * the interactive-terminal precondition, and exit codes; the factory only has to construct the
 * reviewer.
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
        if (CliSupport.helpOrVersionRequested(commandLine, out)) {
            return null;
        }
        CliSupport.validateThreshold(commandLine, parsed.threshold);
        return new DefaultOptions(
                Objects.requireNonNull(parsed.input, "input must not be null after parseArgs"),
                parsed.model,
                Objects.requireNonNull(parsed.output, "output must not be null after parseArgs"),
                parsed.threshold
        );
    }

    /**
     * Invokes {@code factory} with the supplied {@code options} and {@code terminal} and runs the
     * resulting reviewer. The terminal is not closed by this method; the caller owns its lifecycle.
     *
     * <p>
     * The fresh-pass precondition is checked <em>before</em> the factory builds the reviewer, so a bad
     * path fails before a model is loaded. A {@link ReviewPreconditionException} (input equals output,
     * or the output exists and is non-empty) is reported to {@code err} and mapped to exit {@code 1};
     * any other unchecked exception — such as a tokenizer/tagger mismatch surfaced while writing —
     * propagates so it is not masked as a user error.
     *
     * @param options the parsed CLI options
     * @param factory the factory that constructs the reviewer from options and terminal
     * @param terminal the JLine terminal to hand to the factory
     * @param err the writer for diagnostic output
     * @return {@code 0} on success, {@code 1} if a fresh-pass precondition was violated
     * @throws IOException if {@link RetokenizeReviewer#review(Path, Path)} fails
     */
    static int run(Options options, ReviewerFactory factory, Terminal terminal, PrintWriter err) throws IOException {
        try {
            RetokenizeReviewer.validateFreshPass(options.input(), options.output());
            RetokenizeReviewer<?, ?> reviewer = factory.create(options, terminal);
            reviewer.review(options.input(), options.output());
            return 0;
        } catch (ReviewPreconditionException exception) {
            err.println("Retokenize failed: " + exception.getMessage());
            return 1;
        }
    }

    /**
     * Parses {@code arguments}, opens an interactive system terminal, and runs the reviewer produced by
     * {@code factory}. Returns a process exit code suitable for {@link System#exit(int)}.
     *
     * <p>
     * The interactive-terminal precondition rejects JLine "dumb" terminal types, which JLine returns
     * when stdin/stdout are not attached to a real TTY — as happens under CI scripts, piped input, or
     * {@code nohup}-style backgrounding. A non-interactive context is treated as a hard failure rather
     * than a silent write of garbage to the output XML.
     *
     * @param arguments the raw command-line arguments
     * @param factory the factory that constructs the reviewer from options and terminal
     * @return the process exit code
     */
    public static int run(String[] arguments, ReviewerFactory factory) {
        return run(arguments, factory, () -> TerminalBuilder.builder().system(true).build());
    }

    /**
     * Parses {@code arguments}, opens a terminal via {@code terminalSupplier}, and runs the reviewer
     * produced by {@code factory}. This is the testable seam behind
     * {@link #run(String[], ReviewerFactory)}, which supplies a system terminal.
     *
     * @param arguments the raw command-line arguments
     * @param factory the factory that constructs the reviewer from options and terminal
     * @param terminalSupplier supplies the JLine terminal to run against
     * @return the process exit code
     */
    static int run(String[] arguments, ReviewerFactory factory, CliSupport.TerminalSupplier terminalSupplier) {
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

        return CliSupport.runInteractive(
                "retokenize",
                "Retokenize",
                terminalSupplier,
                err,
                terminal -> run(options, factory, terminal, err)
        );
    }

    /**
     * Factory the downstream {@code main} supplies to wire its typed beans (tag provider, tokenizer,
     * feature extractor, optional CRF tagger) into a {@link RetokenizeReviewer}.
     */
    @FunctionalInterface
    public interface ReviewerFactory {
        /**
         * Constructs a reviewer from the parsed CLI options and the JLine terminal opened by the CLI.
         *
         * @param options the parsed CLI options
         * @param terminal the JLine terminal to install on the reviewer's tagging interface; ownership
         *        remains with the CLI
         * @return a configured reviewer ready to {@link RetokenizeReviewer#review(Path, Path) review}
         */
        RetokenizeReviewer<?, ?> create(Options options, Terminal terminal);
    }

    /** Parsed CLI options handed to the downstream {@link ReviewerFactory}. */
    public interface Options {
        /**
         * Returns the path to the XML training-data file to review.
         *
         * @return the input file path
         */
        Path input();

        /**
         * Returns the path to a serialized model, or {@code null} if {@code --model} was not supplied. The
         * downstream factory decides how to materialize a {@link org.coordinatekit.crf.core.tag.CrfTagger
         * CrfTagger} from this path; the threshold drives low-confidence highlighting only when a model is
         * supplied.
         *
         * @return the model path, or {@code null} if no model was supplied
         */
        @Nullable
        Path model();

        /**
         * Returns the path to the XML output file; must be absent or empty, and must differ from the input.
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

    private record DefaultOptions(Path input, @Nullable Path model, Path output, double threshold) implements Options {}

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
