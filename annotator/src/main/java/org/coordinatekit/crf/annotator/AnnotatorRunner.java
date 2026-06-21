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
 * Parser-free invocation of the annotate flow.
 *
 * <p>
 * This is the home of the annotate invocation logic, separated from the picocli command
 * {@code AnnotatorCommand} (in the {@code cli} module). A caller that parses with its own
 * command-line framework builds an {@link AnnotatorConfiguration} and calls
 * {@link #run(AnnotatorConfiguration, AnnotatorFactory)}; this class performs the
 * interactive-terminal precondition and maps exit codes, but does no argument parsing and imports
 * no command-line parser. The shared terminal glue lives in {@link TerminalSupport}.
 *
 * <p>
 * The interactive-terminal precondition rejects JLine "dumb" terminal types, which JLine returns
 * when stdin/stdout are not attached to a real TTY — as happens under CI scripts, piped input, or
 * {@code nohup}-style backgrounding. A non-interactive context is treated as a hard failure rather
 * than a silent write of garbage to the output XML.
 *
 * <p>
 * Exit codes:
 *
 * <ul>
 * <li>{@code 0} — annotation completed;</li>
 * <li>{@code 1} — interactive-terminal precondition failed, the terminal could not be opened, or
 * {@link Annotator#annotate(Path, Path)} threw an {@link IOException}.</li>
 * </ul>
 */
@NullMarked
public final class AnnotatorRunner {
    private AnnotatorRunner() {
        throw new UnsupportedOperationException("AnnotatorRunner is a utility class and cannot be instantiated");
    }

    /**
     * Opens an interactive system terminal and runs the annotator produced by {@code factory}. Returns
     * a process exit code suitable for {@link System#exit(int)}.
     *
     * @param configuration the parser-free annotate configuration
     * @param factory the factory that constructs the annotator from the configuration and terminal
     * @return the process exit code
     */
    public static int run(AnnotatorConfiguration configuration, AnnotatorFactory factory) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        Objects.requireNonNull(factory, "factory must not be null");

        PrintWriter err = new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8), true);
        return TerminalSupport.runInteractive(
                "annotator",
                "Annotation",
                () -> TerminalBuilder.builder().system(true).build(),
                err,
                terminal -> annotate(configuration, factory, terminal)
        );
    }

    /**
     * Runs the annotator produced by {@code factory} against the caller-owned {@code terminal},
     * enforcing the interactive-terminal precondition. This is the testable seam behind
     * {@link #run(AnnotatorConfiguration, AnnotatorFactory)}, which opens a system terminal; the
     * terminal is not closed by this method.
     *
     * @param configuration the parser-free annotate configuration
     * @param factory the factory that constructs the annotator from the configuration and terminal
     * @param terminal the JLine terminal to run against
     * @return the process exit code
     */
    static int run(AnnotatorConfiguration configuration, AnnotatorFactory factory, Terminal terminal) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        Objects.requireNonNull(factory, "factory must not be null");
        Objects.requireNonNull(terminal, "terminal must not be null");

        PrintWriter err = new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8), true);
        return TerminalSupport.runInTerminal(
                "annotator",
                "Annotation",
                terminal,
                err,
                ownedTerminal -> annotate(configuration, factory, ownedTerminal)
        );
    }

    private static int annotate(AnnotatorConfiguration configuration, AnnotatorFactory factory, Terminal terminal)
            throws IOException {
        Annotator<?, ?> annotator = factory.create(configuration, terminal);
        annotator.annotate(configuration.input(), configuration.output());
        return 0;
    }

    /**
     * Factory that wires the typed beans (tag provider, tokenizer, feature extractor, optional CRF
     * tagger) into an {@link Annotator}.
     */
    @FunctionalInterface
    public interface AnnotatorFactory {
        /**
         * Constructs an annotator from the configuration and the JLine terminal opened by the runner.
         *
         * @param configuration the parser-free annotate configuration
         * @param terminal the JLine terminal to install on the annotator's tagging interface; ownership
         *        remains with the runner
         * @return a configured annotator ready to {@link Annotator#annotate(Path, Path) annotate}
         */
        Annotator<?, ?> create(AnnotatorConfiguration configuration, Terminal terminal);
    }
}
