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
 * The outcome of comparing a stored token list against a re-tokenized one.
 *
 * <p>
 * It carries the {@link #differences()} between the two lists: one entry per divergent region, in
 * order. An empty list means the lists are identical, which is what {@link #aligned()} reports.
 * Matched runs are not listed; they are the gaps between successive differences.
 *
 * <p>
 * Instances are produced by an {@link AlignmentStrategy} and constructed via
 * {@link AlignmentModels#tokenComparison(List)}.
 *
 * @see AlignmentStrategy#compare(List, List)
 * @see AlignmentModels#tokenComparison(List)
 */
public interface TokenComparison {
    /**
     * Returns whether the two token lists are identical.
     *
     * @return {@code true} iff {@link #differences()} is empty
     */
    default boolean aligned() {
        return differences().isEmpty();
    }

    /**
     * Returns the divergent regions between the stored and re-tokenized token lists, in order.
     *
     * @return the differences, empty when the lists are identical
     */
    List<TokenDifference> differences();
}
