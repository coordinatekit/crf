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
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.WCWidth;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
    private static final String NUMBER_COLUMN = "##";
    private static final String NULL_VALUE_PLACEHOLDER = "—";
    private static final String TAG_COLUMN = "Tag";
    private static final String TOKEN_COLUMN = "Token";

    private final LineReader lineReader;
    private final int maxTokenDisplayWidth;
    private final TagProvider<T> tagProvider;
    private final Terminal terminal;
    private final double threshold;

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

    private List<TagRow<T>> buildEditRows(Map<T, @Nullable Double> scores) {
        List<TagRow<T>> rows = new ArrayList<>(scores.size());
        for (Map.Entry<T, @Nullable Double> entry : scores.entrySet()) {
            rows.add(new TagRow<>(entry.getKey(), entry.getValue()));
        }
        return rows;
    }

    private int displayWidth(CharSequence text) {
        int total = 0;
        int index = 0;
        int length = text.length();
        while (index < length) {
            int codePoint = Character.codePointAt(text, index);
            int width = WCWidth.wcwidth(codePoint);
            if (width > 0) {
                total += width;
            }
            index += Character.charCount(codePoint);
        }
        return total;
    }

    private String padToWidth(String text, int cells) {
        int width = displayWidth(text);
        if (width >= cells) {
            return text;
        }
        StringBuilder builder = new StringBuilder(text);
        builder.repeat(' ', cells - width);
        return builder.toString();
    }

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

    private @Nullable String readLine() {
        try {
            return lineReader.readLine();
        } catch (EndOfFileException | UserInterruptException exception) {
            return null;
        }
    }

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

        int numberWidth = Math.max(displayWidth(NUMBER_COLUMN), displayWidth(String.valueOf(rows.size())));
        int tagWidth = displayWidth(TAG_COLUMN);
        for (TagRow<T> row : rows) {
            tagWidth = Math.max(tagWidth, displayWidth(tagToString(row.tag())));
        }
        int confidenceWidth = displayWidth(CONFIDENCE_COLUMN);

        appendRow(
                builder,
                AttributedStyle.DEFAULT,
                padToWidth(NUMBER_COLUMN, numberWidth),
                padToWidth(TAG_COLUMN, tagWidth),
                padToWidth(CONFIDENCE_COLUMN, confidenceWidth)
        );
        builder.append(System.lineSeparator());
        appendRow(
                builder,
                AttributedStyle.DEFAULT,
                padToWidth("-".repeat(numberWidth), numberWidth),
                padToWidth("-".repeat(tagWidth), tagWidth),
                padToWidth("-".repeat(confidenceWidth), confidenceWidth)
        );
        builder.append(System.lineSeparator());

        for (int index = 0; index < rows.size(); index++) {
            TagRow<T> row = rows.get(index);
            String numberCell = padToWidth(String.valueOf(index + 1), numberWidth);
            String tagCell = padToWidth(tagToString(row.tag()), tagWidth);
            String confidenceCell = padToWidth(formatConfidence(row.score()), confidenceWidth);
            appendRow(builder, AttributedStyle.DEFAULT, numberCell, tagCell, confidenceCell);
            builder.append(System.lineSeparator());
        }

        builder.append("Enter the number to select the correct tag or C to cancel.");
        terminal.writer().println(builder.toAttributedString().toAnsi());
        terminal.writer().flush();
    }

    private void renderSequenceScreen(AnnotatorSequence<F, T> sequence, List<T> currentTags) {
        List<AnnotatorToken<F, T>> annotatorTokens = sequence.tokens();

        int numberWidth = Math.max(displayWidth(NUMBER_COLUMN), displayWidth(String.valueOf(annotatorTokens.size())));
        int tokenWidth = displayWidth(TOKEN_COLUMN);
        for (AnnotatorToken<F, T> annotatorToken : annotatorTokens) {
            // noinspection MathClampMigration
            tokenWidth = Math.max(tokenWidth, Math.min(maxTokenDisplayWidth, displayWidth(annotatorToken.token())));
        }
        int tagWidth = displayWidth(TAG_COLUMN);
        for (T tag : currentTags) {
            tagWidth = Math.max(tagWidth, displayWidth(tagToString(tag)));
        }
        int confidenceWidth = displayWidth(CONFIDENCE_COLUMN);

        AttributedStringBuilder builder = new AttributedStringBuilder();
        builder.append(sequenceHeaderLine(sequence));
        builder.append(System.lineSeparator());

        appendRow(
                builder,
                AttributedStyle.DEFAULT,
                padToWidth(NUMBER_COLUMN, numberWidth),
                padToWidth(TOKEN_COLUMN, tokenWidth),
                padToWidth(TAG_COLUMN, tagWidth),
                padToWidth(CONFIDENCE_COLUMN, confidenceWidth)
        );
        builder.append(System.lineSeparator());
        appendRow(
                builder,
                AttributedStyle.DEFAULT,
                padToWidth("-".repeat(numberWidth), numberWidth),
                padToWidth("-".repeat(tokenWidth), tokenWidth),
                padToWidth("-".repeat(tagWidth), tagWidth),
                padToWidth("-".repeat(confidenceWidth), confidenceWidth)
        );
        builder.append(System.lineSeparator());

        for (int index = 0; index < annotatorTokens.size(); index++) {
            AnnotatorToken<F, T> annotatorToken = annotatorTokens.get(index);
            Double confidence = annotatorToken.initialConfidence();
            boolean lowConfidence = confidence != null && confidence < threshold;
            AttributedStyle style = lowConfidence ? AttributedStyle.BOLD.foreground(AttributedStyle.YELLOW)
                    : AttributedStyle.DEFAULT;
            String numberCell = padToWidth(String.valueOf(index + 1), numberWidth);
            String tokenCell = padToWidth(truncateToWidth(annotatorToken.token(), tokenWidth), tokenWidth);
            String tagCell = padToWidth(tagToString(currentTags.get(index)), tagWidth);
            String confidenceCell = padToWidth(formatConfidence(confidence), confidenceWidth);
            appendRow(builder, style, numberCell, tokenCell, tagCell, confidenceCell);
            builder.append(System.lineSeparator());
        }

        builder.append("Enter A to accept, the number to edit the token, S to skip, U to undo, or X to exit.");
        terminal.writer().println(builder.toAttributedString().toAnsi());
        terminal.writer().flush();
    }

    private static void appendRow(AttributedStringBuilder builder, AttributedStyle style, String... cells) {
        for (int index = 0; index < cells.length; index++) {
            if (index > 0) {
                builder.append("  ", AttributedStyle.DEFAULT);
            }
            builder.append(new AttributedString(cells[index], style));
        }
    }

    private static String formatConfidence(@Nullable Double value) {
        if (value == null) {
            return NULL_VALUE_PLACEHOLDER;
        }
        return String.format(Locale.US, "%.4f", value);
    }

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

    private String sequenceHeaderLine(AnnotatorSequence<F, T> sequence) {
        return "Sequence " + String.format(Locale.US, "%,d", sequence.sequenceNumber()) + " of "
                + String.format(Locale.US, "%,d", sequence.totalSequences()) + ": "
                + sequence.tokens().stream().map(AnnotatorToken::token).collect(Collectors.joining(" "));
    }

    private String tagToString(T tag) {
        String encoded = tagProvider.encode(tag);
        return encoded != null ? encoded : String.valueOf(tag);
    }

    private String truncateToWidth(String text, int cells) {
        if (displayWidth(text) <= cells) {
            return text;
        }
        StringBuilder builder = new StringBuilder();
        int accumulated = 0;
        int index = 0;
        int length = text.length();
        int limit = cells - 1;
        while (index < length) {
            int codePoint = Character.codePointAt(text, index);
            int width = WCWidth.wcwidth(codePoint);
            int positiveWidth = Math.max(width, 0);
            if (accumulated + positiveWidth > limit) {
                break;
            }
            builder.appendCodePoint(codePoint);
            accumulated += positiveWidth;
            index += Character.charCount(codePoint);
        }
        builder.append('…');
        return builder.toString();
    }

    private record Edit<T> (int position, T previousTag) {}

    private record TagRow<T> (T tag, @Nullable Double score) {}

    /**
     * Builder for {@link JLineTaggingInterface}.
     *
     * <p>
     * {@link #tagProvider(TagProvider)} and {@link #terminal(Terminal)} are required. The other setters
     * carry sensible defaults: {@link #maxTokenDisplayWidth(int) maxTokenDisplayWidth} defaults to
     * {@code 30}.
     *
     * @param <F> the feature type carried on the annotator sequence
     * @param <T> the tag type
     */
    public static final class Builder<F, T extends Comparable<T>> {
        private int maxTokenDisplayWidth = 30;
        private @Nullable TagProvider<T> tagProvider;
        private @Nullable Terminal terminal;
        private final double threshold = 0.80;

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

    }
}
