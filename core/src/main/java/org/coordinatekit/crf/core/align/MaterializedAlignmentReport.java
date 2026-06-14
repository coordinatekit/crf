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

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * An {@link AlignmentReport} that holds every {@link SequenceAlignment} in memory.
 *
 * <p>
 * Produced by {@link AlignmentDetector#detectMaterialized(Path)} or
 * {@link StreamingAlignmentReport#materialize()}, which read and tokenize the source once. Its
 * accessors then read from the captured list: they are cheap, return streams that own no file
 * handle (closing them is optional), and never throw {@link java.io.IOException}. The captured
 * results are a fixed snapshot and do not reflect later edits to the source file.
 *
 * @param <T> the tag type of the training data
 * @see AlignmentDetector
 * @see StreamingAlignmentReport
 */
public final class MaterializedAlignmentReport<T extends Comparable<T>> implements AlignmentReport<T> {
    private final List<SequenceAlignment<T>> sequences;
    private final Path source;
    private final AlignmentSummary summary;

    MaterializedAlignmentReport(Path source, List<SequenceAlignment<T>> sequences) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.sequences = List.copyOf(Objects.requireNonNull(sequences, "sequences must not be null"));
        this.summary = AlignmentSummary.summarize(this.sequences.stream());
    }

    @Override
    public Stream<SequenceAlignment<T>> sequences() {
        return sequences.stream();
    }

    @Override
    public Path source() {
        return source;
    }

    @Override
    public AlignmentSummary summary() {
        return summary;
    }
}
