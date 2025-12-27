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

import java.util.Objects;

/**
 * Configuration settings for {@link MalletCrfTrainer}.
 *
 * <p>
 * This immutable class encapsulates all configurable parameters for CRF model training using the
 * MALLET library. Use the {@link Builder} to construct instances with custom settings, or use
 * {@link #defaults()} to obtain a configuration with sensible default values.
 *
 * <p>
 * Example usage:
 *
 * <pre>
 * <code>
 * MalletCrfTrainerConfig config = MalletCrfTrainerConfig.builder().gaussianVariance(5.0).iterations(1000)
 *         .numThreads(8).build();
 * </code>
 * </pre>
 *
 * @see MalletCrfTrainer
 * @see Builder
 */
@NullMarked
public final class MalletCrfTrainerConfiguration {
    private static final MalletCrfTrainerConfiguration DEFAULTS = builder().build();

    private final boolean conllOutputEnabled;
    private final ConllOutputConfiguration conllOutputConfiguration;
    private final boolean fullyConnected;
    private final double gaussianVariance;
    private final int iterations;
    private final boolean modelOutputEnabled;
    private final ModelOutputConfiguration modelOutputConfiguration;
    private final int randomSeed;
    private final int threads;
    private final double trainingFraction;
    private final WeightsType weightsType;

    private MalletCrfTrainerConfiguration(Builder builder) {
        this.conllOutputEnabled = builder.conllOutputEnabled;
        this.conllOutputConfiguration = builder.conllOutputConfiguration;
        this.fullyConnected = builder.fullyConnected;
        this.gaussianVariance = builder.gaussianVariance;
        this.iterations = builder.iterations;
        this.modelOutputEnabled = builder.modelOutputEnabled;
        this.modelOutputConfiguration = builder.modelOutputConfiguration;
        this.randomSeed = builder.randomSeed;
        this.threads = builder.threads;
        this.trainingFraction = builder.trainingFraction;
        this.weightsType = builder.weightsType;
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
     * This is equivalent to calling {@code MalletCrfTrainerConfig.builder().build()}.
     *
     * @return a configuration with default settings
     */
    public static MalletCrfTrainerConfiguration defaults() {
        return DEFAULTS;
    }

    /**
     * Returns whether CoNLL output is enabled during training.
     *
     * <p>
     * When enabled, predictions are written to files in CoNLL format at regular intervals. Default is
     * true.
     *
     * @return true if CoNLL output is enabled
     * @see ConllOutputEvaluator
     */
    public boolean conllOutputEnabled() {
        return conllOutputEnabled;
    }

    /**
     * Returns the configuration for CoNLL output.
     *
     * <p>
     * This configuration controls the output directory, file naming, and iteration interval for CoNLL
     * output files.
     *
     * @return the CoNLL output configuration
     * @see ConllOutputConfiguration
     */
    public ConllOutputConfiguration conllOutputConfiguration() {
        return conllOutputConfiguration;
    }

    /**
     * Returns whether to create a fully connected CRF state machine.
     *
     * <p>
     * A fully connected CRF allows transitions between all states. Default is true.
     *
     * @return true if the CRF should be fully connected
     */
    public boolean fullyConnected() {
        return fullyConnected;
    }

    /**
     * Returns the Gaussian prior variance for L2 regularization.
     *
     * <p>
     * Higher values result in less regularization (weights can grow larger), while lower values result
     * in stronger regularization. Default is 10.0.
     *
     * @return the Gaussian prior variance
     */
    public double gaussianVariance() {
        return gaussianVariance;
    }

    /**
     * Returns the maximum number of training iterations.
     *
     * <p>
     * Training may stop earlier if convergence is detected. Default is 500.
     *
     * @return the maximum iterations
     */
    public int iterations() {
        return iterations;
    }

    /**
     * Returns whether model checkpoint output is enabled during training.
     *
     * <p>
     * When enabled, model checkpoints are serialized to files at regular intervals during training.
     * Default is true.
     *
     * @return true if model output is enabled
     * @see ModelOutputEvaluator
     */
    public boolean modelOutputEnabled() {
        return modelOutputEnabled;
    }

    /**
     * Returns the configuration for model checkpoint output.
     *
     * <p>
     * This configuration controls the output directory, file naming, and iteration interval for model
     * checkpoint files.
     *
     * @return the model output configuration
     * @see ModelOutputConfiguration
     */
    public ModelOutputConfiguration modelOutputConfiguration() {
        return modelOutputConfiguration;
    }

    /**
     * Returns the random seed for reproducible data splitting.
     *
     * <p>
     * Using the same seed will produce the same train/test split. Default is 0.
     *
     * @return the random seed
     */
    public int randomSeed() {
        return randomSeed;
    }

    /**
     * Returns the number of threads to use for parallel training.
     *
     * <p>
     * More threads can speed up training on multi-core systems. Default is 6.
     *
     * @return the number of threads
     */
    public int threads() {
        return threads;
    }

    /**
     * Returns the fraction of data to use for training.
     *
     * <p>
     * The remaining data is used for testing/evaluation. Default is 0.5.
     *
     * @return the training fraction, between 0.0 (exclusive) and 1.0 (inclusive)
     */
    public double trainingFraction() {
        return trainingFraction;
    }

    /**
     * Returns the weight storage type for the CRF.
     *
     * <p>
     * Controls memory usage and computation speed trade-offs. Default is
     * {@link WeightsType#SOME_DENSE}.
     *
     * @return the weights type
     */
    public WeightsType weightsType() {
        return weightsType;
    }

    /**
     * Builder for constructing {@link MalletCrfTrainerConfiguration} instances.
     *
     * <p>
     * All parameters have sensible defaults, so you only need to set the values you want to customize.
     * The builder validates all parameters when {@link #build()} is called.
     *
     * <p>
     * Example:
     *
     * <pre>
     * <code>
     * MalletCrfTrainerConfig config = MalletCrfTrainerConfig.builder().gaussianVariance(5.0).trainingFraction(0.8)
     *         .iterations(1000).build();
     * </code>
     * </pre>
     */
    @NullMarked
    public static final class Builder {
        private boolean fullyConnected = true;
        private boolean conllOutputEnabled = true;
        private ConllOutputConfiguration conllOutputConfiguration = ConllOutputConfiguration.defaults();
        private double gaussianVariance = 10.0;
        private int iterations = 500;
        private boolean modelOutputEnabled = true;
        private ModelOutputConfiguration modelOutputConfiguration = ModelOutputConfiguration.defaults();
        private int randomSeed = 0;
        private int threads = 6;
        private double trainingFraction = 0.5;
        private WeightsType weightsType = WeightsType.SOME_DENSE;

        private Builder() {}

        /**
         * Builds the configuration with the current settings.
         *
         * @return an immutable configuration instance
         */
        public MalletCrfTrainerConfiguration build() {
            return new MalletCrfTrainerConfiguration(this);
        }

        /**
         * Sets whether to enable CoNLL output during training.
         *
         * @param conllOutputEnabled true to enable CoNLL output
         * @return this builder
         */
        public Builder conllOutputEnabled(boolean conllOutputEnabled) {
            this.conllOutputEnabled = conllOutputEnabled;
            return this;
        }

        /**
         * Sets the configuration for CoNLL output.
         *
         * <p>
         * If null is provided, a default configuration will be used.
         *
         * @param conllOutputConfiguration the CoNLL output configuration, or null for defaults
         * @return this builder
         */
        public Builder conllOutputConfiguration(ConllOutputConfiguration conllOutputConfiguration) {
            this.conllOutputConfiguration = Objects
                    .requireNonNullElseGet(conllOutputConfiguration, () -> ConllOutputConfiguration.builder().build());
            return this;
        }

        /**
         * Sets whether to create a fully connected CRF state machine.
         *
         * @param fullyConnected true for a fully connected CRF
         * @return this builder
         */
        public Builder fullyConnected(boolean fullyConnected) {
            this.fullyConnected = fullyConnected;
            return this;
        }

        /**
         * Sets the Gaussian prior variance for L2 regularization.
         *
         * @param gaussianVariance the variance, must be positive
         * @return this builder
         * @throws IllegalArgumentException if gaussianVariance is not positive
         */
        public Builder gaussianVariance(double gaussianVariance) {
            if (gaussianVariance <= 0) {
                throw new IllegalArgumentException("gaussianVariance must be positive, got: " + gaussianVariance);
            }
            this.gaussianVariance = gaussianVariance;
            return this;
        }

        /**
         * Sets the maximum number of training iterations.
         *
         * @param iterations the maximum iterations, must be positive
         * @return this builder
         * @throws IllegalArgumentException if iterations is not positive
         */
        public Builder iterations(int iterations) {
            if (iterations <= 0) {
                throw new IllegalArgumentException("iterations must be positive, got: " + iterations);
            }
            this.iterations = iterations;
            return this;
        }

        /**
         * Sets whether to enable model checkpoint output during training.
         *
         * @param modelOutputEnabled true to enable model output
         * @return this builder
         */
        public Builder modelOutputEnabled(boolean modelOutputEnabled) {
            this.modelOutputEnabled = modelOutputEnabled;
            return this;
        }

        /**
         * Sets the configuration for model checkpoint output.
         *
         * <p>
         * If null is provided, a default configuration will be used.
         *
         * @param modelOutputConfiguration the model output configuration, or null for defaults
         * @return this builder
         */
        public Builder modelOutputConfiguration(ModelOutputConfiguration modelOutputConfiguration) {
            this.modelOutputConfiguration = Objects
                    .requireNonNullElseGet(modelOutputConfiguration, ModelOutputConfiguration::defaults);
            return this;
        }

        /**
         * Sets the random seed for data splitting.
         *
         * @param randomSeed the seed value
         * @return this builder
         */
        public Builder randomSeed(int randomSeed) {
            this.randomSeed = randomSeed;
            return this;
        }

        /**
         * Sets the number of threads for parallel training.
         *
         * @param numThreads the number of threads, must be positive
         * @return this builder
         * @throws IllegalArgumentException if threads is not positive
         */
        public Builder threads(int numThreads) {
            if (numThreads <= 0) {
                throw new IllegalArgumentException("threads must be positive, got: " + numThreads);
            }
            this.threads = numThreads;
            return this;
        }

        /**
         * Sets the fraction of data to use for training.
         *
         * @param trainingFraction the fraction, must be greater than 0.0 and at most 1.0
         * @return this builder
         * @throws IllegalArgumentException if trainingFraction is not in (0.0, 1.0]
         */
        public Builder trainingFraction(double trainingFraction) {
            if (trainingFraction <= 0.0 || trainingFraction > 1.0) {
                throw new IllegalArgumentException("trainingFraction must be in (0.0, 1.0], got: " + trainingFraction);
            }
            this.trainingFraction = trainingFraction;
            return this;
        }

        /**
         * Sets the weight storage type.
         *
         * @param weightsType the weights type, must not be null
         * @return this builder
         * @throws IllegalArgumentException if weightsType is null
         */
        public Builder weightsType(WeightsType weightsType) {
            Objects.requireNonNull(weightsType, "weightsType must not be null");

            this.weightsType = weightsType;
            return this;
        }
    }
}
