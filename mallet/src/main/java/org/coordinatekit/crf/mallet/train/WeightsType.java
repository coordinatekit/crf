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
package org.coordinatekit.crf.mallet.train;

import org.jspecify.annotations.NullMarked;

/**
 * Specifies the weight storage strategy for CRF training.
 *
 * <p>
 * This enum controls how feature weights are stored during CRF model training, affecting both
 * memory usage and computational performance.
 */
@NullMarked
public enum WeightsType {
    /**
     * Use dense weight storage for all features.
     *
     * <p>
     * Stores weights for all possible feature combinations, regardless of whether they appear in the
     * training data. This uses more memory but may be faster for smaller feature spaces.
     */
    DENSE,

    /**
     * Use sparse weight storage for all features.
     *
     * <p>
     * Only stores weights for features that appear in the training data. This is more memory-efficient
     * for large feature spaces with many zero weights.
     */
    SPARSE,

    /**
     * Use a hybrid approach with some dense and some sparse weights.
     *
     * <p>
     * This is the default and recommended setting. It uses dense storage for frequently occurring
     * features and sparse storage for rare features, providing a balance between memory efficiency and
     * computational speed.
     */
    SOME_DENSE
}
