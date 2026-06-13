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
import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.io.TrainingSequenceWriter;
import org.coordinatekit.crf.core.io.XmlTrainingData;
import org.coordinatekit.crf.core.preprocessing.Segment;
import org.coordinatekit.crf.core.preprocessing.SegmentKind;
import org.coordinatekit.crf.core.preprocessing.Tokenization;
import org.coordinatekit.crf.core.preprocessing.TrainingPositionedToken;
import org.coordinatekit.crf.core.preprocessing.TrainingSegment;
import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.coordinatekit.crf.core.preprocessing.Tokenizer;
import org.coordinatekit.crf.core.tag.CrfTagger;
import org.coordinatekit.crf.core.tag.TaggedTokenization;
import org.jline.terminal.Terminal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static org.coordinatekit.crf.annotator.AnnotatorModels.annotatorSequence;
import static org.coordinatekit.crf.core.preprocessing.TrainingSegments.excluded;
import static org.coordinatekit.crf.core.preprocessing.TrainingSegments.token;

/**
 * Orchestrates an interactive annotation session over an input file of one-sequence-per-line text.
 *
 * <p>
 * Each non-blank input line is tokenized and presented through the configured
 * {@link TaggingInterface}. When the user accepts a sequence, the resulting tokens, tags, and the
 * excluded character runs the tokenizer dropped around them are appended to the output XML file as
 * a {@code <crf:Sequence>} element and flushed immediately, so a crash leaves a valid (or
 * recoverable) document with every confirmed sequence intact. The captured excluded runs make the
 * written sequence losslessly reconstructable: {@link TrainingSequence#surface()} reproduces the
 * original line exactly. Re-running against the same output file skips any input line whose
 * tokenization matches one already present in the output, compared by a 64-bit content fingerprint
 * of the token list (content-match resume).
 *
 * <p>
 * When a {@link CrfTagger} is configured it is the authoritative source of tokens and excluded
 * runs. Each line is tagged once, the tagger's tokenization is used as written, and its suggested
 * tags are always shown. When no tagger is configured the {@link Tokenizer} splits each line
 * instead and the user tags every token starting from {@link TagProvider#startingTag()}. At least
 * one of the two must be present.
 *
 * <p>
 * The "Sequence N of M" counters reported through {@link AnnotatorSequence} use a stable
 * denominator: {@code M} is the total number of non-blank input lines, including lines already
 * present in the output (so "of M" does not drift between sessions). {@code N} is a presentation
 * counter that increments only on sequences actually shown to the user — auto-skipped lines do not
 * advance it.
 *
 * <p>
 * Instances are constructed via the nested {@link Builder}.
 *
 * @param <F> the feature type carried by the tagger and the per-token entries
 * @param <T> the tag type
 * @see Builder
 */
@NullMarked
public final class Annotator<F, T extends Comparable<T>> {
    private final @Nullable CrfTagger<F, T> tagger;
    private final TagProvider<T> tagProvider;
    private final TaggingInterface<F, T> taggingInterface;
    private final Terminal terminal;
    private final @Nullable Tokenizer tokenizer;

    /**
     * Constructs an annotator from a populated builder.
     *
     * @param builder the builder providing configured fields; {@code tagProvider},
     *        {@code taggingInterface}, and {@code terminal} must be non-null
     * @throws NullPointerException if any required builder field is null
     */
    private Annotator(Builder<F, T> builder) {
        this.tagger = builder.tagger;
        this.tagProvider = Objects.requireNonNull(builder.tagProvider, "tagProvider must be set");
        this.taggingInterface = Objects.requireNonNull(builder.taggingInterface, "taggingInterface must be set");
        this.terminal = Objects.requireNonNull(builder.terminal, "terminal must be set");
        this.tokenizer = builder.tokenizer;
    }

    /**
     * Returns a new builder for {@link Annotator}.
     *
     * @param <F> the feature type carried by the tagger and the per-token entries
     * @param <T> the tag type
     * @return a new builder with no values set
     */
    public static <F, T extends Comparable<T>> Builder<F, T> builder() {
        return new Builder<>();
    }

    /**
     * Annotates the input file, appending accepted sequences to the output file.
     *
     * <p>
     * Walks {@code inputFile} line-by-line, skipping blank lines and any line whose token list is
     * already present in {@code outputFile}. Each remaining line is tokenized by the configured
     * {@link Tokenizer}, built into an {@link AnnotatorSequence}, and presented via the configured
     * {@link TaggingInterface}. On {@link TaggingAction#ACCEPT ACCEPT} the tokens, the user-chosen
     * tags, and the tokenizer's excluded runs are written and flushed to the output file. On
     * {@link TaggingAction#SKIP SKIP} the line is left unwritten and will be re-presented on the next
     * run. On {@link TaggingAction#EXIT EXIT} the writer is closed and this method returns.
     *
     * <p>
     * A one-line "Resumed: skipped K of N input lines already present in output." message is written to
     * the configured {@link Terminal} once per call — either immediately before the first presentation,
     * or after the input is exhausted if no presentation occurred.
     *
     * @param inputFile the file to read input lines from, UTF-8 encoded, one sequence per line
     * @param outputFile the XML training-data file to append accepted sequences to
     * @throws IOException if reading the input, reading the existing output, or writing the output
     *         fails
     */
    public void annotate(Path inputFile, Path outputFile) throws IOException {
        Set<Long> seenHashes = readSeenHashes(outputFile);
        int totalSequences = countNonBlankLines(inputFile);

        XmlTrainingData<T> xml = new XmlTrainingData<>(tagProvider);
        try (TrainingSequenceWriter<T> writer = xml.appendingWriter(outputFile);
                Stream<String> lineStream = Files.lines(inputFile, StandardCharsets.UTF_8)) {
            AnnotationState<T> state = new AnnotationState<>(writer, seenHashes, totalSequences);
            for (Iterator<String> lines = lineStream.iterator(); lines.hasNext();) {
                if (!processLine(state, lines.next())) {
                    return;
                }
            }
            if (!state.startupMessageEmitted) {
                state.startupMessageEmitted = true;
                emitResumeMessage(state.skipped, state.totalSequences);
            }
        }
    }

    /**
     * Counts the non-blank lines in the input file. Used to compute the stable {@code M} denominator
     * for "Sequence N of M" counters so it does not drift between sessions.
     *
     * @param inputFile the UTF-8 input file to scan
     * @return the number of lines for which {@link String#isBlank()} returns {@code false}
     * @throws IOException if reading the file fails
     */
    private int countNonBlankLines(Path inputFile) throws IOException {
        try (Stream<String> lines = Files.lines(inputFile, StandardCharsets.UTF_8)) {
            return (int) lines.filter(line -> !line.isBlank()).count();
        }
    }

    /**
     * Writes the one-line "Resumed: skipped K of N..." message to the configured terminal and flushes
     * it. Invoked exactly once per {@link #annotate(Path, Path)} call.
     *
     * @param skipped the number of input lines auto-skipped because their tokenization matched an
     *        existing entry in the output
     * @param totalSequences the total number of non-blank input lines
     */
    private void emitResumeMessage(int skipped, int totalSequences) {
        String message = String.format(
                Locale.getDefault(),
                "Resumed: skipped %,d of %,d input lines already present in output.%n",
                skipped,
                totalSequences
        );
        terminal.writer().write(message);
        terminal.writer().flush();
    }

    /**
     * Computes a 64-bit content fingerprint of a token list for content-match resume. Tokens are
     * encoded as UTF-8 with a NUL byte separator and fed through SHA-256; the leading 8 bytes of the
     * digest are returned as a {@code long}.
     *
     * @param tokens the token list to hash; order is significant
     * @return the 64-bit fingerprint
     * @throws AssertionError if SHA-256 is unavailable, which is forbidden by the JDK specification
     */
    private static long hashTokens(List<String> tokens) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is required by every JDK", exception);
        }
        for (String token : tokens) {
            digest.update(token.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        }
        return ByteBuffer.wrap(digest.digest()).getLong();
    }

    /**
     * Processes a single input line: tokenizes it, skips it if its fingerprint is already in the
     * output, otherwise presents it through the {@link TaggingInterface} and applies the user's action.
     * Emits the resume message on first presentation if not already emitted.
     *
     * @param state the mutable per-call state tracking counters, seen hashes, and the writer
     * @param line the raw input line (blank lines are ignored)
     * @return {@code true} to continue processing further lines, {@code false} when the user has
     *         requested {@link TaggingAction#EXIT EXIT}
     * @throws IOException if writing the accepted sequence to the output fails
     */
    private boolean processLine(AnnotationState<T> state, String line) throws IOException {
        if (line.isBlank()) {
            return true;
        }

        TaggedTokenization<F, T> tagged = tagger != null ? tagger.tag(line) : null;
        Tokenization tokenized = tagged != null ? tagged.tokenization()
                : Objects.requireNonNull(tokenizer).tokenize(line);
        List<String> tokens = tokenized.sequence().stream().map(PositionedToken::token).toList();

        long tokenHash = hashTokens(tokens);
        if (state.seenHashes.contains(tokenHash)) {
            state.skipped++;
            return true;
        }

        if (!state.startupMessageEmitted) {
            state.startupMessageEmitted = true;
            emitResumeMessage(state.skipped, state.totalSequences);
        }

        int currentPresentation = ++state.presentationNumber;
        AnnotatorSequence<F, T> displaySequence = tagged != null
                ? annotatorSequence(currentPresentation, state.totalSequences, tagged.taggedSequence())
                : annotatorSequence(currentPresentation, state.totalSequences, tokens, tagProvider);

        TaggingResult<T> result = taggingInterface.present(displaySequence);

        return switch (result.action()) {
            case ACCEPT -> {
                state.writer.write(TrainingSequence.ofSegments(toSegments(tokenized, result.finalTags())));
                state.writer.flush();
                state.seenHashes.add(tokenHash);
                yield true;
            }
            case SKIP -> true;
            case EXIT -> false;
        };
    }

    /**
     * Reads the existing output file and returns the set of token-list fingerprints already present,
     * used to auto-skip input lines whose tokenization matches a previously accepted sequence. Returns
     * an empty set if the output file does not exist or is empty.
     *
     * @param outputFile the XML training-data file to scan
     * @return a mutable set of {@link #hashTokens(List)} fingerprints for every sequence in the file
     * @throws IOException if reading the file fails
     */
    private Set<Long> readSeenHashes(Path outputFile) throws IOException {
        if (!Files.exists(outputFile) || Files.size(outputFile) == 0) {
            return new HashSet<>();
        }
        Set<Long> seenHashes = new HashSet<>();
        try (Stream<TrainingSequence<T>> stream = new XmlTrainingData<>(tagProvider).read(outputFile)) {
            stream.forEach(sequence -> {
                List<String> tokens = sequence.stream().map(TrainingPositionedToken::token).toList();
                seenHashes.add(hashTokens(tokens));
            });
        }
        return seenHashes;
    }

    /**
     * Assembles the ordered training segments for an accepted sequence by mapping each tokenizer
     * segment in document order: excluded runs pass through untagged, and each token segment is paired
     * with the user-chosen tag at the matching token index. The mapping is surface-preserving, so
     * {@link TrainingSequence#surface()} reproduces the original line exactly.
     *
     * @param tokenized the authoritative tokenization, carrying tokens and excluded runs as segments
     * @param finalTags the per-token tags chosen by the user, one per token in order
     * @return the segments in document order, ready for {@link TrainingSequence#ofSegments(List)}
     */
    private List<TrainingSegment<T>> toSegments(Tokenization tokenized, List<T> finalTags) {
        long tokenSegmentCount = tokenized.segments().stream().filter(segment -> segment.kind() == SegmentKind.TOKEN)
                .count();
        if (finalTags.size() != tokenSegmentCount) {
            throw new IllegalArgumentException(
                    String.format(
                            "Expected one tag per token segment: got %d tags for %d token segments.",
                            finalTags.size(),
                            tokenSegmentCount
                    )
            );
        }
        List<TrainingSegment<T>> segments = new ArrayList<>();
        int tokenIndex = 0;
        for (Segment segment : tokenized.segments()) {
            if (segment.kind() == SegmentKind.TOKEN) {
                segments.add(token(finalTags.get(tokenIndex++), segment.text()));
            } else {
                segments.add(excluded(segment.text()));
            }
        }
        return segments;
    }

    private static final class AnnotationState<T extends Comparable<T>> {
        int presentationNumber;
        final Set<Long> seenHashes;
        int skipped;
        boolean startupMessageEmitted;
        final int totalSequences;
        final TrainingSequenceWriter<T> writer;

        AnnotationState(TrainingSequenceWriter<T> writer, Set<Long> seenHashes, int totalSequences) {
            this.seenHashes = seenHashes;
            this.totalSequences = totalSequences;
            this.writer = writer;
        }
    }

    /**
     * Builder for {@link Annotator}.
     *
     * <p>
     * {@link #tagProvider(TagProvider)}, {@link #taggingInterface(TaggingInterface)}, and
     * {@link #terminal(Terminal)} are required. At least one of {@link #tokenizer(Tokenizer) tokenizer}
     * or {@link #tagger(CrfTagger) tagger} must be set: a tagger supplies both the tokenization and tag
     * suggestions, while a tokenizer alone drives the manual-only path — see the class-level Javadoc.
     *
     * @param <F> the feature type carried by the tagger and the per-token entries
     * @param <T> the tag type
     */
    public static final class Builder<F, T extends Comparable<T>> {
        private @Nullable CrfTagger<F, T> tagger;
        private @Nullable TagProvider<T> tagProvider;
        private @Nullable TaggingInterface<F, T> taggingInterface;
        private @Nullable Terminal terminal;
        private @Nullable Tokenizer tokenizer;

        private Builder() {}

        /**
         * Builds the annotator.
         *
         * @return a new {@link Annotator}
         * @throws IllegalStateException if {@link #tagProvider(TagProvider) tagProvider},
         *         {@link #taggingInterface(TaggingInterface) taggingInterface}, or
         *         {@link #terminal(Terminal) terminal} have not been set; if neither a
         *         {@link #tokenizer(Tokenizer) tokenizer} nor a {@link #tagger(CrfTagger) tagger} has been
         *         set; or if the supplied {@link TagProvider#tags()} set is empty
         */
        public Annotator<F, T> build() {
            if (tagProvider == null) {
                throw new IllegalStateException("tagProvider must be set");
            } else if (taggingInterface == null) {
                throw new IllegalStateException("taggingInterface must be set");
            } else if (terminal == null) {
                throw new IllegalStateException("terminal must be set");
            } else if (tagProvider.tags().isEmpty()) {
                throw new IllegalStateException("tagProvider.tags() must not be empty");
            } else if (tokenizer == null && tagger == null) {
                throw new IllegalStateException("at least one of tokenizer or tagger must be set");
            }

            return new Annotator<>(this);
        }

        /**
         * Sets the CRF tagger used to suggest tags for each input line. May be {@code null}. When present
         * the tagger is the authoritative source of tokens and excluded runs — its tokenization is used
         * directly and its suggestions are always shown — and the {@link #tokenizer(Tokenizer)} becomes
         * optional. When absent the tokenizer supplies the tokenization instead.
         *
         * @param tagger the tagger, or {@code null} to run without tag suggestions
         * @return this builder
         */
        public Builder<F, T> tagger(@Nullable CrfTagger<F, T> tagger) {
            this.tagger = tagger;
            return this;
        }

        /**
         * Sets the tag provider, whose {@link TagProvider#tags()} set defines the tag space.
         *
         * @param tagProvider the tag provider
         * @return this builder
         */
        public Builder<F, T> tagProvider(TagProvider<T> tagProvider) {
            this.tagProvider = Objects.requireNonNull(tagProvider, "tagProvider must not be null");
            return this;
        }

        /**
         * Sets the tagging interface used to present each sequence to the user.
         *
         * @param taggingInterface the tagging interface
         * @return this builder
         */
        public Builder<F, T> taggingInterface(TaggingInterface<F, T> taggingInterface) {
            this.taggingInterface = Objects.requireNonNull(taggingInterface, "taggingInterface must not be null");
            return this;
        }

        /**
         * Sets the JLine terminal used to emit the one-line resume message at session start. Ownership is
         * not transferred; the caller is responsible for closing it.
         *
         * @param terminal the terminal
         * @return this builder
         */
        public Builder<F, T> terminal(Terminal terminal) {
            this.terminal = Objects.requireNonNull(terminal, "terminal must not be null");
            return this;
        }

        /**
         * Sets the tokenizer used to split every input line into tokens and excluded runs. Required only
         * when no {@link #tagger(CrfTagger) tagger} is configured; when a tagger is present it supplies the
         * tokenization and this tokenizer is unused.
         *
         * @param tokenizer the tokenizer
         * @return this builder
         */
        public Builder<F, T> tokenizer(Tokenizer tokenizer) {
            this.tokenizer = Objects.requireNonNull(tokenizer, "tokenizer must not be null");
            return this;
        }
    }
}
