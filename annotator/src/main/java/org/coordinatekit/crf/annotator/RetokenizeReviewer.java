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
package org.coordinatekit.crf.annotator;

import org.coordinatekit.crf.core.PositionedToken;
import org.coordinatekit.crf.core.Sequence;
import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.align.AlignmentDetector;
import org.coordinatekit.crf.core.align.AlignmentStatus;
import org.coordinatekit.crf.core.align.AlignmentStrategy;
import org.coordinatekit.crf.core.align.SequenceAlignment;
import org.coordinatekit.crf.core.io.TrainingSequenceWriter;
import org.coordinatekit.crf.core.io.XmlTrainingData;
import org.coordinatekit.crf.core.preprocessing.Feature;
import org.coordinatekit.crf.core.preprocessing.FeatureExtractor;
import org.coordinatekit.crf.core.preprocessing.InvalidInputException;
import org.coordinatekit.crf.core.preprocessing.Tokenization;
import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.coordinatekit.crf.core.preprocessing.Tokenizer;
import org.coordinatekit.crf.core.tag.CrfTagger;
import org.coordinatekit.crf.core.tag.TaggedPositionedToken;
import org.coordinatekit.crf.core.tag.TaggedTokenization;
import org.jline.terminal.Terminal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static org.coordinatekit.crf.annotator.AnnotatorModels.annotatorSequence;
import static org.coordinatekit.crf.annotator.AnnotatorSupport.extractDisplayFeatures;
import static org.coordinatekit.crf.annotator.AnnotatorSupport.resolveVerboseFeatures;
import static org.coordinatekit.crf.annotator.AnnotatorSupport.toSegments;
import static org.coordinatekit.crf.core.align.AlignmentModels.exactMatchStrategy;

/**
 * Reviews an XML training-data file against a new tokenizer and rewrites a corrected copy.
 *
 * <p>
 * A downstream tokenizer change can leave stored sequences tokenized under an old tokenizer that no
 * longer match what the new one produces. {@link #review(Path, Path)} walks the input file in
 * document order and, for each {@link TrainingSequence}, reconstructs its
 * {@link TrainingSequence#surface() surface}, re-tokenizes it with the configured
 * {@link Tokenizer}, and routes it by the {@link AlignmentStatus} an {@link AlignmentDetector}
 * reports:
 *
 * <ul>
 * <li>{@link AlignmentStatus#ALIGNED ALIGNED} — the stored tokenization still matches, so the
 * sequence is copied through verbatim, preserving its tags and excluded runs.</li>
 * <li>{@link AlignmentStatus#MISALIGNED MISALIGNED} — the sequence is freshly re-tokenized and
 * presented through the configured {@link TaggingInterface} for a human to re-tag. On
 * {@link TaggingAction#ACCEPT ACCEPT} the re-tokenized tokens, the chosen tags, and the new
 * excluded runs are written; on {@link TaggingAction#SKIP SKIP} the original (still-misaligned)
 * sequence is kept; on {@link TaggingAction#EXIT EXIT} this and every remaining sequence are copied
 * through unchanged and review stops.</li>
 * <li>{@link AlignmentStatus#UNTOKENIZABLE UNTOKENIZABLE} — the new tokenizer rejected the surface;
 * the original sequence is copied through unchanged and a warning is emitted to the terminal.</li>
 * </ul>
 *
 * <p>
 * The output contains <em>exactly one sequence per input sequence, in document order</em>. Review
 * never drops or reorders sequences; only accepted misaligned sequences differ from their input, so
 * unreviewed items remain a complete, valid dataset. Initial tags are always fresh —
 * model-suggested when a {@link CrfTagger} is configured, otherwise every token starts at
 * {@link TagProvider#startingTag()}; no old tags carry over.
 *
 * <p>
 * Review is a fresh, single-pass rewrite: the output path must be absent or empty, and must differ
 * from the input path. There is no content-hash resume — that behavior belongs to
 * {@link Annotator}.
 *
 * <p>
 * <strong>Tokenizer/tagger consistency.</strong> Alignment detection uses the standalone
 * {@link Tokenizer}, while presentation with a tagger uses {@link CrfTagger#tag(String)}, which
 * re-tokenizes through the tagger's own tokenizer. These two must agree on how a surface splits;
 * the downstream factory that supplies both is responsible for wiring the same tokenizer into each,
 * as the annotate path already assumes.
 *
 * <p>
 * Instances are constructed via the nested {@link Builder}.
 *
 * @param <T> the tag type
 * @see Builder
 * @see Annotator
 * @see AlignmentDetector
 */
@NullMarked
public final class RetokenizeReviewer<T extends Comparable<T>> {
    private final AlignmentStrategy alignmentStrategy;
    private final @Nullable FeatureExtractor featureExtractor;
    private final @Nullable CrfTagger<T> tagger;
    private final TagProvider<T> tagProvider;
    private final TaggingInterface<T> taggingInterface;
    private final Terminal terminal;
    private final Tokenizer tokenizer;
    private final @Nullable FeatureExtractor verboseFeatureExtractor;

    /**
     * Constructs a reviewer from a populated builder.
     *
     * @param builder the builder providing configured fields
     * @throws NullPointerException if any required builder field is null
     */
    private RetokenizeReviewer(Builder<T> builder) {
        this.alignmentStrategy = builder.alignmentStrategy == null ? exactMatchStrategy() : builder.alignmentStrategy;
        this.featureExtractor = builder.featureExtractor;
        this.tagger = builder.tagger;
        this.tagProvider = Objects.requireNonNull(builder.tagProvider, "tagProvider must be set");
        this.taggingInterface = Objects.requireNonNull(builder.taggingInterface, "taggingInterface must be set");
        this.terminal = Objects.requireNonNull(builder.terminal, "terminal must be set");
        this.tokenizer = Objects.requireNonNull(builder.tokenizer, "tokenizer must be set");
        this.verboseFeatureExtractor = builder.verboseFeatureExtractor;
    }

    /**
     * Returns a new builder for {@link RetokenizeReviewer}.
     *
     * @param <T> the tag type
     * @return a new builder with no values set
     */
    public static <T extends Comparable<T>> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Counts the sequences in a training-data file, used as the stable {@code M} denominator for the
     * "Sequence N of M" counters shown during review.
     *
     * <p>
     * This performs a full extra XML parse of the input solely to compute the summary denominator, on
     * top of the main reviewing pass. That second parse is acceptable for typical training-file sizes;
     * folding the count into the single pass is deferred.
     *
     * @param xml the reader for the input file
     * @param inputFile the input file to scan
     * @return the number of {@code <crf:Sequence>} elements in the file
     * @throws IOException if reading the file fails
     */
    private int countSequences(XmlTrainingData<T> xml, Path inputFile) throws IOException {
        try (Stream<TrainingSequence<T>> stream = xml.read(inputFile)) {
            return (int) stream.count();
        }
    }

    /**
     * Writes the one-line summary of the completed review to the configured terminal and flushes it.
     *
     * @param summary the tallied per-status counts of the review
     */
    private void emitSummary(ReviewSummary summary) {
        StringBuilder message = new StringBuilder(
                String.format(
                        Locale.getDefault(),
                        "Reviewed %,d sequence(s): %,d aligned, %,d re-tagged, %,d skipped, %,d untokenizable.",
                        summary.total,
                        summary.aligned,
                        summary.retagged,
                        summary.skipped,
                        summary.untokenizable
                )
        );
        if (summary.copiedAfterExit > 0) {
            message.append(
                    String.format(
                            Locale.getDefault(),
                            " Exited early; copied %,d sequence(s) through unchanged.",
                            summary.copiedAfterExit
                    )
            );
        }
        message.append(System.lineSeparator());
        terminal.writer().write(message.toString());
        terminal.writer().flush();
    }

    /**
     * Writes a warning that an untokenizable sequence was copied through unchanged, and flushes it.
     *
     * @param sequenceIndex the zero-based index of the untokenizable sequence
     * @param failureReason the tokenizer-supplied reason
     */
    private void emitUntokenizableWarning(int sequenceIndex, String failureReason) {
        String message = String.format(
                Locale.getDefault(),
                "Warning: sequence %,d is untokenizable and was copied through unchanged: %s",
                sequenceIndex + 1,
                failureReason
        );
        terminal.writer().write(message + System.lineSeparator());
        terminal.writer().flush();
    }

    /**
     * Presents a misaligned sequence for re-tagging, freshly re-tokenized from its surface.
     *
     * <p>
     * Mirrors the annotate path: when a {@link CrfTagger} is configured its tokenization drives the
     * display and its suggestions seed the tags; otherwise the configured {@link Tokenizer} splits the
     * surface and every token starts at {@link TagProvider#startingTag()}. The returned
     * {@link Presentation} always carries the configured {@link Tokenizer}'s tokenization — the
     * alignment authority — independent of the tagger, so an accepted sequence is persisted with that
     * tokenizer's token boundaries and excluded runs. The tagger's tokenization is used for display
     * only.
     *
     * @param sequence the misaligned stored sequence
     * @param sequenceNumber the 1-based position of this sequence within the input
     * @param totalSequences the total number of sequences in the input
     * @return the configured tokenizer's tokenization paired with the user's tagging result
     */
    private Presentation<T> present(TrainingSequence<T> sequence, int sequenceNumber, int totalSequences) {
        String surface = sequence.surface();
        Tokenization tokenization = tokenizer.tokenize(surface); // alignment authority; persisted on ACCEPT
        TaggedTokenization<T> tagged = tagger != null ? tagger.tag(surface) : null;

        Sequence<? extends PositionedToken> presentedTokens = tagged != null ? tagged.taggedSequence()
                : tokenization.sequence();
        List<Set<Feature>> features = extractDisplayFeatures(featureExtractor, presentedTokens);
        List<Set<Feature>> verboseFeatures = resolveVerboseFeatures(verboseFeatureExtractor, tagged, presentedTokens);

        AnnotatorSequence<T> displaySequence = tagged != null
                ? annotatorSequence(
                        sequenceNumber,
                        totalSequences,
                        tagged.taggedSequence(),
                        features,
                        verboseFeatures,
                        tagged::probabilityOf
                )
                : annotatorSequence(
                        sequenceNumber,
                        totalSequences,
                        tokenization.sequence().stream().map(PositionedToken::token).toList(),
                        tagProvider,
                        features,
                        verboseFeatures
                );

        return new Presentation<>(tokenization, taggingInterface.present(displaySequence));
    }

    /**
     * Reviews the input file against the configured tokenizer, writing a complete corrected dataset to
     * the output file.
     *
     * <p>
     * Each input sequence is routed by its {@link AlignmentStatus}: aligned and untokenizable sequences
     * are copied through (the latter with a terminal warning), and misaligned sequences are presented
     * through the {@link TaggingInterface} and rewritten on {@link TaggingAction#ACCEPT ACCEPT}, kept
     * as stored on {@link TaggingAction#SKIP SKIP}, or — on {@link TaggingAction#EXIT EXIT} — copied
     * through along with every remaining sequence before review stops. Every written sequence is
     * flushed immediately. A one-line summary is written to the configured {@link Terminal} when the
     * pass completes.
     *
     * @param inputFile the XML training-data file to review
     * @param outputFile the XML training-data file to write the corrected dataset to; must be absent or
     *        empty and must differ from {@code inputFile}
     * @throws ReviewPreconditionException if {@code inputFile} and {@code outputFile} are the same
     *         path, or {@code outputFile} already exists and is non-empty
     * @throws IOException if reading the input or writing the output fails
     */
    public void review(Path inputFile, Path outputFile) throws IOException {
        Objects.requireNonNull(inputFile, "inputFile must not be null");
        Objects.requireNonNull(outputFile, "outputFile must not be null");
        validateFreshPass(inputFile, outputFile);

        XmlTrainingData<T> xml = new XmlTrainingData<>(tagProvider);
        AlignmentDetector<T> detector = new AlignmentDetector<>(tokenizer, xml, alignmentStrategy);
        ReviewSummary summary = new ReviewSummary(countSequences(xml, inputFile));

        try (TrainingSequenceWriter<T> writer = xml.appendingWriter(outputFile);
                Stream<TrainingSequence<T>> stream = xml.read(inputFile)) {
            int index = 0;
            boolean exited = false;
            for (Iterator<TrainingSequence<T>> sequences = stream.iterator(); sequences.hasNext(); index++) {
                TrainingSequence<T> sequence = sequences.next();
                if (exited) {
                    writeThrough(writer, sequence);
                    summary.copiedAfterExit++;
                    continue;
                }
                SequenceAlignment<T> alignment = detector.align(index, sequence);
                switch (alignment.status()) {
                    case ALIGNED -> {
                        writeThrough(writer, sequence);
                        summary.aligned++;
                    }
                    case UNTOKENIZABLE -> {
                        emitUntokenizableWarning(
                                index,
                                Objects.requireNonNull(
                                        alignment.failureReason(),
                                        "an untokenizable alignment carries a failure reason"
                                )
                        );
                        writeThrough(writer, sequence);
                        summary.untokenizable++;
                    }
                    case MISALIGNED -> {
                        Presentation<T> presentation;
                        try {
                            presentation = present(sequence, index + 1, summary.total);
                        } catch (InvalidInputException exception) {
                            emitUntokenizableWarning(
                                    index,
                                    Objects.requireNonNull(
                                            exception.getMessage(),
                                            "an untokenizable input carries a failure reason"
                                    )
                            );
                            writeThrough(writer, sequence);
                            summary.untokenizable++;
                            continue; // advances index via the for-update; copies the sequence through unchanged
                        }
                        switch (presentation.result().action()) {
                            case ACCEPT -> {
                                writeAccepted(writer, presentation);
                                summary.retagged++;
                            }
                            case SKIP -> {
                                writeThrough(writer, sequence);
                                summary.skipped++;
                            }
                            case EXIT -> {
                                writeThrough(writer, sequence);
                                summary.copiedAfterExit++;
                                exited = true;
                            }
                        }
                    }
                }
            }
        }

        emitSummary(summary);
    }

    /**
     * Validates the fresh-pass precondition shared by {@link #review(Path, Path)} and its CLI driver:
     * the input and output must be different paths, and the output must be absent or empty.
     *
     * @param inputFile the XML training-data file to review
     * @param outputFile the XML training-data file to write the corrected dataset to
     * @throws ReviewPreconditionException if {@code inputFile} and {@code outputFile} are the same
     *         path, or {@code outputFile} already exists and is non-empty
     * @throws IOException if probing the output file's size fails
     */
    static void validateFreshPass(Path inputFile, Path outputFile) throws IOException {
        if (inputFile.equals(outputFile)) {
            throw new ReviewPreconditionException("inputFile and outputFile must be different paths: " + inputFile);
        }
        if (Files.exists(outputFile) && Files.size(outputFile) > 0) {
            throw new ReviewPreconditionException(
                    "outputFile must be absent or empty for a fresh review pass: " + outputFile
            );
        }
    }

    /**
     * Writes an accepted misaligned sequence, assembling its segments from the configured tokenizer's
     * tokenization and the user-chosen tags.
     *
     * @param writer the output writer
     * @param presentation the configured tokenizer's tokenization paired with the user's tagging result
     * @throws IOException if writing or flushing fails
     * @throws IllegalArgumentException if the tagging result's tag count does not match the
     *         tokenization's token count
     */
    private void writeAccepted(TrainingSequenceWriter<T> writer, Presentation<T> presentation) throws IOException {
        writeThrough(
                writer,
                TrainingSequence.ofSegments(toSegments(presentation.tokenization(), presentation.result().finalTags()))
        );
    }

    /**
     * Writes a single sequence to the output and flushes immediately, so a crash leaves every
     * already-written sequence intact.
     *
     * @param writer the output writer
     * @param sequence the sequence to write
     * @throws IOException if writing or flushing fails
     */
    private void writeThrough(TrainingSequenceWriter<T> writer, TrainingSequence<T> sequence) throws IOException {
        writer.write(sequence);
        writer.flush();
    }

    /**
     * The configured {@link Tokenizer}'s tokenization for a misaligned sequence — the alignment
     * authority, independent of any tagger — paired with the user's tagging result. Keeping the
     * tokenization lets an accepted sequence be written with that tokenizer's token boundaries and
     * excluded runs without re-tokenizing.
     *
     * @param <T> the tag type
     */
    private record Presentation<T extends Comparable<T>> (Tokenization tokenization, TaggingResult<T> result) {}

    /**
     * Mutable per-status tally of a review pass. Every input sequence increments exactly one of the
     * five counters ({@link #aligned}, {@link #retagged}, {@link #skipped}, {@link #untokenizable},
     * {@link #copiedAfterExit}), which always sum to {@link #total}.
     */
    private static final class ReviewSummary {
        int aligned;
        int copiedAfterExit;
        int retagged;
        int skipped;
        final int total;
        int untokenizable;

        ReviewSummary(int total) {
            this.total = total;
        }
    }

    /**
     * Builder for {@link RetokenizeReviewer}.
     *
     * <p>
     * {@link #tagProvider(TagProvider)}, {@link #taggingInterface(TaggingInterface)},
     * {@link #terminal(Terminal)}, and {@link #tokenizer(Tokenizer)} are required. Unlike
     * {@link Annotator.Builder}, the tokenizer is always required: it is the alignment-detection
     * authority. A {@link #tagger(CrfTagger) tagger} is optional and supplies tag suggestions only.
     *
     * @param <T> the tag type
     */
    public static final class Builder<T extends Comparable<T>> {
        private @Nullable AlignmentStrategy alignmentStrategy;
        private @Nullable FeatureExtractor featureExtractor;
        private @Nullable CrfTagger<T> tagger;
        private @Nullable TagProvider<T> tagProvider;
        private @Nullable TaggingInterface<T> taggingInterface;
        private @Nullable Terminal terminal;
        private @Nullable Tokenizer tokenizer;
        private @Nullable FeatureExtractor verboseFeatureExtractor;

        private Builder() {}

        /**
         * Sets the strategy used to compare a stored token list against the re-tokenized surface when
         * deciding whether a sequence is aligned. May be {@code null}; when {@code null} the whole-list
         * exact-match strategy
         * ({@link org.coordinatekit.crf.core.align.AlignmentModels#exactMatchStrategy()}) is used, which
         * treats a sequence as aligned only when the re-tokenization reproduces the stored tokens exactly.
         *
         * @param alignmentStrategy the comparison strategy, or {@code null} to use the exact-match default
         * @return this builder
         */
        public Builder<T> alignmentStrategy(@Nullable AlignmentStrategy alignmentStrategy) {
            this.alignmentStrategy = alignmentStrategy;
            return this;
        }

        /**
         * Builds the reviewer.
         *
         * @return a new {@link RetokenizeReviewer}
         * @throws IllegalStateException if {@link #tagProvider(TagProvider) tagProvider},
         *         {@link #taggingInterface(TaggingInterface) taggingInterface}, {@link #terminal(Terminal)
         *         terminal}, or {@link #tokenizer(Tokenizer) tokenizer} have not been set, or if the
         *         supplied {@link TagProvider#tags()} set is empty
         */
        public RetokenizeReviewer<T> build() {
            if (tagProvider == null) {
                throw new IllegalStateException("tagProvider must be set");
            } else if (taggingInterface == null) {
                throw new IllegalStateException("taggingInterface must be set");
            } else if (terminal == null) {
                throw new IllegalStateException("terminal must be set");
            } else if (tokenizer == null) {
                throw new IllegalStateException("tokenizer must be set");
            } else if (tagProvider.tags().isEmpty()) {
                throw new IllegalStateException("tagProvider.tags() must not be empty");
            }

            return new RetokenizeReviewer<>(this);
        }

        /**
         * Sets the feature extractor used to compute the key display features shown by the feature view of
         * the tagging interface. May be {@code null}; when {@code null}, the key-feature view is not
         * offered. The extracted features are presentational only — they have no effect on tagging or the
         * written training data.
         *
         * @param featureExtractor the display feature extractor, or {@code null} to disable the feature
         *        display
         * @return this builder
         */
        public Builder<T> featureExtractor(@Nullable FeatureExtractor featureExtractor) {
            this.featureExtractor = featureExtractor;
            return this;
        }

        /**
         * Sets the CRF tagger used to suggest tags when re-tagging a misaligned sequence. May be
         * {@code null}. When present, the tagger tokenizes the surface and its suggestions seed the
         * presented tags; when absent, the configured {@link #tokenizer(Tokenizer)} splits the surface and
         * every token starts at {@link TagProvider#startingTag()}. The tagger never affects alignment
         * detection, which always uses the configured tokenizer.
         *
         * @param tagger the tagger, or {@code null} to re-tag without suggestions
         * @return this builder
         */
        public Builder<T> tagger(@Nullable CrfTagger<T> tagger) {
            this.tagger = tagger;
            return this;
        }

        /**
         * Sets the tag provider, whose {@link TagProvider#tags()} set defines the tag space.
         *
         * @param tagProvider the tag provider
         * @return this builder
         */
        public Builder<T> tagProvider(TagProvider<T> tagProvider) {
            this.tagProvider = Objects.requireNonNull(tagProvider, "tagProvider must not be null");
            return this;
        }

        /**
         * Sets the tagging interface used to present each misaligned sequence to the user.
         *
         * @param taggingInterface the tagging interface
         * @return this builder
         */
        public Builder<T> taggingInterface(TaggingInterface<T> taggingInterface) {
            this.taggingInterface = Objects.requireNonNull(taggingInterface, "taggingInterface must not be null");
            return this;
        }

        /**
         * Sets the JLine terminal used to emit untokenizable warnings and the closing summary. Ownership is
         * not transferred; the caller is responsible for closing it.
         *
         * @param terminal the terminal
         * @return this builder
         */
        public Builder<T> terminal(Terminal terminal) {
            this.terminal = Objects.requireNonNull(terminal, "terminal must not be null");
            return this;
        }

        /**
         * Sets the tokenizer used to re-tokenize each sequence's surface. This is the alignment-detection
         * authority — a sequence is misaligned when this tokenizer's output differs from the stored tokens
         * — and it also splits surfaces for presentation when no {@link #tagger(CrfTagger) tagger} is
         * configured. Required.
         *
         * @param tokenizer the tokenizer
         * @return this builder
         */
        public Builder<T> tokenizer(Tokenizer tokenizer) {
            this.tokenizer = Objects.requireNonNull(tokenizer, "tokenizer must not be null");
            return this;
        }

        /**
         * Sets the feature extractor used to compute the verbose display features shown only by the
         * all-features view of the tagging interface. May be {@code null}; when {@code null} and a
         * {@link #tagger(CrfTagger) tagger} is configured, the all-features view falls back to the tagger's
         * embedded {@link TaggedPositionedToken#features() features}. Setting this extractor overrides that
         * fallback. The extracted features are presentational only and have no effect on tagging or the
         * written training data.
         *
         * @param verboseFeatureExtractor the verbose display feature extractor, or {@code null} to use the
         *        tagger fallback (or no verbose display)
         * @return this builder
         */
        public Builder<T> verboseFeatureExtractor(@Nullable FeatureExtractor verboseFeatureExtractor) {
            this.verboseFeatureExtractor = verboseFeatureExtractor;
            return this;
        }
    }
}
