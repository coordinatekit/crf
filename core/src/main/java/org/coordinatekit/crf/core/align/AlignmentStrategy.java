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
package org.coordinatekit.crf.core.align;

import java.util.List;

/**
 * Compares a stored token list against a re-tokenized one and reports how they diverge.
 *
 * <p>
 * A strategy is the policy {@link AlignmentDetector} delegates the comparison to. The default,
 * {@link AlignmentModels#exactMatchStrategy()}, treats the lists as aligned only when they are
 * element-for-element equal. Alternative policies — case-insensitive or normalized matching,
 * edit-distance reporting — are introduced as further strategies without changing the detector.
 *
 * @see AlignmentModels#exactMatchStrategy()
 * @see AlignmentDetector
 */
@FunctionalInterface
public interface AlignmentStrategy {
    /**
     * Compares the two token lists and returns their divergences.
     *
     * @param storedTokens the tokens stored in the training data
     * @param retokenizedTokens the tokens produced by re-tokenizing the surface
     * @return the comparison, whose {@link TokenComparison#differences()} is empty iff the lists are
     *         identical under this strategy
     */
    TokenComparison compare(List<String> storedTokens, List<String> retokenizedTokens);
}
