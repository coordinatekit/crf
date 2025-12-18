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

import java.util.Set;

/**
 * A positioned token that includes extracted features.
 *
 * <p>
 * This interface extends {@link PositionedToken} to add feature information, representing a token
 * along with the set of features extracted for it during preprocessing. Features are used by the
 * CRF model to make predictions about token labels.
 *
 * @param <F> the type of features associated with this token
 * @see PositionedToken
 * @see FeatureSequence
 */
@NullMarked
public interface FeaturePositionedToken<F> extends PositionedToken {
    /**
     * Returns the set of features extracted for this token.
     *
     * @return an unmodifiable set of features for this token
     */
    Set<F> features();
}
