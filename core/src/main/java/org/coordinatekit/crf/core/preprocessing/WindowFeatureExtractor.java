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
package org.coordinatekit.crf.core.preprocessing;

import org.coordinatekit.crf.core.PositionedToken;
import org.coordinatekit.crf.core.Sequence;
import org.jspecify.annotations.NullMarked;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A feature extractor that extracts features from the current token and neighboring tokens within a
 * window.
 *
 * <p>
 * This extractor applies a delegate extractor to the current token and tokens surrounding the
 * current position. Features from neighboring tokens are transformed using a
 * {@link WindowFeatureMapper} to incorporate positional information. This is useful for capturing
 * contextual information from nearby tokens in sequence labeling tasks.
 *
 * <p>
 * The window is defined by two parameters: {@code windowBefore} specifies how many tokens to look
 * back, and {@code windowAfter} specifies how many tokens to look forward. Features are only
 * extracted for positions that exist within the sequence bounds.
 *
 * <p>
 * By default, features from the current token are included without transformation. This behavior
 * can be disabled using {@link Builder#includeCurrentToken(boolean)}.
 *
 * <p>
 * Example usage:
 *
 * <pre>
 * <code>
 * WindowFeatureExtractor&lt;String&gt; extractor = WindowFeatureExtractor
 *         .builder(baseExtractor, (feature, pos) -&gt;
 *                 (pos &lt; 0 ? "PREV_" + (-pos) : "NEXT_" + pos) + "__" + feature)
 *         .windowBefore(2)
 *         .windowAfter(2)
 *         .build();
 * </code>
 * </pre>
 *
 * @param <F> the type of features produced by this extractor
 */
@NullMarked
public class WindowFeatureExtractor<F> implements FeatureExtractor<F> {
    private final FeatureExtractor<F> delegate;
    private final WindowFeatureMapper<F> featureMapper;
    private final int windowBefore;
    private final int windowAfter;
    private final boolean includeCurrentToken;

    private WindowFeatureExtractor(Builder<F> builder) {
        this.delegate = builder.delegate;
        this.featureMapper = builder.featureMapper;
        this.windowBefore = builder.windowBefore;
        this.windowAfter = builder.windowAfter;
        this.includeCurrentToken = builder.includeCurrentToken;
    }

    /**
     * Creates a new builder for constructing a {@code WindowFeatureExtractor}.
     *
     * @param <F> the type of features
     * @param delegate the feature extractor to apply to neighboring tokens
     * @param featureMapper the mapper to transform features based on relative position
     * @return a new builder instance
     */
    public static <F> Builder<F> builder(FeatureExtractor<F> delegate, WindowFeatureMapper<F> featureMapper) {
        return new Builder<>(delegate, featureMapper);
    }

    @Override
    public Set<F> extractAt(Sequence<? extends PositionedToken> sequence, int position) {
        Set<F> features = new HashSet<>();

        // Extract features from current token
        if (includeCurrentToken) {
            features.addAll(delegate.extractAt(sequence, position));
        }

        // Extract features from preceding tokens
        for (int offset = 1; offset <= windowBefore; offset++) {
            int neighborPosition = position - offset;
            if (neighborPosition >= 0) {
                Set<F> neighborFeatures = delegate.extractAt(sequence, neighborPosition);
                for (F feature : neighborFeatures) {
                    features.add(featureMapper.apply(feature, -offset));
                }
            }
        }

        // Extract features from following tokens
        for (int offset = 1; offset <= windowAfter; offset++) {
            int neighborPosition = position + offset;
            if (neighborPosition < sequence.size()) {
                Set<F> neighborFeatures = delegate.extractAt(sequence, neighborPosition);
                for (F feature : neighborFeatures) {
                    features.add(featureMapper.apply(feature, offset));
                }
            }
        }

        return Collections.unmodifiableSet(features);
    }

    /**
     * Builder for constructing {@link WindowFeatureExtractor} instances.
     *
     * @param <F> the type of features
     */
    public static class Builder<F> {
        private final FeatureExtractor<F> delegate;
        private final WindowFeatureMapper<F> featureMapper;
        private int windowBefore = 1;
        private int windowAfter = 1;
        private boolean includeCurrentToken = true;

        private Builder(FeatureExtractor<F> delegate, WindowFeatureMapper<F> featureMapper) {
            this.delegate = delegate;
            this.featureMapper = featureMapper;
        }

        /**
         * Sets whether to include features from the current token. Defaults to {@code true}.
         *
         * <p>
         * When enabled, features from the current token are included without transformation. When disabled,
         * only features from neighboring tokens (transformed by the feature mapper) are included.
         *
         * @param includeCurrentToken {@code true} to include current token features, {@code false} to
         *        exclude them
         * @return this builder
         */
        public Builder<F> includeCurrentToken(boolean includeCurrentToken) {
            this.includeCurrentToken = includeCurrentToken;
            return this;
        }

        /**
         * Sets the number of tokens to look back from the current position. Defaults to 1.
         *
         * @param windowBefore the number of preceding tokens to include
         * @return this builder
         * @throws IllegalArgumentException if windowBefore is negative
         */
        public Builder<F> windowBefore(int windowBefore) {
            if (windowBefore < 0) {
                throw new IllegalArgumentException("windowBefore must be non-negative");
            }
            this.windowBefore = windowBefore;
            return this;
        }

        /**
         * Sets the number of tokens to look forward from the current position. Defaults to 1.
         *
         * @param windowAfter the number of following tokens to include
         * @return this builder
         * @throws IllegalArgumentException if windowAfter is negative
         */
        public Builder<F> windowAfter(int windowAfter) {
            if (windowAfter < 0) {
                throw new IllegalArgumentException("windowAfter must be non-negative");
            }
            this.windowAfter = windowAfter;
            return this;
        }

        /**
         * Builds a new {@link WindowFeatureExtractor} with the configured parameters.
         *
         * @return a new WindowFeatureExtractor instance
         */
        public WindowFeatureExtractor<F> build() {
            return new WindowFeatureExtractor<>(this);
        }
    }
}
