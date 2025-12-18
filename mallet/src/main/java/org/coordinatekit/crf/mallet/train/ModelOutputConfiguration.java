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
package org.coordinatekit.crf.mallet.train;

import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Configuration settings for {@link ModelOutputEvaluator}.
 *
 * <p>
 * This immutable class encapsulates all configurable parameters for the model output evaluator. Use
 * the {@link Builder} to construct instances.
 *
 * <p>
 * Example usage:
 *
 * <pre>
 * {
 *     &#64;code
 *     ModelOutputConfiguration config = ModelOutputConfiguration.builder().outputDirectory(Path.of("models"))
 *             .filePrefix("crf_model").fileSuffix("ser").iterationInterval(10).build();
 * }
 * </pre>
 *
 * @see ModelOutputEvaluator
 * @see Builder
 */
@NullMarked
public final class ModelOutputConfiguration {
    private static final ModelOutputConfiguration DEFAULTS = builder().build();

    private final String filePrefix;
    private final String fileSuffix;
    private final int iterationInterval;
    private final Path outputDirectory;

    private ModelOutputConfiguration(Builder builder) {
        this.outputDirectory = builder.outputDirectory;
        this.filePrefix = builder.filePrefix;
        this.fileSuffix = builder.fileSuffix;
        this.iterationInterval = builder.iterationInterval;
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
     * Returns a configuration with all default values.
     *
     * <p>
     * This is equivalent to calling {@code ModelOutputConfiguration.builder().build()}.
     *
     * @return a configuration with default settings
     */
    public static ModelOutputConfiguration defaults() {
        return DEFAULTS;
    }

    /**
     * Returns the prefix for output file names.
     *
     * <p>
     * Output files are named as {@code {prefix}_iter{N}.{suffix}}. Default is "model".
     *
     * @return the file prefix
     */
    public String filePrefix() {
        return filePrefix;
    }

    /**
     * Returns the suffix (extension) for output file names.
     *
     * <p>
     * Output files are named as {@code {prefix}_iter{N}.{suffix}}. Default is "ser".
     *
     * @return the file suffix
     */
    public String fileSuffix() {
        return fileSuffix;
    }

    /**
     * Returns the iteration interval for writing model files.
     *
     * <p>
     * The model is written every N iterations. For example, if set to 10, files are written at
     * iterations 10, 20, 30, etc. Default is 10.
     *
     * @return the iteration interval
     */
    public int iterationInterval() {
        return iterationInterval;
    }

    /**
     * Returns the directory in which to write model files.
     *
     * @return the output directory path
     */
    public Path outputDirectory() {
        return outputDirectory;
    }

    /**
     * Builder for constructing {@link ModelOutputConfiguration} instances.
     *
     * <p>
     * All parameters have sensible defaults.
     *
     * <p>
     * Example:
     *
     * <pre>
     * {
     *     &#64;code
     *     ModelOutputConfiguration config = ModelOutputConfiguration.builder().outputDirectory(Path.of("models"))
     *             .filePrefix("crf_model").iterationInterval(5).build();
     * }
     * </pre>
     */
    @NullMarked
    public static final class Builder {
        private String filePrefix = "model_";
        private String fileSuffix = ".ser";
        private int iterationInterval = 10;
        private Path outputDirectory = Paths.get("");

        private Builder() {}

        /**
         * Builds the configuration with the current settings.
         *
         * @return an immutable configuration instance
         */
        public ModelOutputConfiguration build() {
            return new ModelOutputConfiguration(this);
        }

        /**
         * Sets the prefix for output file names.
         *
         * @param filePrefix the file prefix, must not be null
         * @return this builder
         * @throws NullPointerException if filePrefix is null
         */
        public Builder filePrefix(String filePrefix) {
            Objects.requireNonNull(filePrefix, "filePrefix may not be null");

            this.filePrefix = filePrefix;
            return this;
        }

        /**
         * Sets the suffix (extension) for output file names.
         *
         * @param fileSuffix the file suffix, must not be null
         * @return this builder
         * @throws NullPointerException if fileSuffix is null
         */
        public Builder fileSuffix(String fileSuffix) {
            Objects.requireNonNull(fileSuffix, "fileSuffix may not be null");

            this.fileSuffix = fileSuffix;
            return this;
        }

        /**
         * Sets the iteration interval for writing model files.
         *
         * @param iterationInterval the interval, must be positive
         * @return this builder
         * @throws IllegalArgumentException if iterationInterval is not positive
         */
        public Builder iterationInterval(int iterationInterval) {
            if (iterationInterval <= 0) {
                throw new IllegalArgumentException("iterationInterval must be positive, got: " + iterationInterval);
            }
            this.iterationInterval = iterationInterval;
            return this;
        }

        /**
         * Sets the directory in which to write model files.
         *
         * @param outputDirectory the output directory path, must not be null
         * @return this builder
         * @throws NullPointerException if outputDirectory is null
         */
        public Builder outputDirectory(Path outputDirectory) {
            Objects.requireNonNull(outputDirectory, "outputDirectory may not be null");

            this.outputDirectory = outputDirectory;
            return this;
        }
    }
}
