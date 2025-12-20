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

import org.coordinatekit.crf.core.preprocessing.FeaturePositionedToken;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.SortedSet;

/**
 * A positioned token that includes the scores for each tag.
 *
 * <p>
 * This interface extends {@link FeaturePositionedToken} to add tag score information, representing
 * a token after the model has tagged it.
 *
 * @param <F> the type of features associated with this token
 * @param <T> the type of tag (label) associated with this token
 * @see FeaturePositionedToken
 * @see TaggedSequence
 */
@NullMarked
public interface TaggedPositionedToken<F, T extends Comparable<T>> extends FeaturePositionedToken<F> {
    /**
     * Returns the highest-scoring tag for this token.
     *
     * @return the most likely tag
     */
    T tag();

    /**
     * Returns an iterator over the top N tags for this token, ordered by score descending.
     *
     * @param numberOfTags the number of tags to return
     * @return a list over the top tags
     */
    List<T> tag(int numberOfTags);

    /**
     * Returns an iterator over all tag scores for this token, ordered by score descending.
     *
     * @return a set over tag scores
     */
    SortedSet<TagScore<T>> tagScores();
}
