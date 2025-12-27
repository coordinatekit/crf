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

import java.util.Set;
import java.util.function.Function;

/**
 * A feature extractor that applies a transformation function to each token to produce features.
 *
 * <p>
 * This extractor delegates feature extraction to a user-provided function, allowing flexible
 * feature generation without requiring a custom {@link FeatureExtractor} implementation.
 *
 * <p>
 * Example usage:
 *
 * <pre>
 * <code>
 * TransformingFeatureExtractor&lt;String&gt; extractor = new TransformingFeatureExtractor&lt;&gt;(
 *         token -> Set.of("LENGTH=" + token.length(), "LOWER=" + token.toLowerCase())
 * );
 * </code>
 * </pre>
 *
 * @param <F> the type of feature produced by the extractor
 */
@NullMarked
public class TransformingFeatureExtractor<F> implements FeatureExtractor<F> {
    private final Function<String, Set<F>> transformer;

    /**
     * Creates a new transforming feature extractor with the specified transformation function.
     *
     * @param transformer a function that takes a token string and returns a set of features
     */
    public TransformingFeatureExtractor(Function<String, Set<F>> transformer) {
        this.transformer = transformer;
    }

    @Override
    public Set<F> extractAt(Sequence<? extends PositionedToken> sequence, int position) {
        return transformer.apply(sequence.get(position).token());
    }
}
