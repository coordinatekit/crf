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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A feature extractor that matches tokens against a regex pattern.
 *
 * <p>
 * This extractor tests each token against a compiled regular expression and emits features based on
 * whether the token matches. Different features can be configured for matching and non-matching
 * tokens.
 *
 * <p>
 * Use one of the {@code builder} factory methods to create instances:
 *
 * <pre>
 * {
 *     &#64;code
 *     PatternMatcherFeatureExtractor<String> extractor = PatternMatcherFeatureExtractor
 *             .<String>builder("[A-Z]+", false).matchedFeature("IS_CAPS").notMatchedFeature("NOT_CAPS").build();
 * }
 * </pre>
 *
 * @param <F> the type of feature produced by the extractor
 */
@NullMarked
public class PatternMatchingFeatureExtractor<F> implements FeatureExtractor<F> {
    private final @Nullable F matchedFeature;
    private final @Nullable F notMatchedFeature;
    private final Pattern pattern;

    private PatternMatchingFeatureExtractor(Builder<F> builder) {
        this.pattern = builder.pattern;
        this.matchedFeature = builder.matchedFeature;
        this.notMatchedFeature = builder.notMatchedFeature;
    }

    @Override
    public Set<F> extractAt(Sequence<? extends PositionedToken> sequence, int position) {
        Matcher matcher = pattern.matcher(sequence.get(position).token());

        if (matcher.matches()) {
            return matchedFeature != null ? Set.of(matchedFeature) : Set.of();
        } else {
            return notMatchedFeature != null ? Set.of(notMatchedFeature) : Set.of();
        }
    }

    /**
     * Creates a new builder with the specified compiled pattern.
     *
     * @param pattern the compiled regex pattern to match tokens against
     * @param <F> the type of feature produced by the extractor
     * @return a new builder instance
     */
    public static <F> Builder<F> builder(Pattern pattern) {
        return new Builder<>(pattern);
    }

    /**
     * Creates a new builder with the specified pattern string.
     *
     * <p>
     * The pattern is compiled as case-sensitive.
     *
     * @param pattern the regex pattern string to match tokens against
     * @param <F> the type of feature produced by the extractor
     * @return a new builder instance
     */
    public static <F> Builder<F> builder(String pattern) {
        return new Builder<>(Pattern.compile(pattern));
    }

    /**
     * Creates a new builder with the specified pattern string and case sensitivity.
     *
     * @param pattern the regex pattern string to match tokens against
     * @param caseSensitive {@code true} for case-sensitive matching, {@code false} for case-insensitive
     * @param <F> the type of feature produced by the extractor
     * @return a new builder instance
     */
    public static <F> Builder<F> builder(String pattern, boolean caseSensitive) {
        return new Builder<>(Pattern.compile(pattern, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE));
    }

    /**
     * Builder for {@link PatternMatchingFeatureExtractor}.
     *
     * @param <F> the type of feature produced by the extractor
     */
    public static final class Builder<F> {
        private final Pattern pattern;
        private @Nullable F matchedFeature;
        private @Nullable F notMatchedFeature;

        private Builder(Pattern pattern) {
            this.pattern = pattern;
        }

        /**
         * Sets the feature to emit when a token matches the pattern.
         *
         * @param matchedFeature the feature to emit on match, or {@code null} for no feature
         * @return this builder
         */
        public Builder<F> matchedFeature(@Nullable F matchedFeature) {
            this.matchedFeature = matchedFeature;
            return this;
        }

        /**
         * Sets the feature to emit when a token does not match the pattern.
         *
         * @param notMatchedFeature the feature to emit on non-match, or {@code null} for no feature
         * @return this builder
         */
        public Builder<F> notMatchedFeature(@Nullable F notMatchedFeature) {
            this.notMatchedFeature = notMatchedFeature;
            return this;
        }

        /**
         * Builds the feature extractor.
         *
         * @return a new {@link PatternMatchingFeatureExtractor} instance
         */
        public PatternMatchingFeatureExtractor<F> build() {
            return new PatternMatchingFeatureExtractor<>(this);
        }
    }
}
