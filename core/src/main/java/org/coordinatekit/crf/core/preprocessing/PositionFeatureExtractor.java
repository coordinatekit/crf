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
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * A feature extractor that generates features based on token position within a sequence.
 *
 * <p>
 * This extractor can produce features for:
 * <ul>
 * <li>The first token in a sequence</li>
 * <li>The last token in a sequence</li>
 * <li>Position from the start (0-indexed)</li>
 * <li>Position from the end (1-indexed, where 1 is the last position)</li>
 * </ul>
 *
 * <p>
 * Use the {@link #builder()} factory method to create instances:
 *
 * <pre>
 * <code>
 * PositionFeatureExtractor<String> extractor = PositionFeatureExtractor.<String>builder().firstFeature("FIRST")
 *         .lastFeature("LAST").positionFromStartFeatureMapper(pos -> "POS_" + pos).build();
 * </code>
 * </pre>
 *
 * @param <F> the type of features produced by this extractor
 */
@NullMarked
public class PositionFeatureExtractor<F> implements FeatureExtractor<F> {
    private final @Nullable F firstFeature;
    private final @Nullable F lastFeature;
    private final @Nullable Function<Integer, F> positionFromEndFeatureMapper;
    private final @Nullable Function<Integer, F> positionFromStartFeatureMapper;

    private PositionFeatureExtractor(Builder<F> builder) {
        this.firstFeature = builder.firstFeature;
        this.lastFeature = builder.lastFeature;
        this.positionFromEndFeatureMapper = builder.positionFromEndFeatureMapper;
        this.positionFromStartFeatureMapper = builder.positionFromStartFeatureMapper;
    }

    @Override
    public Set<F> extractAt(Sequence<? extends PositionedToken> sequence, int position) {
        if (firstFeature == null && lastFeature == null && positionFromEndFeatureMapper == null
                && positionFromStartFeatureMapper == null) {
            return Set.of();
        }

        Set<F> features = new HashSet<>();

        if (position == 0 && firstFeature != null) {
            features.add(firstFeature);
        }

        if (position == sequence.size() - 1 && lastFeature != null) {
            features.add(lastFeature);
        }

        if (positionFromStartFeatureMapper != null) {
            features.add(positionFromStartFeatureMapper.apply(position));
        }

        if (positionFromEndFeatureMapper != null) {
            features.add(positionFromEndFeatureMapper.apply(sequence.size() - position - 1));
        }

        return features;
    }

    /**
     * Creates a new builder for {@link PositionFeatureExtractor}.
     *
     * @param <F> the type of feature produced by the extractor
     * @return a new builder instance
     */
    public static <F> Builder<F> builder() {
        return new Builder<>();
    }

    /**
     * Builder for {@link PositionFeatureExtractor}.
     *
     * @param <F> the type of feature produced by the extractor
     */
    public static final class Builder<F> {
        private @Nullable F firstFeature;
        private @Nullable F lastFeature;
        private @Nullable Function<Integer, F> positionFromEndFeatureMapper;
        private @Nullable Function<Integer, F> positionFromStartFeatureMapper;

        private Builder() {}

        /**
         * Sets the feature to emit for the first token in a sequence.
         *
         * @param firstFeature the feature to emit at position 0, or {@code null} for no feature
         * @return this builder
         */
        public Builder<F> firstFeature(@Nullable F firstFeature) {
            this.firstFeature = firstFeature;
            return this;
        }

        /**
         * Sets the feature to emit for the last token in a sequence.
         *
         * @param lastFeature the feature to emit at the last position, or {@code null} for no feature
         * @return this builder
         */
        public Builder<F> lastFeature(@Nullable F lastFeature) {
            this.lastFeature = lastFeature;
            return this;
        }

        /**
         * Sets the mapper function to generate features based on position from the end.
         *
         * <p>
         * Position from the end of the sequence is zero-indexed, where 0 represents the last token. The
         * mapper will be passed the position from the end of the sequence.
         *
         * @param positionFromEndFeatureMapper the function to map position to features, or {@code null} for
         *        no features
         * @return this builder
         */
        public Builder<F> positionFromEndFeatureMapper(@Nullable Function<Integer, F> positionFromEndFeatureMapper) {
            this.positionFromEndFeatureMapper = positionFromEndFeatureMapper;
            return this;
        }

        /**
         * Sets the mapper function to generate features based on position from the start.
         *
         * <p>
         * Position from the start of the sequence is zero-indexed, where 0 represents the first token. The
         * mapper will be passed the position from the start of the sequence.
         *
         * @param positionFromStartFeatureMapper the function to map position to features, or {@code null}
         *        for no features
         * @return this builder
         */
        public Builder<F> positionFromStartFeatureMapper(
                @Nullable Function<Integer, F> positionFromStartFeatureMapper
        ) {
            this.positionFromStartFeatureMapper = positionFromStartFeatureMapper;
            return this;
        }

        /**
         * Builds the feature extractor.
         *
         * @return a new {@link PositionFeatureExtractor} instance
         */
        public PositionFeatureExtractor<F> build() {
            return new PositionFeatureExtractor<>(this);
        }
    }
}
