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

/**
 * A single region where a stored token list and a re-tokenized one diverge.
 *
 * <p>
 * The region is described by two half-open spans, {@code [start, end)}, one indexing into the
 * stored token list and one into the re-tokenized list. The {@link #kind()} is determined by which
 * spans are empty: an {@link DifferenceKind#INSERTION} has an empty stored span
 * ({@code storedStart() == storedEnd()}), a {@link DifferenceKind#DELETION} has an empty
 * re-tokenized span ({@code retokenizedStart() == retokenizedEnd()}), and a
 * {@link DifferenceKind#REPLACEMENT} has both spans non-empty.
 *
 * <p>
 * Instances are constructed via
 * {@link AlignmentModels#tokenDifference(DifferenceKind, int, int, int, int)}.
 *
 * <p>
 * Within a {@link TokenComparison} the differences are ordered, and their stored and re-tokenized
 * anchors drift independently as earlier insertions and deletions accumulate. The exact-match
 * strategy populates at most a single region, but a forthcoming multi-region strategy will populate
 * several; the cross-region coherence contract — equal-length matched prefix and gaps, no overlap —
 * is enforced at construction by {@link AlignmentModels#tokenComparison(java.util.List)}.
 *
 * @see TokenComparison#differences()
 * @see AlignmentModels#tokenDifference(DifferenceKind, int, int, int, int)
 */
public interface TokenDifference {
    /**
     * Returns the kind of this difference.
     *
     * @return the difference kind
     */
    DifferenceKind kind();

    /**
     * Returns the exclusive end index of this region within the re-tokenized list.
     *
     * @return the re-tokenized span end
     */
    int retokenizedEnd();

    /**
     * Returns the inclusive start index of this region within the re-tokenized list.
     *
     * @return the re-tokenized span start
     */
    int retokenizedStart();

    /**
     * Returns the exclusive end index of this region within the stored list.
     *
     * @return the stored span end
     */
    int storedEnd();

    /**
     * Returns the inclusive start index of this region within the stored list.
     *
     * @return the stored span start
     */
    int storedStart();
}
