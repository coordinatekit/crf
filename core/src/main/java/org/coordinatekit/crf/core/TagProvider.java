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
package org.coordinatekit.crf.core;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Provides tag information for CRF models, including conversion, enumeration, and the starting
 * state.
 *
 * @param <T> the type of tag this provider handles
 * @see StringTagProvider
 */
@NullMarked
public interface TagProvider<T> {
    /**
     * Converts a string representation to a typed tag value.
     *
     * @param tag the string representation of the tag, or null
     * @return the typed tag value
     */
    T decode(@Nullable String tag);

    /**
     * Converts a typed tag value to its string representation.
     *
     * @param rawTag the typed tag value
     * @return the string representation, or null
     */
    @Nullable
    String encode(T rawTag);

    /**
     * Returns the starting/fallback tag for the CRF.
     *
     * <p>
     * This is the initial state for sequence labeling, used as the starting point for transitions and
     * as a fallback for unknown or null tag strings.
     *
     * @return the starting tag; if {@link #tags()} is non-empty, this must be a member of that set
     */
    T startingTag();

    /**
     * Returns all valid tags in the model's label space.
     *
     * <p>
     * If the returned set is empty, the valid tags will be determined from the training data.
     *
     * @return an unmodifiable set of all valid tags, or an empty set if tags should be inferred from
     *         training data
     */
    Set<T> tags();
}
