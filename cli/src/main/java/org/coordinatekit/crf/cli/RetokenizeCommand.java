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

import org.coordinatekit.crf.annotator.RetokenizeConfiguration;
import org.coordinatekit.crf.annotator.RetokenizeRunner;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/**
 * The {@code retokenize} subcommand: parses the standard flags into a
 * {@link RetokenizeConfiguration} and delegates to {@link RetokenizeRunner}.
 *
 * <p>
 * The peer of {@link AnnotatorCommand} for the retokenize flow. It owns only argument parsing;
 * picocli handles the {@code --help} / {@code --version} short-circuit and maps a rejected argument
 * list to exit {@code 2}, and a bad threshold is translated to the same exit {@code 2}. The domain
 * services are resolved through {@link ResolvedServicesFactory} before the runner opens a terminal;
 * the interactive-terminal precondition, the fresh-pass precondition, and the remaining exit codes
 * live in {@link RetokenizeRunner}.
 *
 * <p>
 * Exit codes:
 *
 * <ul>
 * <li>{@code 0} — review completed (or {@code --help} was requested);</li>
 * <li>{@code 1} — the services could not be resolved, the terminal could not be opened, the
 * fresh-pass precondition was violated, or the review failed;</li>
 * <li>{@code 2} — picocli rejected the arguments (missing required flag, invalid threshold, unknown
 * option, …).</li>
 * </ul>
 */
@NullMarked
@Command(name = "retokenize", mixinStandardHelpOptions = true, versionProvider = CrfVersionProvider.class, description = "Walk an XML training-data file, re-tokenize each sequence with the new "
        + "tokenizer, re-tag misaligned sequences via an interactive prompt, and write a corrected XML file.")
final class RetokenizeCommand implements Callable<Integer> {
    private final ResolvedServices.Builder servicesBuilder;

    @Option(names = {"-i", "--input"}, required = true, description = "XML training-data file to review.")
    @Nullable
    Path input;

    @Option(names = {"-m", "--model"}, description = "Path to a serialized model. Optional; if absent re-tagging runs "
            + "without tag suggestions.")
    @Nullable
    Path model;

    @Option(names = {"-o", "--output"}, required = true, description = "XML output file; must be absent or empty.")
    @Nullable
    Path output;

    @Spec
    @Nullable
    CommandSpec spec;

    @Option(names = "--tagger-loader", description = "Name of the model loader to select when more than one is on "
            + "the classpath (for example \"mallet\"). Optional; a single registered loader is selected automatically.")
    @Nullable
    String taggerLoader;

    @Option(names = {"-t",
                    "--threshold"}, defaultValue = RetokenizeConfiguration.DEFAULT_THRESHOLD_TEXT, description = "Confidence below which tokens are highlighted (in [0.0, 1.0]; "
                            + "default ${DEFAULT-VALUE}).")
    double threshold;

    RetokenizeCommand(ResolvedServices.Builder servicesBuilder) {
        this.servicesBuilder = servicesBuilder;
    }

    @Override
    public Integer call() {
        RetokenizeConfiguration configuration = configuration();
        RetokenizeRunner.ReviewerFactory factory;
        try {
            servicesBuilder.taggerLoaderName(taggerLoader);
            factory = ResolvedServicesFactory.reviewerFactory(servicesBuilder, model);
        } catch (CrfStartupException exception) {
            commandLine().getErr().println(exception.getMessage());
            return 1;
        }
        return RetokenizeRunner.run(configuration, factory);
    }

    private CommandLine commandLine() {
        return Objects.requireNonNull(spec, "spec must be injected by picocli").commandLine();
    }

    /**
     * Builds the configuration from the parsed flags. Package-private so the parsing seam can be
     * asserted directly.
     *
     * @return the parsed configuration
     * @throws ParameterException if the threshold is outside {@code [0.0, 1.0]}
     */
    RetokenizeConfiguration configuration() {
        try {
            return RetokenizeConfiguration.builder()
                    .input(Objects.requireNonNull(input, "input must not be null after parsing")).model(model)
                    .output(Objects.requireNonNull(output, "output must not be null after parsing"))
                    .threshold(threshold).build();
        } catch (IllegalArgumentException exception) {
            throw new ParameterException(commandLine(), exception.getMessage());
        }
    }
}
