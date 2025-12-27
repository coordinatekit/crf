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

import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

/**
 * A feature extractor that generates features based on the length of a sequence.
 *
 * <p>
 * This extractor produces features for each length from 1 up to a configurable upper limit,
 * distinguishing between lengths the sequence has (i.e., lengths less than or equal to the actual
 * sequence size) and lengths it lacks.
 *
 * <p>
 * Use the {@link #builder(int)} factory method to create instances:
 *
 * <pre>
 * <code>
 * LengthFeatureExtractor<String> extractor = LengthFeatureExtractor.<String>builder(5)
 *         .hasLengthFeatureMapper(len -> "HAS_LENGTH_" + len)
 *         .lacksLengthFeatureMapper(len -> "LACKS_LENGTH_" + len).build();
 * </code>
 * </pre>
 *
 * @param <F> the type of features produced by this extractor
 */
@NullMarked
public class LengthFeatureExtractor<F> implements FeatureExtractor<F> {
    private final @Nullable Function<Integer, F> hasLengthFeatureMapper;
    private final @Nullable Function<Integer, F> lacksLengthFeatureMapper;
    private final int lengthUpperLimit;

    private LengthFeatureExtractor(Builder<F> builder) {
        this.hasLengthFeatureMapper = builder.hasLengthFeatureMapper;
        this.lacksLengthFeatureMapper = builder.lacksLengthFeatureMapper;
        this.lengthUpperLimit = builder.lengthUpperLimit;
    }

    @Override
    public Set<F> extractAt(Sequence<? extends PositionedToken> sequence, int position) {
        int actualLength = sequence.size();
        return IntStream.rangeClosed(1, lengthUpperLimit).boxed().flatMap(length -> {
            Function<Integer, F> mapper = length <= actualLength ? hasLengthFeatureMapper : lacksLengthFeatureMapper;
            return mapper != null ? Stream.of(mapper.apply(length)) : Stream.empty();
        }).collect(toSet());
    }

    /**
     * Creates a new builder with the specified length upper limit.
     *
     * @param lengthUpperLimit the maximum length to generate features for
     * @param <F> the type of feature produced by the extractor
     * @return a new builder instance
     */
    public static <F> Builder<F> builder(int lengthUpperLimit) {
        return new Builder<>(lengthUpperLimit);
    }

    /**
     * Builder for {@link LengthFeatureExtractor}.
     *
     * @param <F> the type of feature produced by the extractor
     */
    public static final class Builder<F> {
        private final int lengthUpperLimit;
        private @Nullable Function<Integer, F> hasLengthFeatureMapper;
        private @Nullable Function<Integer, F> lacksLengthFeatureMapper;

        private Builder(int lengthUpperLimit) {
            this.lengthUpperLimit = lengthUpperLimit;
        }

        /**
         * Sets the mapper function to generate features for lengths the sequence has.
         *
         * <p>
         * This mapper is called for each length from 1 up to the actual sequence size.
         *
         * @param hasLengthFeatureMapper the function to map lengths to features, or {@code null} for no
         *        features
         * @return this builder
         */
        public Builder<F> hasLengthFeatureMapper(@Nullable Function<Integer, F> hasLengthFeatureMapper) {
            this.hasLengthFeatureMapper = hasLengthFeatureMapper;
            return this;
        }

        /**
         * Sets the mapper function to generate features for lengths the sequence lacks.
         *
         * <p>
         * This mapper is called for each length greater than the actual sequence size up to the upper
         * limit.
         *
         * @param lacksLengthFeatureMapper the function to map lengths to features, or {@code null} for no
         *        features
         * @return this builder
         */
        public Builder<F> lacksLengthFeatureMapper(@Nullable Function<Integer, F> lacksLengthFeatureMapper) {
            this.lacksLengthFeatureMapper = lacksLengthFeatureMapper;
            return this;
        }

        /**
         * Builds the feature extractor.
         *
         * @return a new {@link LengthFeatureExtractor} instance
         */
        public LengthFeatureExtractor<F> build() {
            return new LengthFeatureExtractor<>(this);
        }
    }
}
