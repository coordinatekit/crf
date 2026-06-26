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

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Parser-free inputs for {@link AnnotatorRunner}.
 *
 * <p>
 * This immutable, builder-driven class carries the data an annotation run needs — the input file,
 * the optional model, the output file, and the highlighting threshold — without any dependency on a
 * particular command-line parser. The {@code cli} module's {@code AnnotatorCommand} builds one from
 * picocli-parsed arguments, but any other CLI framework can build one directly and hand it to
 * {@link AnnotatorRunner#run(AnnotatorConfiguration, AnnotatorRunner.AnnotatorFactory)}.
 *
 * <p>
 * Example usage:
 *
 * <pre>
 * <code>
 * AnnotatorConfiguration configuration = AnnotatorConfiguration.builder()
 *         .input(Path.of("lines.txt"))
 *         .output(Path.of("labeled.xml"))
 *         .threshold(0.90)
 *         .build();
 * </code>
 * </pre>
 *
 * @see AnnotatorRunner
 * @see Builder
 */
@NullMarked
public final class AnnotatorConfiguration {
    /** The default highlighting threshold used when none is set on the {@link Builder}. */
    public static final double DEFAULT_THRESHOLD = 0.80;

    /**
     * The {@link #DEFAULT_THRESHOLD} as a compile-time {@code String} for picocli's
     * {@code @Option(defaultValue = ...)}, which requires a constant expression. Kept in agreement with
     * {@link #DEFAULT_THRESHOLD} by {@code AnnotatorCommandTest.configuration__defaults}.
     */
    public static final String DEFAULT_THRESHOLD_TEXT = "0.80";

    private final Path input;
    private final @Nullable Path model;
    private final Path output;
    private final double threshold;

    private AnnotatorConfiguration(Builder builder) {
        this.input = Objects.requireNonNull(builder.input);
        this.model = builder.model;
        this.output = Objects.requireNonNull(builder.output);
        this.threshold = builder.threshold;
    }

    /**
     * Returns a new {@link Builder} instance for constructing a configuration.
     *
     * @return a new builder with default values
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the path to the plain-text input file (UTF-8), one sequence per line.
     *
     * @return the input file path
     */
    public Path input() {
        return input;
    }

    /**
     * Returns the path to a serialized model, or {@code null} if none was supplied. The factory decides
     * how to materialize a {@link org.coordinatekit.crf.core.tag.CrfTagger CrfTagger} from this path.
     *
     * @return the model path, or {@code null} if no model was supplied
     */
    public @Nullable Path model() {
        return model;
    }

    /**
     * Returns the path to the XML output file; created or appended.
     *
     * @return the output file path
     */
    public Path output() {
        return output;
    }

    /**
     * Returns the confidence threshold below which token rows are highlighted on the sequence screen.
     *
     * @return the threshold, in the closed interval {@code [0.0, 1.0]}
     */
    public double threshold() {
        return threshold;
    }

    /**
     * Builder for constructing {@link AnnotatorConfiguration} instances.
     *
     * <p>
     * {@link #input(Path)} and {@link #output(Path)} are required; the model is optional and the
     * threshold defaults to {@link #DEFAULT_THRESHOLD}.
     */
    public static final class Builder {
        private @Nullable Path input;
        private @Nullable Path model;
        private @Nullable Path output;
        private double threshold = DEFAULT_THRESHOLD;

        private Builder() {}

        /**
         * Builds the configuration with the current settings.
         *
         * @return an immutable configuration instance
         * @throws IllegalStateException if the input or output path was not set
         */
        public AnnotatorConfiguration build() {
            if (input == null) {
                throw new IllegalStateException("input must be set");
            }
            if (output == null) {
                throw new IllegalStateException("output must be set");
            }
            return new AnnotatorConfiguration(this);
        }

        /**
         * Sets the path to the plain-text input file (UTF-8), one sequence per line.
         *
         * @param input the input file path
         * @return this builder
         */
        public Builder input(Path input) {
            this.input = input;
            return this;
        }

        /**
         * Sets the path to a serialized model. Optional; if absent the annotator runs without tag
         * suggestions.
         *
         * @param model the model path, or {@code null} if no model is supplied
         * @return this builder
         */
        public Builder model(@Nullable Path model) {
            this.model = model;
            return this;
        }

        /**
         * Sets the path to the XML output file; created or appended.
         *
         * @param output the output file path
         * @return this builder
         */
        public Builder output(Path output) {
            this.output = output;
            return this;
        }

        /**
         * Sets the confidence threshold below which token rows are highlighted.
         *
         * @param threshold the threshold, in the closed interval {@code [0.0, 1.0]}
         * @return this builder
         * @throws IllegalArgumentException if the threshold is {@code NaN} or outside {@code [0.0, 1.0]}
         */
        public Builder threshold(double threshold) {
            if (Double.isNaN(threshold) || threshold < 0.0 || threshold > 1.0) {
                throw new IllegalArgumentException("threshold must be in [0.0, 1.0], got: " + threshold);
            }
            this.threshold = threshold;
            return this;
        }
    }
}
