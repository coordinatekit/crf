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

import static org.coordinatekit.crf.core.align.AlignmentModels.exactMatchStrategy;
import static org.coordinatekit.crf.core.align.AlignmentModels.sequenceAlignment;

import org.coordinatekit.crf.core.PositionedToken;
import org.coordinatekit.crf.core.io.TrainingDataSequencer;
import org.coordinatekit.crf.core.preprocessing.InvalidInputException;
import org.coordinatekit.crf.core.preprocessing.TrainingPositionedToken;
import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.coordinatekit.crf.core.preprocessing.Tokenizer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Detects training sequences that no longer align with a tokenizer.
 *
 * <p>
 * Given a black-box {@link Tokenizer} and a {@link TrainingDataSequencer},
 * {@link #detectStreaming(Path)} produces an {@link AlignmentReport} that streams each stored
 * sequence's alignment: it reconstructs the surface, re-tokenizes it, and compares the result
 * against the stored token list using an {@link AlignmentStrategy} (the exact-match policy by
 * default). A sequence is <em>aligned</em> iff the strategy finds no differences between the
 * re-tokenized and stored token lists. {@link #detectMaterialized(Path)} captures those results
 * once for cheap repeated access, and the single-sequence comparison is exposed directly via
 * {@link #align(int, TrainingSequence)}. The detector is read-only: it mutates nothing, streams in
 * {@code O(1)} memory, and is safe to run headless or in CI to gauge the blast radius of a
 * tokenizer change.
 *
 * @param <T> the tag type of the training data
 * @see AlignmentReport
 * @see SequenceAlignment
 * @see AlignmentStrategy
 */
public final class AlignmentDetector<T extends Comparable<T>> {
    private final TrainingDataSequencer<T> sequencer;
    private final AlignmentStrategy strategy;
    private final Tokenizer tokenizer;

    /**
     * Constructs a detector over the given tokenizer and sequencer, using the exact-match strategy.
     *
     * @param tokenizer the tokenizer to re-tokenize reconstructed surfaces with
     * @param sequencer the sequencer used to read stored sequences from the training data
     * @throws NullPointerException if either argument is null
     */
    public AlignmentDetector(Tokenizer tokenizer, TrainingDataSequencer<T> sequencer) {
        this(tokenizer, sequencer, exactMatchStrategy());
    }

    /**
     * Constructs a detector over the given tokenizer, sequencer, and comparison strategy.
     *
     * @param tokenizer the tokenizer to re-tokenize reconstructed surfaces with
     * @param sequencer the sequencer used to read stored sequences from the training data
     * @param strategy the strategy used to compare stored and re-tokenized token lists
     * @throws NullPointerException if any argument is null
     */
    public AlignmentDetector(Tokenizer tokenizer, TrainingDataSequencer<T> sequencer, AlignmentStrategy strategy) {
        this.tokenizer = Objects.requireNonNull(tokenizer, "tokenizer must not be null");
        this.sequencer = Objects.requireNonNull(sequencer, "sequencer must not be null");
        this.strategy = Objects.requireNonNull(strategy, "strategy must not be null");
    }

    /**
     * Aligns a single stored sequence against the tokenizer.
     *
     * <p>
     * The sequence's {@link TrainingSequence#surface() surface} is reconstructed and re-tokenized, then
     * the result is compared against its stored tokens. If the tokenizer rejects the surface with an
     * {@link InvalidInputException}, the alignment is reported as {@link AlignmentStatus#UNTOKENIZABLE}
     * rather than propagating; any other tokenizer exception propagates.
     *
     * @param sequenceIndex the zero-based index of the sequence within its source file
     * @param sequence the stored training sequence
     * @return the alignment result
     */
    public SequenceAlignment<T> align(int sequenceIndex, TrainingSequence<T> sequence) {
        List<String> storedTokens = sequence.stream().map(TrainingPositionedToken::token).toList();
        String surface = sequence.surface();

        List<String> retokenizedTokens;
        try {
            retokenizedTokens = tokenizer.tokenize(surface).sequence().stream().map(PositionedToken::token).toList();
        } catch (InvalidInputException exception) {
            return sequenceAlignment(
                    sequenceIndex,
                    AlignmentStatus.UNTOKENIZABLE,
                    sequence,
                    List.of(),
                    null,
                    exception.getMessage()
            );
        }

        TokenComparison comparison = strategy.compare(storedTokens, retokenizedTokens);
        AlignmentStatus status = comparison.aligned() ? AlignmentStatus.ALIGNED : AlignmentStatus.MISALIGNED;
        return sequenceAlignment(sequenceIndex, status, sequence, retokenizedTokens, comparison, null);
    }

    /**
     * Streams each stored sequence's alignment from a file in document order.
     *
     * <p>
     * Reads the stored sequences with the configured {@link TrainingDataSequencer} and aligns each
     * against the tokenizer via {@link #align(int, TrainingSequence)}, assigning the document-order
     * index during the sequential traversal. Keeps the sequencer encapsulated here while
     * {@link StreamingAlignmentReport} owns the public freshness and stream-closing contract over this
     * stream.
     *
     * @param trainingDataFile the training data file to read
     * @return a lazy, ordered stream of per-sequence alignments that must be closed after use
     * @throws IOException if the file cannot be opened
     */
    Stream<SequenceAlignment<T>> alignSequences(Path trainingDataFile) throws IOException {
        AtomicInteger index = new AtomicInteger();
        return sequencer.read(trainingDataFile).map(sequence -> align(index.getAndIncrement(), sequence));
    }

    /**
     * Builds an in-memory {@link MaterializedAlignmentReport} over a training data file.
     *
     * <p>
     * Reads and tokenizes {@code trainingDataFile} once, capturing every {@link SequenceAlignment} so
     * the returned report's accessors are cheap and need no file handle. Equivalent to
     * {@link #detectStreaming(Path) detectStreaming(trainingDataFile)}{@code .materialize()}.
     *
     * @param trainingDataFile the training data file to inspect
     * @return an in-memory report over the file
     * @throws IOException if the file cannot be read
     * @throws NullPointerException if {@code trainingDataFile} is null
     */
    public MaterializedAlignmentReport<T> detectMaterialized(Path trainingDataFile) throws IOException {
        return detectStreaming(trainingDataFile).materialize();
    }

    /**
     * Builds a lazy {@link StreamingAlignmentReport} over a training data file.
     *
     * <p>
     * No I/O happens here: the returned report pairs this detector with {@code trainingDataFile} and
     * streams each sequence's alignment on demand via {@link StreamingAlignmentReport#sequences()}. See
     * {@link StreamingAlignmentReport} for the live-view cost and the stream-closing contract.
     *
     * @param trainingDataFile the training data file to inspect
     * @return a lazy report over the file
     * @throws NullPointerException if {@code trainingDataFile} is null
     */
    public StreamingAlignmentReport<T> detectStreaming(Path trainingDataFile) {
        Objects.requireNonNull(trainingDataFile, "trainingDataFile must not be null");
        return new StreamingAlignmentReport<>(this, trainingDataFile);
    }
}
