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
 * A positioned token that includes both extracted features and a training label.
 *
 * <p>
 * This interface combines feature information from {@link FeaturePositionedToken} with a training
 * tag, representing a token used in CRF training where both features and the correct label are
 * known.
 *
 * @param <F> the type of features associated with this token
 * @param <T> the type of tag (label) associated with this token
 * @see FeaturePositionedToken
 * @see FeatureTrainingSequence
 */
@NullMarked
public interface FeatureTrainingPositionedToken<F, T> extends FeaturePositionedToken<F> {
    /**
     * Returns the training tag (label) for this token.
     *
     * @return the tag associated with this token
     */
    T tag();
}
