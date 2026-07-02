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
package org.coordinatekit.crf.core.feature;

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
 * current position. Features from neighboring tokens are stamped with their window
 * {@link Feature#offset() offset} — negative for preceding tokens, positive for following ones — so
 * the positional context becomes structural data rather than an encoded name. This is useful for
 * capturing contextual information from nearby tokens in sequence labeling tasks.
 *
 * <p>
 * The window is defined by two parameters: {@code windowBefore} specifies how many tokens to look
 * back, and {@code windowAfter} specifies how many tokens to look forward. Features are only
 * extracted for positions that exist within the sequence bounds.
 *
 * <p>
 * By default, features from the current token are included at offset {@code 0}. This behavior can
 * be disabled using {@link Builder#includeCurrentToken(boolean)}.
 *
 * <p>
 * Example usage:
 *
 * <pre>
 * <code>
 * WindowFeatureExtractor extractor = WindowFeatureExtractor
 *         .builder(baseExtractor)
 *         .windowBefore(2)
 *         .windowAfter(2)
 *         .build();
 * </code>
 * </pre>
 */
@NullMarked
public class WindowFeatureExtractor implements FeatureExtractor {
    private final FeatureExtractor delegate;
    private final int windowBefore;
    private final int windowAfter;
    private final boolean includeCurrentToken;

    private WindowFeatureExtractor(Builder builder) {
        this.delegate = builder.delegate;
        this.windowBefore = builder.windowBefore;
        this.windowAfter = builder.windowAfter;
        this.includeCurrentToken = builder.includeCurrentToken;
    }

    /**
     * Creates a new builder for constructing a {@code WindowFeatureExtractor}.
     *
     * @param delegate the feature extractor to apply to the current and neighboring tokens
     * @return a new builder instance
     */
    public static Builder builder(FeatureExtractor delegate) {
        return new Builder(delegate);
    }

    @Override
    public Set<Feature> extractAt(Sequence<? extends PositionedToken> sequence, int position) {
        Set<Feature> features = new HashSet<>();

        // Extract features from current token
        if (includeCurrentToken) {
            features.addAll(delegate.extractAt(sequence, position));
        }

        // Extract features from preceding tokens
        for (int offset = 1; offset <= windowBefore; offset++) {
            int neighborPosition = position - offset;
            if (neighborPosition >= 0) {
                Set<Feature> neighborFeatures = delegate.extractAt(sequence, neighborPosition);
                for (Feature feature : neighborFeatures) {
                    features.add(feature.withOffset(-offset));
                }
            }
        }

        // Extract features from following tokens
        for (int offset = 1; offset <= windowAfter; offset++) {
            int neighborPosition = position + offset;
            if (neighborPosition < sequence.size()) {
                Set<Feature> neighborFeatures = delegate.extractAt(sequence, neighborPosition);
                for (Feature feature : neighborFeatures) {
                    features.add(feature.withOffset(offset));
                }
            }
        }

        return Collections.unmodifiableSet(features);
    }

    /**
     * Builder for constructing {@link WindowFeatureExtractor} instances.
     */
    public static class Builder {
        private final FeatureExtractor delegate;
        private int windowBefore = 1;
        private int windowAfter = 1;
        private boolean includeCurrentToken = true;

        private Builder(FeatureExtractor delegate) {
            this.delegate = delegate;
        }

        /**
         * Sets whether to include features from the current token. Defaults to {@code true}.
         *
         * <p>
         * When enabled, features from the current token are included at offset {@code 0}. When disabled,
         * only features from neighboring tokens (stamped with their window offset) are included.
         *
         * @param includeCurrentToken {@code true} to include current token features, {@code false} to
         *        exclude them
         * @return this builder
         */
        public Builder includeCurrentToken(boolean includeCurrentToken) {
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
        public Builder windowBefore(int windowBefore) {
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
        public Builder windowAfter(int windowAfter) {
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
        public WindowFeatureExtractor build() {
            return new WindowFeatureExtractor(this);
        }
    }
}
