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

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A per-status tally of a training data file's sequence alignments.
 *
 * <p>
 * Produced in a single pass by {@link AlignmentReport#summary()}, it counts how many sequences fell
 * into each {@link AlignmentStatus}, with {@link #total()} and {@link #allAligned()} derived from
 * those counts.
 *
 * @see AlignmentReport#summary()
 */
@SuppressWarnings("SpellCheckingInspection")
public final class AlignmentSummary {
    private final long aligned;
    private final long misaligned;
    private final long untokenizable;

    /**
     * Constructs a summary from its per-status counts.
     *
     * @param aligned the number of {@link AlignmentStatus#ALIGNED} sequences
     * @param misaligned the number of {@link AlignmentStatus#MISALIGNED} sequences
     * @param untokenizable the number of {@link AlignmentStatus#UNTOKENIZABLE} sequences
     * @throws IllegalArgumentException if any count is negative
     */
    AlignmentSummary(long aligned, long misaligned, long untokenizable) {
        if (aligned < 0) {
            throw new IllegalArgumentException("aligned must not be negative, got: " + aligned);
        }
        if (misaligned < 0) {
            throw new IllegalArgumentException("misaligned must not be negative, got: " + misaligned);
        }
        if (untokenizable < 0) {
            throw new IllegalArgumentException("untokenizable must not be negative, got: " + untokenizable);
        }
        this.aligned = aligned;
        this.misaligned = misaligned;
        this.untokenizable = untokenizable;
    }

    /**
     * Returns the number of {@link AlignmentStatus#ALIGNED} sequences.
     *
     * @return the aligned count
     */
    public long aligned() {
        return aligned;
    }

    /**
     * Returns whether every sequence in the file is aligned.
     *
     * <p>
     * Vacuously {@code true} for a file with no sequences.
     *
     * @return {@code true} iff there are no misaligned or untokenizable sequences
     */
    public boolean allAligned() {
        return misaligned == 0 && untokenizable == 0;
    }

    @Override
    public boolean equals(@Nullable Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AlignmentSummary other)) {
            return false;
        }
        return aligned == other.aligned && misaligned == other.misaligned && untokenizable == other.untokenizable;
    }

    @Override
    public int hashCode() {
        return Objects.hash(aligned, misaligned, untokenizable);
    }

    /**
     * Returns the number of {@link AlignmentStatus#MISALIGNED} sequences.
     *
     * @return the misaligned count
     */
    public long misaligned() {
        return misaligned;
    }

    /**
     * Tallies a stream of alignments by their {@link SequenceAlignment#status() status}.
     *
     * @param alignments the alignments to count, in any order
     * @param <T> the tag type of the alignments
     * @return the per-status summary
     */
    static <T extends Comparable<T>> AlignmentSummary summarize(Stream<SequenceAlignment<T>> alignments) {
        Map<AlignmentStatus, Long> counts = alignments
                .collect(Collectors.groupingBy(SequenceAlignment::status, Collectors.counting()));
        return new AlignmentSummary(
                counts.getOrDefault(AlignmentStatus.ALIGNED, 0L),
                counts.getOrDefault(AlignmentStatus.MISALIGNED, 0L),
                counts.getOrDefault(AlignmentStatus.UNTOKENIZABLE, 0L)
        );
    }

    @Override
    public String toString() {
        return "AlignmentSummary[aligned=" + aligned + ", misaligned=" + misaligned + ", untokenizable=" + untokenizable
                + "]";
    }

    /**
     * Returns the total number of sequences in the file.
     *
     * @return the sum of the per-status counts
     */
    public long total() {
        return aligned + misaligned + untokenizable;
    }

    /**
     * Returns the number of {@link AlignmentStatus#UNTOKENIZABLE} sequences.
     *
     * @return the untokenizable count
     */
    public long untokenizable() {
        return untokenizable;
    }
}
