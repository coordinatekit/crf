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

import org.coordinatekit.crf.annotator.AnnotatorModels;
import org.coordinatekit.crf.annotator.AnnotatorSequence;
import org.coordinatekit.crf.annotator.FeatureAvailability;
import org.coordinatekit.crf.core.tag.TaggedSequence;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.TAG_PROVIDER;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.initialTagsOf;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.scoreMap;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.sequenceWith;
import static org.coordinatekit.crf.annotator.terminal.TaggingPrompts.ALL_VIEW_PROMPT;
import static org.coordinatekit.crf.annotator.terminal.TaggingPrompts.KEY_FEATURES_PROMPT;
import static org.coordinatekit.crf.annotator.terminal.TaggingPrompts.KEY_ONLY_VIEW_PROMPT;
import static org.coordinatekit.crf.annotator.terminal.TaggingPrompts.KEY_VIEW_PROMPT;
import static org.coordinatekit.crf.annotator.terminal.TaggingPrompts.SEQUENCE_PROMPT;
import static org.coordinatekit.crf.annotator.terminal.TaggingPrompts.VERBOSE_FEATURES_PROMPT;
import static org.coordinatekit.crf.annotator.terminal.TaggingPrompts.VERBOSE_ONLY_VIEW_PROMPT;
import static org.coordinatekit.crf.annotator.terminal.TaggingViewModels.editScreen;
import static org.coordinatekit.crf.annotator.terminal.TaggingViewModels.sequenceViewModel;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TaggingViewModelsTest {
    private static final List<String> TOKENS = List.of("The", "fox");

    record FeatureRowsParameters(String name, FeatureView effectiveView, List<String> expectedFeaturesText) {}

    record FooterPromptParameters(
            String name,
            FeatureAvailability availability,
            FeatureView effectiveView,
            String expectedPrompt
    ) {}

    record TotalLikelihoodParameters(
            String name,
            List<String> currentTags,
            @Nullable Double currentTotal,
            @Nullable Double originalTotal,
            @Nullable String expectedText
    ) {}

    @MethodSource
    @ParameterizedTest
    void footerPrompt(FooterPromptParameters parameters) {
        // ARRANGE //
        AnnotatorSequence<String, String> sequence = sequenceWith(parameters.availability());

        // ACT //
        TaggingViewModel viewModel = sequenceViewModel(
                sequence,
                initialTagsOf(sequence),
                parameters.effectiveView(),
                TAG_PROVIDER,
                0.80,
                null,
                null
        );

        // ASSERT //
        assertEquals(parameters.expectedPrompt(), viewModel.footerPrompt());
    }

    static Stream<FooterPromptParameters> footerPrompt() {
        return Stream.of(
                new FooterPromptParameters(
                        "no_features_legacy_prompt",
                        FeatureAvailability.NONE,
                        FeatureView.NONE,
                        SEQUENCE_PROMPT
                ),
                new FooterPromptParameters(
                        "key_only",
                        FeatureAvailability.KEY_ONLY,
                        FeatureView.NONE,
                        KEY_FEATURES_PROMPT
                ),
                new FooterPromptParameters(
                        "key_and_verbose",
                        FeatureAvailability.BOTH,
                        FeatureView.NONE,
                        KEY_FEATURES_PROMPT
                ),
                new FooterPromptParameters(
                        "verbose_only",
                        FeatureAvailability.VERBOSE_ONLY,
                        FeatureView.NONE,
                        VERBOSE_FEATURES_PROMPT
                ),
                new FooterPromptParameters(
                        "key_view_offers_hide_and_show_all",
                        FeatureAvailability.BOTH,
                        FeatureView.KEY,
                        KEY_VIEW_PROMPT
                ),
                new FooterPromptParameters(
                        "all_view_offers_show_key_only",
                        FeatureAvailability.BOTH,
                        FeatureView.ALL,
                        ALL_VIEW_PROMPT
                ),
                new FooterPromptParameters(
                        "key_only_view_offers_hide",
                        FeatureAvailability.KEY_ONLY,
                        FeatureView.KEY,
                        KEY_ONLY_VIEW_PROMPT
                ),
                new FooterPromptParameters(
                        "verbose_only_view_offers_hide",
                        FeatureAvailability.VERBOSE_ONLY,
                        FeatureView.ALL,
                        VERBOSE_ONLY_VIEW_PROMPT
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void sequenceViewModel__featureRows(FeatureRowsParameters parameters) {
        // ARRANGE //
        List<Set<String>> key = List.of(Set.of("CAP"), Set.of("ANIMAL", "LOWER"));
        List<Set<String>> verbose = List.of(Set.of("WINDOW"), Set.of("ANIMAL"));
        AnnotatorSequence<String, String> sequence = taggedSequence(key, verbose);

        // ACT //
        TaggingViewModel viewModel = sequenceViewModel(
                sequence,
                initialTagsOf(sequence),
                parameters.effectiveView(),
                TAG_PROVIDER,
                0.80,
                null,
                null
        );

        // ASSERT //
        assertNotNull(viewModel.featureRows());
        List<String> actual = viewModel.featureRows().stream().map(TaggingViewModel.FeatureRow::featuresText).toList();
        assertEquals(parameters.expectedFeaturesText(), actual);
    }

    static Stream<FeatureRowsParameters> sequenceViewModel__featureRows() {
        return Stream.of(
                new FeatureRowsParameters(
                        "key_view_shows_key_features",
                        FeatureView.KEY,
                        List.of("CAP", "ANIMAL, LOWER")
                ),
                new FeatureRowsParameters(
                        "all_view_shows_key_and_verbose_union",
                        FeatureView.ALL,
                        List.of("CAP, WINDOW", "ANIMAL, LOWER")
                )
        );
    }

    @Test
    void sequenceViewModel__formatsPlaceholdersForEmptyFeaturesAndAbsentConfidence() {
        // ARRANGE //
        AnnotatorSequence<String, String> sequence = AnnotatorModels
                .annotatorSequence(1, 1, TOKENS, TAG_PROVIDER, List.of(Set.of("CAP"), Set.of()), null);

        // ACT //
        TaggingViewModel viewModel = sequenceViewModel(
                sequence,
                initialTagsOf(sequence),
                FeatureView.KEY,
                TAG_PROVIDER,
                0.80,
                null,
                null
        );

        // ASSERT //
        assertEquals("—", viewModel.tokenRows().getFirst().confidenceText());
        assertNotNull(viewModel.featureRows());
        assertEquals("CAP", viewModel.featureRows().getFirst().featuresText());
        assertEquals("—", viewModel.featureRows().get(1).featuresText());
    }

    @Test
    void sequenceViewModel__buildsTokenRowsWithLowConfidenceFlag() {
        // ARRANGE //
        AnnotatorSequence<String, String> sequence = taggedSequence(null, null);

        // ACT //
        TaggingViewModel viewModel = sequenceViewModel(
                sequence,
                initialTagsOf(sequence),
                FeatureView.NONE,
                TAG_PROVIDER,
                0.80,
                null,
                null
        );

        // ASSERT //
        assertEquals("Sequence 1 of 1: The fox", viewModel.headerLine());
        assertEquals(
                List.of(
                        new TaggingViewModel.TokenRow("1", "The", "DT", "0.9000", false),
                        new TaggingViewModel.TokenRow("2", "fox", "NN", "0.5000", true)
                ),
                viewModel.tokenRows()
        );
        assertNull(viewModel.featureRows());
    }

    @Test
    void sequenceViewModel__showsChosenTagConfidenceWithOriginalOnlyAfterEdit() {
        // ARRANGE //
        // Initial tags are the top-scoring tags: token 1 = DT (0.9), token 2 = NN (0.5). Editing
        // token 1 to NN diverges from its initial tag; token 2 keeps its initial tag.
        AnnotatorSequence<String, String> sequence = taggedSequence(null, null);

        // ACT //
        TaggingViewModel viewModel = sequenceViewModel(
                sequence,
                List.of("NN", "NN"),
                FeatureView.NONE,
                TAG_PROVIDER,
                0.80,
                null,
                null
        );

        // ASSERT //
        // The edited token shows its chosen tag's confidence and the original; the unedited one does not.
        assertEquals("0.1000 (was 0.9000)", viewModel.tokenRows().get(0).confidenceText());
        assertEquals("0.5000", viewModel.tokenRows().get(1).confidenceText());
    }

    @MethodSource
    @ParameterizedTest
    void sequenceViewModel__totalLikelihoodText(TotalLikelihoodParameters parameters) {
        // ARRANGE //
        AnnotatorSequence<String, String> sequence = taggedSequence(null, null);

        // ACT //
        TaggingViewModel viewModel = sequenceViewModel(
                sequence,
                parameters.currentTags(),
                FeatureView.NONE,
                TAG_PROVIDER,
                0.80,
                parameters.currentTotal(),
                parameters.originalTotal()
        );

        // ASSERT //
        assertEquals(parameters.expectedText(), viewModel.totalLikelihoodText());
    }

    static Stream<TotalLikelihoodParameters> sequenceViewModel__totalLikelihoodText() {
        return Stream.of(
                new TotalLikelihoodParameters(
                        "shows_original_after_divergent_edit",
                        List.of("NN", "NN"),
                        0.6210,
                        0.8804,
                        "0.6210 (was 0.8804)"
                ),
                new TotalLikelihoodParameters(
                        "omits_original_when_tags_unchanged",
                        List.of("DT", "NN"),
                        0.8804,
                        0.8804,
                        "0.8804"
                ),
                new TotalLikelihoodParameters("absent_without_scorer", List.of("DT", "NN"), null, null, null)
        );
    }

    @Test
    void editScreen__listsCandidateTagsInCanonicalOrder() {
        // ARRANGE //
        AnnotatorSequence<String, String> sequence = AnnotatorModels
                .annotatorSequence(1, 1, List.of("the", "fox"), TAG_PROVIDER);

        // ACT //
        EditScreen<String> editScreen = editScreen(sequence, 0, TAG_PROVIDER);

        // ASSERT //
        EditViewModel viewModel = editScreen.viewModel();
        assertEquals("Sequence 1 of 1: the fox", viewModel.headerLine());
        assertEquals("Token 1 of 2: the", viewModel.tokenLine());
        assertEquals(
                List.of(
                        new EditViewModel.TagRow("1", "DT", "—"),
                        new EditViewModel.TagRow("2", "NN", "—"),
                        new EditViewModel.TagRow("3", "VB", "—")
                ),
                viewModel.tagRows()
        );
        assertEquals(List.of("DT", "NN", "VB"), editScreen.candidateTags());
    }

    private static AnnotatorSequence<String, String> taggedSequence(
            @Nullable List<Set<String>> key,
            @Nullable List<Set<String>> verbose
    ) {
        Map<String, Double> firstScores = scoreMap("DT", 0.9, "NN", 0.1);
        Map<String, Double> secondScores = scoreMap("NN", 0.5, "VB", 0.5);
        TaggedSequence<String, String> tagged = new TaggedSequence<>(
                TOKENS,
                List.of(Set.of(), Set.of()),
                List.of(firstScores, secondScores)
        );
        return AnnotatorModels.annotatorSequence(1, 1, tagged, key, verbose);
    }
}
