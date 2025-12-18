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
import org.jspecify.annotations.NullMarked;

/**
 * A positioned token that includes a training label.
 *
 * <p>
 * This interface extends {@link PositionedToken} to add tag information, representing a token from
 * labeled training data where the correct classification is known.
 *
 * @param <T> the type of tag (label) associated with this token
 * @see PositionedToken
 * @see TrainingSequence
 */
@NullMarked
public interface TrainingPositionedToken<T> extends PositionedToken {
    /**
     * Returns the training tag (label) for this token.
     *
     * @return the tag associated with this token
     */
    T tag();
}
