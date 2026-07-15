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

import org.coordinatekit.crf.annotator.AnnotatorConfiguration;
import org.coordinatekit.crf.annotator.AnnotatorRunner;
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
 * The {@code annotate} subcommand: parses the standard flags into an {@link AnnotatorConfiguration}
 * and delegates to {@link AnnotatorRunner}.
 *
 * <p>
 * This command owns only argument parsing; picocli handles the {@code --help} / {@code --version}
 * short-circuit and maps a rejected argument list to exit {@code 2}. A bad threshold is translated
 * to the same exit {@code 2} by re-throwing the builder's validation as a
 * {@link ParameterException}. The domain services are resolved through
 * {@link ResolvedServicesFactory} before the runner opens a terminal, so a missing tag provider or
 * unreadable model fails fast with exit {@code 1}; the remaining exit codes and the
 * interactive-terminal precondition live in {@link AnnotatorRunner}.
 *
 * <p>
 * Exit codes:
 *
 * <ul>
 * <li>{@code 0} — annotation completed (or {@code --help} was requested);</li>
 * <li>{@code 1} — the services could not be resolved, the terminal could not be opened, or the
 * annotation run failed;</li>
 * <li>{@code 2} — picocli rejected the arguments (missing required flag, invalid threshold, unknown
 * option, …).</li>
 * </ul>
 */
@NullMarked
@Command(name = "annotate", mixinStandardHelpOptions = true, versionProvider = CrfVersionProvider.class, description = "Walk an input file line-by-line, tag each sequence via an interactive "
        + "prompt, and append accepted sequences to an XML training-data file.")
final class AnnotatorCommand implements Callable<Integer> {
    private final ResolvedServices.Builder servicesBuilder;

    @Option(names = "--feature-configuration", description = "Feature-configuration file (for example features.xml) "
            + "that declares the extractors to use. Optional; overrides any registered feature extractor.")
    @Nullable
    Path featureConfiguration;

    @Option(names = {"-i",
                    "--input"}, required = true, description = "Plain-text input file (UTF-8), one sequence per line.")
    @Nullable
    Path input;

    @Option(names = {"-m", "--model"}, description = "Path to a serialized model. Optional; if absent the annotator "
            + "runs without tag suggestions.")
    @Nullable
    Path model;

    @Option(names = {"-o", "--output"}, required = true, description = "XML output file; created or appended.")
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
                    "--threshold"}, defaultValue = AnnotatorConfiguration.DEFAULT_THRESHOLD_TEXT, description = "Confidence below which tokens are highlighted (in [0.0, 1.0]; "
                            + "default ${DEFAULT-VALUE}).")
    double threshold;

    AnnotatorCommand(ResolvedServices.Builder servicesBuilder) {
        this.servicesBuilder = servicesBuilder;
    }

    @Override
    public Integer call() {
        AnnotatorConfiguration configuration = configuration();
        AnnotatorRunner.AnnotatorFactory factory;
        try {
            ResolvedServicesFactory.applyFeatureConfiguration(servicesBuilder, featureConfiguration);
            servicesBuilder.taggerLoaderName(taggerLoader);
            factory = ResolvedServicesFactory.annotatorFactory(servicesBuilder, model);
        } catch (CrfStartupException exception) {
            commandLine().getErr().println(exception.getMessage());
            return 1;
        }
        return AnnotatorRunner.run(configuration, factory);
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
    AnnotatorConfiguration configuration() {
        try {
            return AnnotatorConfiguration.builder()
                    .input(Objects.requireNonNull(input, "input must not be null after parsing")).model(model)
                    .output(Objects.requireNonNull(output, "output must not be null after parsing"))
                    .threshold(threshold).build();
        } catch (IllegalArgumentException exception) {
            throw new ParameterException(commandLine(), exception.getMessage());
        }
    }
}
