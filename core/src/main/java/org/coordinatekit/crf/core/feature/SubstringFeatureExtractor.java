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

import java.util.Set;
import java.util.function.Function;

/**
 * A feature extractor that generates features based on prefixes or suffixes of tokens.
 *
 * <p>
 * This extractor extracts a substring of a configurable length from either the beginning (prefix)
 * or end (suffix) of each token and maps it to a feature using a provided function.
 *
 * <p>
 * Use the {@link #builder(Function)} factory method to create instances:
 *
 * <pre>
 * <code>
 * SubstringFeatureExtractor extractor = SubstringFeatureExtractor.builder(s -> createFeature("PREFIX_" + s))
 *         .length(3).ending(false).includeIfLessThanLength(true).build();
 * </code>
 * </pre>
 */
@NullMarked
public class SubstringFeatureExtractor implements FeatureExtractor {
    private final boolean ending;
    private final Function<String, Feature> featureMapper;
    private final boolean includeIfLessThanLength;
    private final int length;

    private SubstringFeatureExtractor(Builder builder) {
        this.ending = builder.ending;
        this.featureMapper = builder.featureMapper;
        this.includeIfLessThanLength = builder.includeIfLessThanLength;
        this.length = builder.length;
    }

    @Override
    public Set<Feature> extractAt(Sequence<? extends PositionedToken> sequence, int position) {
        String token = sequence.get(position).token();

        if (!includeIfLessThanLength && token.length() < length) {
            return Set.of();
        }

        String substring;

        if (token.length() <= length) {
            substring = token;
        } else if (ending) {
            substring = token.substring(token.length() - length);
        } else {
            substring = token.substring(0, length);
        }

        return Set.of(featureMapper.apply(substring));
    }

    /**
     * Creates a new builder with the specified feature mapper function.
     *
     * @param featureMapper the function to map substrings to features
     * @return a new builder instance
     */
    public static Builder builder(Function<String, Feature> featureMapper) {
        return new Builder(featureMapper);
    }

    /**
     * Builder for {@link SubstringFeatureExtractor}.
     */
    public static final class Builder {
        private final Function<String, Feature> featureMapper;
        private boolean ending = false;
        private boolean includeIfLessThanLength = true;
        private int length = 2;

        private Builder(Function<String, Feature> featureMapper) {
            this.featureMapper = featureMapper;
        }

        /**
         * Sets whether to extract from the end (suffix) or beginning (prefix) of the token.
         *
         * <p>
         * Defaults to {@code false} (prefix extraction).
         *
         * @param ending {@code true} for suffix extraction, {@code false} for prefix extraction
         * @return this builder
         */
        public Builder ending(boolean ending) {
            this.ending = ending;
            return this;
        }

        /**
         * Sets whether to include tokens shorter than the configured length.
         *
         * <p>
         * When {@code true}, tokens shorter than the length will use the entire token as the substring.
         * When {@code false}, no feature will be emitted for short tokens.
         *
         * <p>
         * Defaults to {@code true}.
         *
         * @param includeIfLessThanLength {@code true} to include short tokens, {@code false} to skip them
         * @return this builder
         */
        public Builder includeIfLessThanLength(boolean includeIfLessThanLength) {
            this.includeIfLessThanLength = includeIfLessThanLength;
            return this;
        }

        /**
         * Sets the length of the substring to extract.
         *
         * <p>
         * Defaults to {@code 2}.
         *
         * @param length the number of characters to extract (must be at least 1)
         * @return this builder
         * @throws IllegalArgumentException if length is less than 1
         */
        public Builder length(int length) {
            if (length < 1) {
                throw new IllegalArgumentException("length must be at least 1");
            }
            this.length = length;
            return this;
        }

        /**
         * Builds the feature extractor.
         *
         * @return a new {@link SubstringFeatureExtractor} instance
         */
        public SubstringFeatureExtractor build() {
            return new SubstringFeatureExtractor(this);
        }
    }
}
