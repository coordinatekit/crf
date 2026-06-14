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
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A live {@link AlignmentReport} that re-reads its source file on every accessor.
 *
 * <p>
 * Produced by {@link AlignmentDetector#detectStreaming(Path)}. The report holds no results: it
 * pairs a detector with its {@link #source()} file and streams each {@link SequenceAlignment} on
 * demand by re-reading and re-tokenizing the file.
 *
 * <p>
 * Because it is a live view, not a snapshot, every accessor — {@link #sequences()},
 * {@link #misaligned()}, {@link #summary()} — re-opens and re-tokenizes {@link #source()}, so each
 * call reflects the file's current contents. Reading several statistics therefore costs several
 * full passes; {@link #summary()} folds them into one pass, and {@link #materialize()} captures one
 * pass into a {@link MaterializedAlignmentReport} whose accessors are then cheap. Every returned
 * {@link Stream} owns a file handle and must be closed (use try-with-resources); {@link #summary()}
 * and {@link #materialize()} close the stream they open.
 *
 * @param <T> the tag type of the training data
 * @see AlignmentDetector
 * @see MaterializedAlignmentReport
 */
public final class StreamingAlignmentReport<T extends Comparable<T>> implements AlignmentReport<T> {
    private final AlignmentDetector<T> detector;
    private final Path source;

    StreamingAlignmentReport(AlignmentDetector<T> detector, Path source) {
        this.detector = Objects.requireNonNull(detector, "detector must not be null");
        this.source = Objects.requireNonNull(source, "source must not be null");
    }

    /**
     * Captures the file's alignments into a {@link MaterializedAlignmentReport} in a single pass.
     *
     * <p>
     * Reads {@link #source()} once and holds every {@link SequenceAlignment} in memory, so the returned
     * report's accessors are cheap and need no file handle. Closes the stream it opens.
     *
     * @return an in-memory snapshot of this report
     * @throws IOException if the source file cannot be read
     */
    public MaterializedAlignmentReport<T> materialize() throws IOException {
        try (Stream<SequenceAlignment<T>> stream = sequences()) {
            return new MaterializedAlignmentReport<>(source, stream.toList());
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The file is opened eagerly, so an {@link IOException} surfaces from this call rather than
     * mid-traversal; a malformed source still throws lazily as the stream is read (the XML sequencer
     * throws {@link UncheckedCrfException}). The returned stream owns the file handle and must be
     * closed by the caller (use try-with-resources). It is sequential and must not be parallelized: the
     * document-order index is assigned during a sequential traversal, so {@code .parallel()} would
     * corrupt it. Memory is {@code O(1)} — sequences are aligned one at a time as they are pulled.
     */
    @Override
    public Stream<SequenceAlignment<T>> sequences() throws IOException {
        return detector.alignSequences(source);
    }

    @Override
    public Path source() {
        return source;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Folds {@link #sequences()} into the tally in a single pass and closes the stream it opens. Prefer
     * this over reading several statistics separately, each of which costs its own full pass.
     */
    @Override
    public AlignmentSummary summary() throws IOException {
        try (Stream<SequenceAlignment<T>> stream = sequences()) {
            return AlignmentSummary.summarize(stream);
        }
    }
}
