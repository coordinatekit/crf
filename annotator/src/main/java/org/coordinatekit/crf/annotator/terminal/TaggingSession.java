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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The reducer behind the control loop: input is parsed to a {@link TaggingCommand.Reducer} and then
 * applied here, so tagging and undo can be tested with no terminal and no rendering.
 *
 * <p>
 * This object holds state with two distinct lifetimes. The current tag for each token and the undo
 * history are <em>owned</em> by the session and live only for the one
 * {@link TerminalTaggingInterface#present} call it backs. The {@link FeatureViewState}, by
 * contrast, is a reference to state <em>shared</em> across every sequence the interface presents;
 * the feature-view toggles mutate it in place, which is exactly how the chosen feature view stays
 * sticky from one sequence to the next. Reads of that shared view are clamped to what the current
 * sequence can actually show.
 *
 * @param <T> the tag type
 */
final class TaggingSession<T> {
    private final List<T> currentTags;
    private final FeatureAvailability featureAvailability;

    /** Shared across every presented sequence; mutated in place so the feature view stays sticky. */
    private final FeatureViewState featureViewState;
    private final Deque<Edit<T>> undoStack = new ArrayDeque<>();

    private TaggingSession(
            List<T> currentTags,
            FeatureAvailability featureAvailability,
            FeatureViewState featureViewState
    ) {
        this.currentTags = currentTags;
        this.featureAvailability = featureAvailability;
        this.featureViewState = featureViewState;
    }

    /**
     * Starts a session for {@code sequence}, seeding each token's current tag from its initial tag and
     * sharing {@code featureViewState} so the feature view persists across sequences.
     *
     * @param sequence the sequence being presented
     * @param featureViewState the sticky feature-view state shared across sequences
     * @param <F> the feature type
     * @param <T> the tag type
     * @return a new session
     */
    static <F, T extends Comparable<T>> TaggingSession<T> startingFrom(
            AnnotatorSequence<F, T> sequence,
            FeatureViewState featureViewState
    ) {
        List<T> currentTags = new ArrayList<>(sequence.tokens().size());
        for (AnnotatorToken<F, T> token : sequence.tokens()) {
            currentTags.add(token.initialTag());
        }
        return new TaggingSession<>(currentTags, sequence.featureAvailability(), featureViewState);
    }

    /**
     * Applies a reducer {@code command} to the session: {@link TaggingCommand.Undo} reverts the most
     * recent tag change, and the two toggles step the feature view.
     *
     * @param command the command to apply
     */
    void apply(TaggingCommand.Reducer command) {
        switch (command) {
            case TaggingCommand.ToggleAllFeatures ignored -> toggleAllFeatures();
            case TaggingCommand.ToggleKeyFeatures ignored -> toggleKeyFeatures();
            case TaggingCommand.Undo ignored -> undo();
        }
    }

    /**
     * Returns an immutable snapshot of the current tag for each token, in token order.
     *
     * @return the current tags
     */
    List<T> currentTags() {
        return List.copyOf(currentTags);
    }

    /**
     * Returns the feature view this sequence can actually show, clamping the sticky view down toward
     * {@link FeatureView#NONE} when the sequence lacks the features it would require.
     *
     * @return the effective feature view
     */
    FeatureView effectiveFeatureView() {
        return switch (featureViewState.featureView()) {
            case KEY -> featureAvailability.keyAvailable() ? FeatureView.KEY : FeatureView.NONE;
            case ALL -> {
                if (featureAvailability.verboseAvailable()) {
                    yield FeatureView.ALL;
                }
                yield featureAvailability.keyAvailable() ? FeatureView.KEY : FeatureView.NONE;
            }
            case NONE -> FeatureView.NONE;
        };
    }

    /**
     * Records a tag change for the token at {@code position}, pushing the previous tag onto the undo
     * stack so it can be reverted. Does nothing when {@code chosenTag} equals {@code previousTag}.
     *
     * @param position the zero-based index of the token
     * @param previousTag the tag in effect before the change
     * @param chosenTag the newly chosen tag
     */
    void recordEdit(int position, T previousTag, T chosenTag) {
        if (!chosenTag.equals(previousTag)) {
            undoStack.push(new Edit<>(position, previousTag));
            currentTags.set(position, chosenTag);
        }
    }

    private void toggleAllFeatures() {
        if (featureAvailability.verboseAvailable()) {
            featureViewState
                    .featureView(effectiveFeatureView() == FeatureView.ALL ? FeatureView.NONE : FeatureView.ALL);
        }
    }

    private void toggleKeyFeatures() {
        if (featureAvailability.keyAvailable()) {
            featureViewState
                    .featureView(effectiveFeatureView() == FeatureView.KEY ? FeatureView.NONE : FeatureView.KEY);
        }
    }

    private void undo() {
        if (!undoStack.isEmpty()) {
            Edit<T> edit = undoStack.pop();
            currentTags.set(edit.position(), edit.previousTag());
        }
    }

    private record Edit<T> (int position, T previousTag) {}
}
