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

import org.coordinatekit.crf.core.UncheckedCrfException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * The alignment results for a single training data file.
 *
 * <p>
 * A report exposes the per-sequence {@link SequenceAlignment}s of one {@link #source()} file, the
 * {@link #misaligned()} subset, and a {@link #summary()} tally. Two implementations trade memory
 * against freshness:
 *
 * <ul>
 * <li>{@link StreamingAlignmentReport} re-reads and re-tokenizes the source on every accessor, so
 * it holds no results and runs in {@code O(1)} memory but pays a full pass per call. Obtain one
 * from {@link AlignmentDetector#detectStreaming(Path)}.
 * <li>{@link MaterializedAlignmentReport} captures the alignments once into memory, so its
 * accessors are cheap and need no file handle, at the cost of holding every result. Obtain one from
 * {@link AlignmentDetector#detectMaterialized(Path)} or
 * {@link StreamingAlignmentReport#materialize()}.
 * </ul>
 *
 * <p>
 * The accessors are declared to throw {@link IOException} for the streaming case; a materialized
 * report does its reading up front and never throws from them.
 *
 * @param <T> the tag type of the training data
 * @see AlignmentDetector
 * @see SequenceAlignment
 */
public interface AlignmentReport<T extends Comparable<T>> {
    /**
     * Streams the sequences whose status is not {@link AlignmentStatus#ALIGNED}.
     *
     * <p>
     * This includes {@link AlignmentStatus#UNTOKENIZABLE} sequences as well as
     * {@link AlignmentStatus#MISALIGNED} ones. Filters {@link #sequences()}, so the same freshness and
     * stream-closing rules apply.
     *
     * @return the non-aligned sequences in document order
     * @throws IOException if the source file cannot be read
     */
    default Stream<SequenceAlignment<T>> misaligned() throws IOException {
        return sequences().filter(alignment -> !alignment.isAligned());
    }

    /**
     * Streams the per-sequence alignment results in document order.
     *
     * @return a lazy, ordered stream of every sequence's alignment result
     * @throws IOException if the source file cannot be read
     * @throws UncheckedCrfException if the XML sequencer reads a file that is not well-formed CRF
     *         training XML
     */
    Stream<SequenceAlignment<T>> sequences() throws IOException;

    /**
     * Returns the file these results are computed from.
     *
     * @return the source path
     */
    Path source();

    /**
     * Summarizes the file's alignments into a per-status tally.
     *
     * @return the per-status summary
     * @throws IOException if the source file cannot be read
     */
    AlignmentSummary summary() throws IOException;
}
