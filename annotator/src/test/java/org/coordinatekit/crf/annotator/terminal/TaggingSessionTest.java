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

import org.coordinatekit.crf.annotator.FeatureAvailability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.sequenceWith;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TaggingSessionTest {
    record EffectiveViewParameters(
            String name,
            FeatureView stored,
            FeatureAvailability availability,
            FeatureView expected
    ) {}

    static Stream<EffectiveViewParameters> effectiveFeatureView() {
        return Stream.of(
                new EffectiveViewParameters(
                        "key_view_with_key_only",
                        FeatureView.KEY,
                        FeatureAvailability.KEY_ONLY,
                        FeatureView.KEY
                ),
                new EffectiveViewParameters(
                        "key_view_with_both",
                        FeatureView.KEY,
                        FeatureAvailability.BOTH,
                        FeatureView.KEY
                ),
                new EffectiveViewParameters(
                        "key_view_with_none",
                        FeatureView.KEY,
                        FeatureAvailability.NONE,
                        FeatureView.NONE
                ),
                new EffectiveViewParameters(
                        "key_view_with_verbose_only",
                        FeatureView.KEY,
                        FeatureAvailability.VERBOSE_ONLY,
                        FeatureView.NONE
                ),
                new EffectiveViewParameters(
                        "all_view_with_verbose_only",
                        FeatureView.ALL,
                        FeatureAvailability.VERBOSE_ONLY,
                        FeatureView.ALL
                ),
                new EffectiveViewParameters(
                        "all_view_with_both",
                        FeatureView.ALL,
                        FeatureAvailability.BOTH,
                        FeatureView.ALL
                ),
                new EffectiveViewParameters(
                        "all_view_with_key_only",
                        FeatureView.ALL,
                        FeatureAvailability.KEY_ONLY,
                        FeatureView.KEY
                ),
                new EffectiveViewParameters(
                        "all_view_with_none",
                        FeatureView.ALL,
                        FeatureAvailability.NONE,
                        FeatureView.NONE
                ),
                new EffectiveViewParameters("none_view", FeatureView.NONE, FeatureAvailability.BOTH, FeatureView.NONE)
        );
    }

    @Test
    void apply__toggleAllFeaturesIgnoredWhenVerboseUnavailable() {
        // ARRANGE //
        TaggingSession<String> session = TaggingSession
                .startingFrom(sequenceWith(FeatureAvailability.KEY_ONLY), new FeatureViewState());

        // ACT //
        session.apply(new TaggingCommand.ToggleAllFeatures());

        // ASSERT //
        assertEquals(FeatureView.NONE, session.effectiveFeatureView());
    }

    @Test
    void apply__toggleAllFeaturesShowsThenHides() {
        // ARRANGE //
        TaggingSession<String> session = TaggingSession
                .startingFrom(sequenceWith(FeatureAvailability.BOTH), new FeatureViewState());

        // ACT + ASSERT //
        session.apply(new TaggingCommand.ToggleAllFeatures());
        assertEquals(FeatureView.ALL, session.effectiveFeatureView());
        session.apply(new TaggingCommand.ToggleAllFeatures());
        assertEquals(FeatureView.NONE, session.effectiveFeatureView());
    }

    @Test
    void apply__toggleKeyFeaturesHonorsClampFromStoredAllView() {
        // ARRANGE //
        // A stored ALL view clamps to KEY on a key-only sequence, so a single F must hide it rather than
        // perform a dead ALL -> KEY step.
        FeatureViewState featureViewState = new FeatureViewState();
        featureViewState.featureView(FeatureView.ALL);
        TaggingSession<String> session = TaggingSession
                .startingFrom(sequenceWith(FeatureAvailability.KEY_ONLY), featureViewState);
        assertEquals(FeatureView.KEY, session.effectiveFeatureView());

        // ACT //
        session.apply(new TaggingCommand.ToggleKeyFeatures());

        // ASSERT //
        assertEquals(FeatureView.NONE, session.effectiveFeatureView());
    }

    @Test
    void apply__toggleKeyFeaturesIgnoredWhenKeyUnavailable() {
        // ARRANGE //
        TaggingSession<String> session = TaggingSession
                .startingFrom(sequenceWith(FeatureAvailability.VERBOSE_ONLY), new FeatureViewState());

        // ACT //
        session.apply(new TaggingCommand.ToggleKeyFeatures());

        // ASSERT //
        assertEquals(FeatureView.NONE, session.effectiveFeatureView());
    }

    @Test
    void apply__toggleKeyFeaturesShowsThenHides() {
        // ARRANGE //
        TaggingSession<String> session = TaggingSession
                .startingFrom(sequenceWith(FeatureAvailability.KEY_ONLY), new FeatureViewState());

        // ACT + ASSERT //
        session.apply(new TaggingCommand.ToggleKeyFeatures());
        assertEquals(FeatureView.KEY, session.effectiveFeatureView());
        session.apply(new TaggingCommand.ToggleKeyFeatures());
        assertEquals(FeatureView.NONE, session.effectiveFeatureView());
    }

    @Test
    void apply__undoIsNoOpWhenNothingRecorded() {
        // ARRANGE //
        TaggingSession<String> session = TaggingSession
                .startingFrom(sequenceWith(FeatureAvailability.NONE), new FeatureViewState());

        // ACT //
        session.apply(new TaggingCommand.Undo());

        // ASSERT //
        assertEquals(List.of("NN", "NN"), session.currentTags());
    }

    @MethodSource
    @ParameterizedTest
    void effectiveFeatureView(EffectiveViewParameters parameters) {
        // ARRANGE //
        FeatureViewState featureViewState = new FeatureViewState();
        featureViewState.featureView(parameters.stored());
        TaggingSession<String> session = TaggingSession
                .startingFrom(sequenceWith(parameters.availability()), featureViewState);

        // ACT //
        FeatureView effectiveView = session.effectiveFeatureView();

        // ASSERT //
        assertEquals(parameters.expected(), effectiveView);
    }

    @Test
    void recordEdit__appliesChangeThenUndoReverts() {
        // ARRANGE //
        TaggingSession<String> session = TaggingSession
                .startingFrom(sequenceWith(FeatureAvailability.NONE), new FeatureViewState());

        // ACT + ASSERT //
        session.recordEdit(0, "NN", "DT");
        assertEquals(List.of("DT", "NN"), session.currentTags());
        session.apply(new TaggingCommand.Undo());
        assertEquals(List.of("NN", "NN"), session.currentTags());
    }

    @Test
    void recordEdit__noOpWhenTagUnchanged() {
        // ARRANGE //
        TaggingSession<String> session = TaggingSession
                .startingFrom(sequenceWith(FeatureAvailability.NONE), new FeatureViewState());

        // ACT + ASSERT //
        session.recordEdit(0, "NN", "NN");
        assertEquals(List.of("NN", "NN"), session.currentTags());
        session.apply(new TaggingCommand.Undo());
        assertEquals(List.of("NN", "NN"), session.currentTags());
    }

    @Test
    void recordEdit__undoRevertsMostRecentFirst() {
        // ARRANGE //
        TaggingSession<String> session = TaggingSession
                .startingFrom(sequenceWith(FeatureAvailability.NONE), new FeatureViewState());

        // ACT + ASSERT //
        session.recordEdit(0, "NN", "DT");
        session.recordEdit(1, "NN", "VB");
        assertEquals(List.of("DT", "VB"), session.currentTags());
        session.apply(new TaggingCommand.Undo());
        assertEquals(List.of("DT", "NN"), session.currentTags());
        session.apply(new TaggingCommand.Undo());
        assertEquals(List.of("NN", "NN"), session.currentTags());
    }

    @Test
    void startingFrom__seedsCurrentTagsFromInitialTags() {
        // ACT //
        TaggingSession<String> session = TaggingSession
                .startingFrom(sequenceWith(FeatureAvailability.NONE), new FeatureViewState());

        // ASSERT //
        assertEquals(List.of("NN", "NN"), session.currentTags());
    }

}
