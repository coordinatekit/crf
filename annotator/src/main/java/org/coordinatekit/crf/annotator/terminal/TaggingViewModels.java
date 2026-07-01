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
package org.coordinatekit.crf.annotator.terminal;

import org.coordinatekit.crf.annotator.AnnotatorSequence;
import org.coordinatekit.crf.annotator.AnnotatorToken;
import org.coordinatekit.crf.annotator.FeatureAvailability;
import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.preprocessing.Feature;
import org.coordinatekit.crf.core.preprocessing.FeatureFormat;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.coordinatekit.crf.annotator.terminal.TerminalDisplay.FEATURE_SEPARATOR;
import static org.coordinatekit.crf.annotator.terminal.TerminalDisplay.NULL_VALUE_PLACEHOLDER;

/**
 * Builds the terminal-free {@link TaggingViewModel} and {@link EditViewModel} screen descriptions
 * from a sequence and the current session state. Every method here is pure: the derivations that
 * turn tags, confidences, and features into displayed text live here, away from the terminal, so
 * they can be asserted on directly.
 */
final class TaggingViewModels {
    private static final String EDIT_PROMPT = "Enter the number to select the correct tag or C to cancel.";

    private TaggingViewModels() {}

    /**
     * Returns the features shown for {@code token} under {@code effectiveView}. The
     * {@link FeatureView#KEY} view shows only the token's key features; any other view shows the union
     * of its key and verbose features.
     *
     * @param token the token whose features are shown
     * @param effectiveView the active feature view
     * @param <T> the tag type
     * @return the features to display
     */
    private static <T extends Comparable<T>> Set<Feature> displayedFeatures(
            AnnotatorToken<T> token,
            FeatureView effectiveView
    ) {
        if (effectiveView == FeatureView.KEY) {
            return token.features();
        }
        Set<Feature> union = new HashSet<>(token.features());
        union.addAll(token.verboseFeatures());
        return union;
    }

    /**
     * Builds the edit screen for the token at {@code position}: the terminal-free view model (header,
     * the token under edit, and one row per candidate tag in canonical order) paired with the candidate
     * tags in that same order. Building the display rows and the selectable tags in a single pass keeps
     * them in lockstep, so the row a user picks indexes back to the right tag.
     *
     * @param sequence the sequence being annotated
     * @param position the zero-based index of the token under edit
     * @param tagProvider the tag provider used to render tags
     * @param <T> the tag type
     * @return the edit screen
     */
    static <T extends Comparable<T>> EditScreen<T> editScreen(
            AnnotatorSequence<T> sequence,
            int position,
            TagProvider<T> tagProvider
    ) {
        AnnotatorToken<T> token = sequence.tokens().get(position);
        String tokenLine = "Token " + formatCount(position + 1) + " of " + formatCount(sequence.tokens().size()) + ": "
                + token.token();
        List<EditViewModel.TagRow> tagRows = new ArrayList<>(token.alternativeTagScores().size());
        List<T> candidateTags = new ArrayList<>(token.alternativeTagScores().size());
        int number = 1;
        for (Map.Entry<T, @Nullable Double> entry : token.alternativeTagScores().entrySet()) {
            tagRows.add(
                    new EditViewModel.TagRow(
                            String.valueOf(number),
                            tagToString(tagProvider, entry.getKey()),
                            formatConfidence(entry.getValue())
                    )
            );
            candidateTags.add(entry.getKey());
            number++;
        }
        EditViewModel viewModel = new EditViewModel(sequenceHeaderLine(sequence), tokenLine, tagRows, EDIT_PROMPT);
        return new EditScreen<>(viewModel, candidateTags);
    }

    /**
     * Builds the footer prompt for the sequence screen. The accept, edit, skip, undo, and exit actions
     * are always offered. {@code F} steps the feature view down one rung (all to key, key to hidden)
     * and {@code FA} jumps straight to showing all features, so on the all-features screen {@code FA}
     * is dropped wherever {@code F} can step down to the key view. A verbose-only sequence has no key
     * rung, so its all-features screen instead offers {@code FA} to hide the features.
     *
     * @param availability which feature sources the sequence carries
     * @param effectiveView the feature view in effect for the sequence
     * @return the footer prompt line
     */
    private static String footerPrompt(FeatureAvailability availability, FeatureView effectiveView) {
        StringBuilder prompt = new StringBuilder("Enter A to accept, the number to edit the token,");
        switch (effectiveView) {
            case NONE -> {
                if (availability.keyAvailable()) {
                    prompt.append(" F to show key features,");
                } else if (availability.verboseAvailable()) {
                    prompt.append(" FA to show all features,");
                }
            }
            case KEY -> {
                prompt.append(" F to hide features,");
                if (availability.verboseAvailable()) {
                    prompt.append(" FA to show all features,");
                }
            }
            case ALL -> {
                if (availability.keyAvailable()) {
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
     * Formats a confidence {@code value} to four decimal places, or returns the
     * {@value TerminalDisplay#NULL_VALUE_PLACEHOLDER} placeholder when it is null.
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
     * Formats {@code value} as a grouped decimal count.
     *
     * @param value the value to format
     * @return the grouped count
     */
    private static String formatCount(int value) {
        return String.format(Locale.US, "%,d", value);
    }

    /**
     * Formats {@code features} as a sorted, comma-separated string, or returns the
     * {@value TerminalDisplay#NULL_VALUE_PLACEHOLDER} placeholder when the set is empty. Each feature
     * is rendered through {@code featureFormat} first, then the rendered strings are sorted — sorting
     * the rendered text rather than the features preserves the historical lexicographic display order,
     * which
     * {@link org.coordinatekit.crf.core.preprocessing.Feature#compareTo(org.coordinatekit.crf.core.preprocessing.Feature)}
     * would not (it orders by offset first).
     *
     * @param features the features to format
     * @param featureFormat the format rendering each feature to its displayed string
     * @return the formatted feature list
     */
    private static String formatFeatures(Set<Feature> features, FeatureFormat featureFormat) {
        if (features.isEmpty()) {
            return NULL_VALUE_PLACEHOLDER;
        }
        return features.stream().map(featureFormat::render).sorted().collect(Collectors.joining(FEATURE_SEPARATOR));
    }

    /**
     * Formats {@code current}, appending {@code (was <original>)} when {@code showOriginal} is set.
     * Used for both per-token confidence and the total likelihood so a changed value shows alongside
     * the one the model originally produced. Both values are formatted with
     * {@link #formatConfidence(Double)}, so either renders the
     * {@value TerminalDisplay#NULL_VALUE_PLACEHOLDER} placeholder when null.
     *
     * @param current the current value, or null when none is available
     * @param original the original value to show in parentheses, or null when none is available
     * @param showOriginal whether to append the original value
     * @return the formatted text
     */
    private static String formatWithOriginal(
            @Nullable Double current,
            @Nullable Double original,
            boolean showOriginal
    ) {
        String text = formatConfidence(current);
        return showOriginal ? text + " (was " + formatConfidence(original) + ")" : text;
    }

    /**
     * Builds the header line shown above both screens: the sequence's position within the batch
     * followed by its tokens joined with spaces.
     *
     * @param sequence the sequence to describe
     * @param <T> the tag type
     * @return the header line
     */
    private static <T extends Comparable<T>> String sequenceHeaderLine(AnnotatorSequence<T> sequence) {
        return "Sequence " + formatCount(sequence.sequenceNumber()) + " of " + formatCount(sequence.totalSequences())
                + ": " + sequence.tokens().stream().map(AnnotatorToken::token).collect(Collectors.joining(" "));
    }

    /**
     * Builds the sequence-screen description: the header, one row per token, an optional feature
     * section when {@code effectiveView} is not {@link FeatureView#NONE}, an optional total-likelihood
     * line, and the footer prompt.
     *
     * <p>
     * Each token's confidence reflects its <em>currently selected</em> tag, looked up from the token's
     * {@link AnnotatorToken#alternativeTagScores() alternative-tag scores} rather than the original
     * best tag. Once a token's current tag diverges from its {@link AnnotatorToken#initialTag() initial
     * tag}, the cell also shows the original value as {@code current (was original)}, and the
     * low-confidence highlight follows the current value.
     *
     * <p>
     * The total-likelihood line is {@code null} (omitted) when {@code currentTotal} is {@code null},
     * the no-model case. Otherwise it shows the current total, appending {@code (was originalTotal)}
     * when {@code currentTags} differs from the sequence's initial tags.
     *
     * @param sequence the sequence to present
     * @param currentTags the tag currently assigned to each token, in token order
     * @param effectiveView the feature view in effect for the sequence
     * @param tagProvider the tag provider used to render tags
     * @param featureFormat the format rendering each feature to its displayed string
     * @param threshold the confidence threshold below which a row is flagged low-confidence
     * @param currentTotal the total likelihood of {@code currentTags}, or {@code null} when no scorer
     *        is available (no model)
     * @param originalTotal the total likelihood of the sequence's initial tags, or {@code null} when no
     *        scorer is available (no model)
     * @param <T> the tag type
     * @return the sequence view model
     */
    static <T extends Comparable<T>> TaggingViewModel sequenceViewModel(
            AnnotatorSequence<T> sequence,
            List<T> currentTags,
            FeatureView effectiveView,
            TagProvider<T> tagProvider,
            FeatureFormat featureFormat,
            double threshold,
            @Nullable Double currentTotal,
            @Nullable Double originalTotal
    ) {
        List<AnnotatorToken<T>> tokens = sequence.tokens();
        List<TaggingViewModel.TokenRow> tokenRows = new ArrayList<>(tokens.size());
        boolean tagsChanged = false;
        for (int index = 0; index < tokens.size(); index++) {
            AnnotatorToken<T> token = tokens.get(index);
            T currentTag = currentTags.get(index);
            boolean changed = !currentTag.equals(token.initialTag());
            tagsChanged |= changed;
            Double current = token.alternativeTagScores().get(currentTag);
            boolean lowConfidence = current != null && current < threshold;
            tokenRows.add(
                    new TaggingViewModel.TokenRow(
                            String.valueOf(index + 1),
                            token.token(),
                            tagToString(tagProvider, currentTag),
                            formatWithOriginal(current, token.initialConfidence(), changed),
                            lowConfidence
                    )
            );
        }

        List<TaggingViewModel.FeatureRow> featureRows = null;
        if (effectiveView != FeatureView.NONE) {
            featureRows = new ArrayList<>(tokens.size());
            for (int index = 0; index < tokens.size(); index++) {
                AnnotatorToken<T> token = tokens.get(index);
                featureRows.add(
                        new TaggingViewModel.FeatureRow(
                                String.valueOf(index + 1),
                                token.token(),
                                formatFeatures(displayedFeatures(token, effectiveView), featureFormat)
                        )
                );
            }
        }

        String totalLikelihoodText = currentTotal == null ? null
                : formatWithOriginal(currentTotal, originalTotal, tagsChanged);

        return new TaggingViewModel(
                sequenceHeaderLine(sequence),
                tokenRows,
                featureRows,
                totalLikelihoodText,
                footerPrompt(sequence.featureAvailability(), effectiveView)
        );
    }

    /**
     * Renders {@code tag} for display, using the tag provider's encoding when available and falling
     * back to {@link String#valueOf(Object)} otherwise.
     *
     * @param tagProvider the tag provider
     * @param tag the tag to render
     * @param <T> the tag type
     * @return the display string for the tag
     */
    private static <T extends Comparable<T>> String tagToString(TagProvider<T> tagProvider, T tag) {
        String encoded = tagProvider.encode(tag);
        return encoded != null ? encoded : String.valueOf(tag);
    }
}
