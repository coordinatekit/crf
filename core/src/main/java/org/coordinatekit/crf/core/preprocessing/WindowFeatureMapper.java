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

import org.jspecify.annotations.NullMarked;

/**
 * A function that maps a feature to a new feature based on its relative position.
 *
 * <p>
 * This is typically used with {@link WindowFeatureExtractor} to transform features from neighboring
 * tokens by incorporating positional information into the feature representation.
 *
 * <p>
 * Example usage:
 *
 * <pre>
 * <code>
 * PositionalFeatureMapper&lt;String&gt; mapper = (feature, position) ->
 *         (position &lt; 0 ? "PREV_" + (-position) : "NEXT_" + position) + "__" + feature;
 * </code>
 * </pre>
 *
 * @param <F> the type of feature
 */
@FunctionalInterface
@NullMarked
public interface WindowFeatureMapper<F> {

    /**
     * Applies this mapper to transform a feature based on its relative position.
     *
     * @param feature the feature to transform
     * @param relativePosition the position of the token relative to the current token (negative for
     *        preceding tokens, positive for following tokens)
     * @return the transformed feature
     */
    F apply(F feature, int relativePosition);
}
