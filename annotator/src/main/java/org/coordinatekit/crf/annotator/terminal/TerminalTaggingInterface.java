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
import org.coordinatekit.crf.annotator.TaggingAction;
import org.coordinatekit.crf.annotator.TaggingInterface;
import org.coordinatekit.crf.annotator.TaggingResult;
import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.preprocessing.FeatureFormat;
import org.coordinatekit.crf.core.spi.CrfServices;
import org.jline.terminal.Terminal;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

import static org.coordinatekit.crf.annotator.AnnotatorModels.taggingResult;
import static org.coordinatekit.crf.annotator.terminal.TaggingViewModels.editScreen;
import static org.coordinatekit.crf.annotator.terminal.TaggingViewModels.sequenceViewModel;

/**
 * A {@link TaggingInterface} implementation that drives a JLine-backed terminal.
 *
 * <p>
 * Each call to {@link #present(AnnotatorSequence)} runs a render, read, parse, and reduce/render
 * loop over its collaborators: a {@link TerminalScreen} owns the terminal I/O, an
 * {@link InputParser} maps input to a {@link TaggingCommand}, a {@link TaggingSession} owns the
 * tagging and undo state, and a {@link SequenceScreenRenderer} (or {@link EditScreenRenderer}) lays
 * out a view model built by {@link TaggingViewModels}. The terminal supplied at construction is
 * borrowed for the duration of the call; this class never closes it.
 *
 * <p>
 * Instances are constructed via the nested {@link Builder}. The builder validates that the supplied
 * {@link TagProvider}'s {@link TagProvider#tags() tags} set is non-empty so that the edit screen
 * has at least one tag to offer.
 *
 * @param <T> the tag type
 * @see Builder
 */
public final class TerminalTaggingInterface<T extends Comparable<T>> implements TaggingInterface<T> {
    private final EditInputParser editInputParser;
    private final EditScreenRenderer editScreenRenderer;
    private final FeatureFormat featureFormat;
    private final FeatureViewState featureViewState;
    private final InputParser inputParser;
    private final TerminalScreen screen;
    private final SequenceScreenRenderer sequenceScreenRenderer;
    private final TagProvider<T> tagProvider;
    private final double threshold;

    /**
     * Creates an interface from {@code builder}, wiring its collaborators over the supplied terminal.
     *
     * @param builder the builder holding the tag provider, terminal, and display settings
     */
    private TerminalTaggingInterface(Builder<T> builder) {
        this.tagProvider = Objects.requireNonNull(builder.tagProvider, "tagProvider must be set");
        this.threshold = builder.threshold;
        this.featureFormat = builder.featureFormat != null ? builder.featureFormat : CrfServices.featureFormat();
        this.screen = new TerminalScreen(Objects.requireNonNull(builder.terminal, "terminal must be set"));
        this.featureViewState = new FeatureViewState();
        this.inputParser = new InputParser();
        this.editInputParser = new EditInputParser();
        this.sequenceScreenRenderer = new SequenceScreenRenderer(builder.maxTokenDisplayWidth);
        this.editScreenRenderer = new EditScreenRenderer();
    }

    /**
     * Returns a new builder for {@link TerminalTaggingInterface}.
     *
     * @param <T> the tag type
     * @return a new builder with default values
     */
    public static <T extends Comparable<T>> Builder<T> builder() {
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
    public TaggingResult<T> present(AnnotatorSequence<T> sequence) {
        Objects.requireNonNull(sequence, "sequence must not be null");

        TaggingSession<T> session = TaggingSession.startingFrom(sequence, featureViewState);
        List<T> initialTags = sequence.tokens().stream().map(AnnotatorToken::initialTag).toList();
        Double originalTotal = sequence.probabilityOf(initialTags);
        while (true) {
            List<T> currentTags = session.currentTags();
            Double currentTotal = sequence.probabilityOf(currentTags);
            TaggingViewModel viewModel = sequenceViewModel(
                    sequence,
                    currentTags,
                    session.effectiveFeatureView(),
                    tagProvider,
                    featureFormat,
                    threshold,
                    currentTotal,
                    originalTotal
            );
            screen.render(sequenceScreenRenderer, viewModel);

            String input = screen.readLine();
            if (input == null) {
                return taggingResult(TaggingAction.EXIT, List.of());
            }

            TaggingCommand command = inputParser.parse(input, sequence.tokens().size());
            switch (command) {
                case TaggingCommand.Accept ignored -> {
                    return taggingResult(TaggingAction.ACCEPT, session.currentTags());
                }
                case TaggingCommand.Skip ignored -> {
                    return taggingResult(TaggingAction.SKIP, List.of());
                }
                case TaggingCommand.Exit ignored -> {
                    return taggingResult(TaggingAction.EXIT, List.of());
                }
                case TaggingCommand.EditToken edit -> editToken(sequence, session, edit.position());
                case TaggingCommand.ToggleAllFeatures toggle -> session.apply(toggle);
                case TaggingCommand.ToggleKeyFeatures toggle -> session.apply(toggle);
                case TaggingCommand.Undo undo -> session.apply(undo);
                case TaggingCommand.Noop ignored -> {
                    // Unrecognized or empty input: the loop redraws the sequence screen.
                }
            }
        }
    }

    /**
     * Runs the edit screen for the token at {@code position} until the annotator selects a tag, cancels,
     * or the input stream closes, recording the change on {@code session} when a different tag is chosen.
     *
     * @param sequence the sequence being annotated
     * @param session the session owning the tagging state
     * @param position the zero-based index of the token under edit
     */
    private void editToken(AnnotatorSequence<T> sequence, TaggingSession<T> session, int position) {
        EditScreen<T> editScreen = editScreen(sequence, position, tagProvider);
        T previousTag = session.currentTags().get(position);
        while (true) {
            screen.render(editScreenRenderer, editScreen.viewModel());

            String input = screen.readLine();
            if (input == null) {
                return;
            }

            EditCommand command = editInputParser.parse(input, editScreen.candidateTags().size());
            switch (command) {
                case EditCommand.Cancel ignored -> {
                    return;
                }
                case EditCommand.SelectTag select -> {
                    session.recordEdit(position, previousTag, editScreen.candidateTags().get(select.index()));
                    return;
                }
                case EditCommand.Noop ignored -> {
                    // Unrecognized or empty input: the loop redraws the edit screen.
                }
            }
        }
    }

    /**
     * Builder for {@link TerminalTaggingInterface}.
     *
     * <p>
     * {@link #tagProvider(TagProvider)} and {@link #terminal(Terminal)} are required. The other setters
     * carry sensible defaults: {@link #maxTokenDisplayWidth(int) maxTokenDisplayWidth} defaults to
     * {@code 30}, {@link #threshold(double) threshold} defaults to {@code 0.80}, and the
     * {@link #featureFormat(FeatureFormat) featureFormat} defaults to
     * {@link CrfServices#featureFormat()}.
     *
     * @param <T> the tag type
     */
    public static final class Builder<T extends Comparable<T>> {
        private @Nullable FeatureFormat featureFormat;
        private int maxTokenDisplayWidth = 30;
        private @Nullable TagProvider<T> tagProvider;
        private @Nullable Terminal terminal;
        private double threshold = 0.80;

        private Builder() {}

        /**
         * Builds the interface.
         *
         * @return a new {@link TerminalTaggingInterface}
         * @throws IllegalStateException if {@link #tagProvider(TagProvider)} or {@link #terminal(Terminal)}
         *         have not been set, or if the supplied {@link TagProvider#tags()} set is empty
         */
        public TerminalTaggingInterface<T> build() {
            if (tagProvider == null) {
                throw new IllegalStateException("tagProvider must be set");
            }
            if (terminal == null) {
                throw new IllegalStateException("terminal must be set");
            }
            if (tagProvider.tags().isEmpty()) {
                throw new IllegalStateException("tagProvider.tags() must not be empty");
            }
            return new TerminalTaggingInterface<>(this);
        }

        /**
         * Sets the feature format used to render each structured feature to its displayed string. Defaults
         * to {@link CrfServices#featureFormat()} — the single registered format, or the built-in fallback.
         * The display renders features through this format and never sees the model's flat strings
         * otherwise.
         *
         * @param featureFormat the feature format, must not be null
         * @return this builder
         */
        public Builder<T> featureFormat(FeatureFormat featureFormat) {
            this.featureFormat = Objects.requireNonNull(featureFormat, "featureFormat must not be null");
            return this;
        }

        /**
         * Sets the maximum display width (in terminal cells) used for the token column on the sequence
         * screen.
         *
         * @param maxTokenDisplayWidth the maximum width, must be positive
         * @return this builder
         * @throws IllegalArgumentException if {@code maxTokenDisplayWidth} is not positive
         */
        public Builder<T> maxTokenDisplayWidth(int maxTokenDisplayWidth) {
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
        public Builder<T> tagProvider(TagProvider<T> tagProvider) {
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
        public Builder<T> terminal(Terminal terminal) {
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
        public Builder<T> threshold(double threshold) {
            if (Double.isNaN(threshold) || threshold < 0.0 || threshold > 1.0) {
                throw new IllegalArgumentException("threshold must be in [0.0, 1.0], got: " + threshold);
            }
            this.threshold = threshold;
            return this;
        }
    }
}
