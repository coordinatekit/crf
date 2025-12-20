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
package org.coordinatekit.crf.core.tag;

import org.jspecify.annotations.NullMarked;

/**
 * A tag with its associated confidence score.
 *
 * <p>
 * This interface represents a single tag prediction from the CRF model, pairing the tag value with
 * the model's confidence score for that prediction.
 *
 * @param <T> the type of tag
 * @see TaggedPositionedToken
 */
@NullMarked
public interface TagScore<T extends Comparable<T>> extends Comparable<TagScore<T>> {
    /**
     * Returns the confidence score for this tag.
     *
     * @return the score value
     */
    double score();

    /**
     * Returns the tag value.
     *
     * @return the tag
     */
    T tag();
}
