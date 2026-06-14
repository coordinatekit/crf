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

import org.coordinatekit.crf.core.TagProvider;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.coordinatekit.crf.annotator.AnnotatorModels.taggingResult;

/**
 * A {@link TaggingInterface} implementation that drives a JLine-backed terminal.
 *
 * <p>
 * Each call to {@link #present(AnnotatorSequence)} renders a sequence screen, accepts a line of
 * input, and either returns a {@link TaggingResult} or transitions to the per-token edit screen.
 * The terminal supplied at construction is borrowed for the duration of the call; this class never
 * closes it.
 *
 * <p>
 * Instances are constructed via the nested {@link Builder}. The builder validates that the supplied
 * {@link TagProvider}'s {@link TagProvider#tags() tags} set is non-empty so that the edit screen
 * has at least one tag to offer.
 *
 * @param <F> the feature type carried on the annotator sequence
 * @param <T> the tag type
 * @see Builder
 */
public final class JLineTaggingInterface<F, T extends Comparable<T>> implements TaggingInterface<F, T> {
    private static final String CONFIDENCE_COLUMN = "Confidence";
    private static final String FEATURES_COLUMN = "Features";
    private static final String FEATURE_SEPARATOR = ", ";
    private static final String NUMBER_COLUMN = "##";
    private static final String NULL_VALUE_PLACEHOLDER = "—";
    private static final String TAG_COLUMN = "Tag";
    private static final String TOKEN_COLUMN = "Token";

    private final LineReader lineReader;
    private final int maxTokenDisplayWidth;
    private final TagProvider<T> tagProvider;
    private final Terminal terminal;
    private final double threshold;

    private FeatureView featureView = FeatureView.NONE;

    /**
     * Creates an interface from {@code builder}, building a JLine {@link LineReader} over the supplied
     * terminal with history disabled.
     *
     * @param builder the builder holding the tag provider, terminal, and display settings
     */
    private JLineTaggingInterface(Builder<F, T> builder) {
        this.maxTokenDisplayWidth = builder.maxTokenDisplayWidth;
        this.tagProvider = Objects.requireNonNull(builder.tagProvider, "tagProvider must be set");
        this.terminal = Objects.requireNonNull(builder.terminal, "terminal must be set");
        this.threshold = builder.threshold;
        this.lineReader = LineReaderBuilder.builder().terminal(this.terminal).variable(LineReader.HISTORY_SIZE, 0)
                .build();
    }

    /**
     * Returns a new builder for {@link JLineTaggingInterface}.
     *
     * @param <F> the feature type carried on the annotator sequence
     * @param <T> the tag type
     * @return a new builder with default values
     */
    public static <F, T extends Comparable<T>> Builder<F, T> builder() {
        return new Builder<>();
    }

    /**
     * Renders the sequence screen for {@code sequence} and processes input until the annotator accepts,
     * skips, or exits, or the input stream closes. Typing a token number opens the edit screen for that
     * token; {@code U} undoes the most recent tag change; {@code F} and {@code FA} toggle the feature
     * sections when the sequence carries features.
     *
     * @param sequence the sequence to present
     * @return the chosen action together with, for {@link TaggingAction#ACCEPT}, the tag assigned to
     *         each token
     */
    @Override
    public TaggingResult<T> present(AnnotatorSequence<F, T> sequence) {
        Objects.requireNonNull(sequence, "sequence must not be null");

        List<T> currentTags = new ArrayList<>(sequence.tokens().size());
        for (AnnotatorToken<F, T> annotatorToken : sequence.tokens()) {
            currentTags.add(annotatorToken.initialTag());
        }
        Deque<Edit<T>> undoStack = new ArrayDeque<>();

        while (true) {
            renderSequenceScreen(sequence, currentTags);
            String input = readLine();
            if (input == null) {
                return taggingResult(TaggingAction.EXIT, List.of());
            }
            String trimmed = input.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String normalized = trimmed.toUpperCase(Locale.ROOT);
            switch (normalized) {
                case "A":
                    return taggingResult(TaggingAction.ACCEPT, List.copyOf(currentTags));
                case "S":
                    return taggingResult(TaggingAction.SKIP, List.of());
                case "X":
                    return taggingResult(TaggingAction.EXIT, List.of());
                case "U":
                    if (!undoStack.isEmpty()) {
                        Edit<T> edit = undoStack.pop();
                        currentTags.set(edit.position(), edit.previousTag());
                    }
                    break;
                case "F":
                    if (sequence.featuresAvailable()) {
                        featureView = effectiveView(sequence) == FeatureView.KEY ? FeatureView.NONE : FeatureView.KEY;
                    }
                    break;
                case "FA":
                    if (sequence.verboseFeaturesAvailable()) {
                        featureView = effectiveView(sequence) == FeatureView.ALL ? FeatureView.NONE : FeatureView.ALL;
                    }
                    break;
                default:
                    Integer index = parseTokenIndex(trimmed, sequence.tokens().size());
                    if (index == null) {
                        break;
                    }
                    int position = index - 1;
                    T previousTag = currentTags.get(position);
                    T chosen = runEditScreen(sequence, position);
                    if (chosen != null && !chosen.equals(previousTag)) {
                        undoStack.push(new Edit<>(position, previousTag));
                        currentTags.set(position, chosen);
                    }
                    break;
            }
        }
    }

    /**
     * Builds the edit-screen rows from {@code scores}, one row per tag carrying its alternative-tag
     * score, which may be null when no score is available.
     *
     * @param scores the candidate tags mapped to their scores
     * @return the edit rows, one per entry in {@code scores}
     */
    private List<TagRow<T>> buildEditRows(Map<T, @Nullable Double> scores) {
        List<TagRow<T>> rows = new ArrayList<>(scores.size());
        for (Map.Entry<T, @Nullable Double> entry : scores.entrySet()) {
            rows.add(new TagRow<>(entry.getKey(), entry.getValue()));
        }
        return rows;
    }

    /**
     * Returns the features shown for {@code annotatorToken} under {@code featureView}. The
     * {@link FeatureView#KEY} view returns only the token's key features; any other view returns the
     * union of its key and verbose features.
     *
     * @param annotatorToken the token whose features are shown
     * @param featureView the active feature view
     * @return the features to display
     */
    private Set<F> displayedFeatures(AnnotatorToken<F, T> annotatorToken, FeatureView featureView) {
        if (featureView == FeatureView.KEY) {
            return annotatorToken.features();
        }
        Set<F> union = new HashSet<>(annotatorToken.features());
        union.addAll(annotatorToken.verboseFeatures());
        return union;
    }

    /**
     * Clamps the sticky {@link #featureView} to what the given sequence can actually render. The view
     * persists across sequences, but feature availability is fixed per batch, so this guards against a
     * stored view that a sequence cannot satisfy.
     *
     * @param sequence the sequence about to be rendered
     * @return the feature view the sequence can render, falling back toward {@link FeatureView#NONE}
     */
    private FeatureView effectiveView(AnnotatorSequence<F, T> sequence) {
        return switch (featureView) {
            case KEY -> sequence.featuresAvailable() ? FeatureView.KEY : FeatureView.NONE;
            case ALL -> {
                if (sequence.verboseFeaturesAvailable()) {
                    yield FeatureView.ALL;
                }
                yield sequence.featuresAvailable() ? FeatureView.KEY : FeatureView.NONE;
            }
            case NONE -> FeatureView.NONE;
        };
    }

    /**
     * Builds the footer prompt for the sequence screen. The accept, edit, skip, undo, and exit actions
     * are always offered. {@code F} steps the feature view down one rung (all to key, key to hidden)
     * and {@code FA} jumps straight to showing all features, so on the all-features screen {@code FA}
     * is dropped wherever {@code F} can step down to the key view. A verbose-only sequence has no key
     * rung, so its all-features screen instead offers {@code FA} to hide the features. The hints are
     * worded according to {@code effectiveView} and which feature sets the sequence carries.
     *
     * @param sequence the sequence being presented
     * @param effectiveView the feature view in effect for the sequence
     * @return the footer prompt line
     */
    private String footerPrompt(AnnotatorSequence<F, T> sequence, FeatureView effectiveView) {
        StringBuilder prompt = new StringBuilder("Enter A to accept, the number to edit the token,");
        switch (effectiveView) {
            case NONE -> {
                if (sequence.featuresAvailable()) {
                    prompt.append(" F to show key features,");
                } else if (sequence.verboseFeaturesAvailable()) {
                    prompt.append(" FA to show all features,");
                }
            }
            case KEY -> {
                prompt.append(" F to hide features,");
                if (sequence.verboseFeaturesAvailable()) {
                    prompt.append(" FA to show all features,");
                }
            }
            case ALL -> {
                if (sequence.featuresAvailable()) {
                    prompt.append(" F to show key features only,");
                } else {
                    prompt.append(" FA to hide features,");
                }
            }
        }
        prompt.append(" S to skip, U to undo, or X to exit.");
        return prompt.toString();
    }

    /**
     * Formats {@code features} as a sorted, comma-separated string, or returns the
     * {@value #NULL_VALUE_PLACEHOLDER} placeholder when the set is empty.
     *
     * @param features the features to format
     * @return the formatted feature list
     */
    private String formatFeatures(Set<F> features) {
        if (features.isEmpty()) {
            return NULL_VALUE_PLACEHOLDER;
        }
        return features.stream().map(String::valueOf).sorted().collect(Collectors.joining(FEATURE_SEPARATOR));
    }

    /**
     * Parses {@code input} as a one-based token index and returns it when it falls within
     * {@code [1, tokenCount]}. Returns null when the input is not an integer or is out of range.
     *
     * @param input the trimmed user input
     * @param tokenCount the number of tokens in the sequence
     * @return the parsed index, or null when the input is not a valid token index
     */
    private @Nullable Integer parseTokenIndex(String input, int tokenCount) {
        try {
            int value = Integer.parseInt(input);
            if (value >= 1 && value <= tokenCount) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return null;
    }

    /**
     * Reads a line from the terminal, returning null when the input stream signals end-of-file or the
     * user interrupts it, for example with Ctrl-D or Ctrl-C.
     *
     * @return the line read, or null when input ends
     */
    private @Nullable String readLine() {
        try {
            return lineReader.readLine();
        } catch (EndOfFileException | UserInterruptException exception) {
            return null;
        }
    }

    /**
     * Renders the per-token edit screen for the token at {@code position}: the sequence header, the
     * token under edit, a table of candidate tags with their confidences, and a prompt to select a tag
     * or cancel.
     *
     * @param sequence the sequence being annotated
     * @param position the zero-based index of the token under edit
     * @param rows the candidate tag rows to offer
     */
    private void renderEditScreen(AnnotatorSequence<F, T> sequence, int position, List<TagRow<T>> rows) {
        AttributedStringBuilder builder = new AttributedStringBuilder();
        builder.append(sequenceHeaderLine(sequence));
        builder.append(System.lineSeparator());
        builder.append("Token ");
        builder.append(String.format(Locale.US, "%,d", position + 1));
        builder.append(" of ");
        builder.append(String.format(Locale.US, "%,d", sequence.tokens().size()));
        builder.append(": ");
        builder.append(sequence.tokens().get(position).token());
        builder.append(System.lineSeparator());

        TerminalTable.Builder table = TerminalTable.builder().column(NUMBER_COLUMN).column(TAG_COLUMN)
                .column(CONFIDENCE_COLUMN);
        for (int index = 0; index < rows.size(); index++) {
            TagRow<T> row = rows.get(index);
            table.row(
                    AttributedStyle.DEFAULT,
                    String.valueOf(index + 1),
                    tagToString(row.tag()),
                    formatConfidence(row.score())
            );
        }
        table.build().appendTo(builder);

        builder.append("Enter the number to select the correct tag or C to cancel.");
        terminal.writer().println(builder.toAttributedString().toAnsi());
        terminal.writer().flush();
    }

    /**
     * Renders the sequence screen: the sequence header, a table of tokens with their current tags and
     * confidences (rows below the confidence threshold highlighted), an optional feature table when a
     * feature view is active, and the footer prompt.
     *
     * @param sequence the sequence to render
     * @param currentTags the tag currently assigned to each token, in token order
     */
    private void renderSequenceScreen(AnnotatorSequence<F, T> sequence, List<T> currentTags) {
        List<AnnotatorToken<F, T>> annotatorTokens = sequence.tokens();
        FeatureView effectiveView = effectiveView(sequence);

        TerminalTable.Builder tableBuilder = TerminalTable.builder().column(NUMBER_COLUMN)
                .column(TOKEN_COLUMN, maxTokenDisplayWidth).column(TAG_COLUMN).column(CONFIDENCE_COLUMN);
        for (int index = 0; index < annotatorTokens.size(); index++) {
            AnnotatorToken<F, T> annotatorToken = annotatorTokens.get(index);
            Double confidence = annotatorToken.initialConfidence();
            boolean lowConfidence = confidence != null && confidence < threshold;
            AttributedStyle style = lowConfidence ? AttributedStyle.BOLD.foreground(AttributedStyle.YELLOW)
                    : AttributedStyle.DEFAULT;
            tableBuilder.row(
                    style,
                    String.valueOf(index + 1),
                    annotatorToken.token(),
                    tagToString(currentTags.get(index)),
                    formatConfidence(confidence)
            );
        }
        TerminalTable table = tableBuilder.build();

        AttributedStringBuilder builder = new AttributedStringBuilder();
        builder.append(sequenceHeaderLine(sequence));
        builder.append(System.lineSeparator());
        table.appendTo(builder);

        if (effectiveView != FeatureView.NONE) {
            builder.append(System.lineSeparator());
            TerminalTable.Builder featuresTable = TerminalTable.builder().terminalWidth(terminal.getWidth())
                    .column(NUMBER_COLUMN).column(TOKEN_COLUMN, maxTokenDisplayWidth)
                    .wrappingColumn(FEATURES_COLUMN, FEATURE_SEPARATOR);
            for (int index = 0; index < annotatorTokens.size(); index++) {
                AnnotatorToken<F, T> annotatorToken = annotatorTokens.get(index);
                featuresTable.row(
                        AttributedStyle.DEFAULT,
                        String.valueOf(index + 1),
                        annotatorToken.token(),
                        formatFeatures(displayedFeatures(annotatorToken, effectiveView))
                );
            }
            featuresTable.build().appendTo(builder);
        }

        builder.append(footerPrompt(sequence, effectiveView));
        terminal.writer().println(builder.toAttributedString().toAnsi());
        terminal.writer().flush();
    }

    /**
     * Formats a confidence {@code value} to four decimal places, or returns the
     * {@value #NULL_VALUE_PLACEHOLDER} placeholder when it is null.
     *
     * @param value the confidence value, or null when none is available
     * @return the formatted confidence
     */
    private static String formatConfidence(@Nullable Double value) {
        if (value == null) {
            return NULL_VALUE_PLACEHOLDER;
        }
        return String.format(Locale.US, "%.4f", value);
    }

    /**
     * Runs the edit screen for the token at {@code position} until the annotator selects a tag or
     * cancels. Returns the selected tag, or null when the annotator cancels or input ends.
     *
     * @param sequence the sequence being annotated
     * @param position the zero-based index of the token under edit
     * @return the selected tag, or null when the edit is cancelled
     */
    private @Nullable T runEditScreen(AnnotatorSequence<F, T> sequence, int position) {
        List<TagRow<T>> rows = buildEditRows(sequence.tokens().get(position).alternativeTagScores());
        while (true) {
            renderEditScreen(sequence, position, rows);
            String input = readLine();
            if (input == null) {
                return null;
            }
            String trimmed = input.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.equalsIgnoreCase("C")) {
                return null;
            }
            try {
                int value = Integer.parseInt(trimmed);
                if (value >= 1 && value <= rows.size()) {
                    return rows.get(value - 1).tag();
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
    }

    /**
     * Builds the header line shown above both screens: the sequence's position within the batch
     * followed by its tokens joined with spaces.
     *
     * @param sequence the sequence to describe
     * @return the header line
     */
    private String sequenceHeaderLine(AnnotatorSequence<F, T> sequence) {
        return "Sequence " + String.format(Locale.US, "%,d", sequence.sequenceNumber()) + " of "
                + String.format(Locale.US, "%,d", sequence.totalSequences()) + ": "
                + sequence.tokens().stream().map(AnnotatorToken::token).collect(Collectors.joining(" "));
    }

    /**
     * Renders {@code tag} for display, using the tag provider's encoding when available and falling
     * back to {@link String#valueOf(Object)} otherwise.
     *
     * @param tag the tag to render
     * @return the display string for the tag
     */
    private String tagToString(T tag) {
        String encoded = tagProvider.encode(tag);
        return encoded != null ? encoded : String.valueOf(tag);
    }

    private record Edit<T> (int position, T previousTag) {}

    /** The sequence-screen feature section state: hidden, key features only, or key + verbose union. */
    private enum FeatureView {
        ALL, KEY, NONE
    }

    private record TagRow<T> (T tag, @Nullable Double score) {}

    /**
     * Builder for {@link JLineTaggingInterface}.
     *
     * <p>
     * {@link #tagProvider(TagProvider)} and {@link #terminal(Terminal)} are required. The other setters
     * carry sensible defaults: {@link #maxTokenDisplayWidth(int) maxTokenDisplayWidth} defaults to
     * {@code 30}, and {@link #threshold(double) threshold} defaults to {@code 0.80}.
     *
     * @param <F> the feature type carried on the annotator sequence
     * @param <T> the tag type
     */
    public static final class Builder<F, T extends Comparable<T>> {
        private int maxTokenDisplayWidth = 30;
        private @Nullable TagProvider<T> tagProvider;
        private @Nullable Terminal terminal;
        private double threshold = 0.80;

        private Builder() {}

        /**
         * Builds the interface.
         *
         * @return a new {@link JLineTaggingInterface}
         * @throws IllegalStateException if {@link #tagProvider(TagProvider)} or {@link #terminal(Terminal)}
         *         have not been set, or if the supplied {@link TagProvider#tags()} set is empty
         */
        public JLineTaggingInterface<F, T> build() {
            if (tagProvider == null) {
                throw new IllegalStateException("tagProvider must be set");
            }
            if (terminal == null) {
                throw new IllegalStateException("terminal must be set");
            }
            if (tagProvider.tags().isEmpty()) {
                throw new IllegalStateException("tagProvider.tags() must not be empty");
            }
            return new JLineTaggingInterface<>(this);
        }

        /**
         * Sets the maximum display width (in terminal cells) used for the token column on the sequence
         * screen.
         *
         * @param maxTokenDisplayWidth the maximum width, must be positive
         * @return this builder
         * @throws IllegalArgumentException if {@code maxTokenDisplayWidth} is not positive
         */
        public Builder<F, T> maxTokenDisplayWidth(int maxTokenDisplayWidth) {
            if (maxTokenDisplayWidth <= 0) {
                throw new IllegalArgumentException(
                        "maxTokenDisplayWidth must be positive, got: " + maxTokenDisplayWidth
                );
            }
            this.maxTokenDisplayWidth = maxTokenDisplayWidth;
            return this;
        }

        /**
         * Sets the tag provider, whose {@link TagProvider#tags()} set defines the tag space offered on the
         * edit screen.
         *
         * @param tagProvider the tag provider, must not be null
         * @return this builder
         */
        public Builder<F, T> tagProvider(TagProvider<T> tagProvider) {
            this.tagProvider = Objects.requireNonNull(tagProvider, "tagProvider must not be null");
            return this;
        }

        /**
         * Sets the JLine terminal used for input and output. Ownership is not transferred; the caller is
         * responsible for closing it.
         *
         * @param terminal the terminal, must not be null
         * @return this builder
         */
        public Builder<F, T> terminal(Terminal terminal) {
            this.terminal = Objects.requireNonNull(terminal, "terminal must not be null");
            return this;
        }

        /**
         * Sets the confidence threshold below which token rows are highlighted (bold + yellow) on the
         * sequence screen. A row is highlighted when its initial confidence is non-null and strictly less
         * than this value.
         *
         * @param threshold the threshold, in the closed interval {@code [0.0, 1.0]}
         * @return this builder
         * @throws IllegalArgumentException if {@code threshold} is outside {@code [0.0, 1.0]} or is
         *         {@code NaN}
         */
        public Builder<F, T> threshold(double threshold) {
            if (Double.isNaN(threshold) || threshold < 0.0 || threshold > 1.0) {
                throw new IllegalArgumentException("threshold must be in [0.0, 1.0], got: " + threshold);
            }
            this.threshold = threshold;
            return this;
        }
    }
}
