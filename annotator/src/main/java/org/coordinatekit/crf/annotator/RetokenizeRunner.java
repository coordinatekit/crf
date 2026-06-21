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

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Parser-free invocation of the retokenize flow.
 *
 * <p>
 * This is the peer of {@link AnnotatorRunner} for the retokenize flow: where
 * {@code AnnotatorRunner} drives {@link Annotator#annotate(Path, Path)}, this class drives
 * {@link RetokenizeReviewer#review(Path, Path)}. It is the home of the retokenize invocation logic,
 * separated from the picocli adapter {@link RetokenizeCli}. A caller that parses with its own
 * command-line framework builds a {@link RetokenizeConfiguration} and calls
 * {@link #run(RetokenizeConfiguration, ReviewerFactory)}; this class performs the
 * interactive-terminal precondition, the fresh-pass precondition, and the exit-code mapping, but
 * does no argument parsing and imports no command-line parser. The shared terminal glue lives in
 * {@link TerminalSupport}.
 *
 * <p>
 * The {@code reviewerFactory} wires the typed beans (tag provider, tokenizer, feature extractor,
 * optional CRF tagger) into a {@link RetokenizeReviewer}, wiring the <em>same tokenizer</em> into
 * both the reviewer and any {@link org.coordinatekit.crf.core.tag.CrfTagger CrfTagger}, as
 * {@link RetokenizeReviewer}'s contract requires.
 *
 * <p>
 * Exit codes:
 *
 * <ul>
 * <li>{@code 0} — review completed;</li>
 * <li>{@code 1} — interactive-terminal precondition failed, the terminal could not be opened, the
 * review's fresh-pass precondition was violated (input path equals output, or the output exists and
 * is non-empty), or {@link RetokenizeReviewer#review(Path, Path)} threw an
 * {@link IOException}.</li>
 * </ul>
 */
@NullMarked
public final class RetokenizeRunner {
    private RetokenizeRunner() {
        throw new UnsupportedOperationException("RetokenizeRunner is a utility class and cannot be instantiated");
    }

    /**
     * Opens an interactive system terminal and runs the reviewer produced by {@code factory}. Returns a
     * process exit code suitable for {@link System#exit(int)}.
     *
     * @param configuration the parser-free retokenize configuration
     * @param factory the factory that constructs the reviewer from the configuration and terminal
     * @return the process exit code
     */
    public static int run(RetokenizeConfiguration configuration, ReviewerFactory factory) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        Objects.requireNonNull(factory, "factory must not be null");

        PrintWriter err = new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8), true);
        return TerminalSupport.runInteractive(
                "retokenize",
                "Retokenize",
                () -> TerminalBuilder.builder().system(true).build(),
                err,
                terminal -> review(configuration, factory, terminal, err)
        );
    }

    /**
     * Runs the reviewer produced by {@code factory} against the caller-owned {@code terminal},
     * enforcing the interactive-terminal precondition. This is the testable seam behind
     * {@link #run(RetokenizeConfiguration, ReviewerFactory)}, which opens a system terminal; the
     * terminal is not closed by this method.
     *
     * @param configuration the parser-free retokenize configuration
     * @param factory the factory that constructs the reviewer from the configuration and terminal
     * @param terminal the JLine terminal to run against
     * @return the process exit code
     */
    static int run(RetokenizeConfiguration configuration, ReviewerFactory factory, Terminal terminal) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        Objects.requireNonNull(factory, "factory must not be null");
        Objects.requireNonNull(terminal, "terminal must not be null");

        PrintWriter err = new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8), true);
        return TerminalSupport.runInTerminal(
                "retokenize",
                "Retokenize",
                terminal,
                err,
                ownedTerminal -> review(configuration, factory, ownedTerminal, err)
        );
    }

    /**
     * Validates the fresh-pass precondition <em>before</em> the factory builds the reviewer, so a bad
     * path fails before a model is loaded. A {@link ReviewPreconditionException} (input equals output,
     * or the output exists and is non-empty) is reported to {@code err} and mapped to exit {@code 1};
     * any other unchecked exception — such as a tokenizer/tagger mismatch surfaced while writing —
     * propagates so it is not masked as a user error.
     */
    private static int review(
            RetokenizeConfiguration configuration,
            ReviewerFactory factory,
            Terminal terminal,
            PrintWriter err
    ) throws IOException {
        try {
            RetokenizeReviewer.validateFreshPass(configuration.input(), configuration.output());
            RetokenizeReviewer<?, ?> reviewer = factory.create(configuration, terminal);
            reviewer.review(configuration.input(), configuration.output());
            return 0;
        } catch (ReviewPreconditionException exception) {
            err.println("Retokenize failed: " + exception.getMessage());
            return 1;
        }
    }

    /**
     * Factory that wires the typed beans (tag provider, tokenizer, feature extractor, optional CRF
     * tagger) into a {@link RetokenizeReviewer}.
     */
    @FunctionalInterface
    public interface ReviewerFactory {
        /**
         * Constructs a reviewer from the configuration and the JLine terminal opened by the runner.
         *
         * @param configuration the parser-free retokenize configuration
         * @param terminal the JLine terminal to install on the reviewer's tagging interface; ownership
         *        remains with the runner
         * @return a configured reviewer ready to {@link RetokenizeReviewer#review(Path, Path) review}
         */
        RetokenizeReviewer<?, ?> create(RetokenizeConfiguration configuration, Terminal terminal);
    }
}
