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

import org.coordinatekit.crf.annotator.AnnotatorTestSupport;
import org.jline.utils.AttributedStringBuilder;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SequenceScreenRendererTest {
    private static final String BOLD_YELLOW = AnnotatorTestSupport.boldYellowEscape();

    record FeatureSectionParameters(
            String name,
            @Nullable List<TaggingViewModel.FeatureRow> featureRows,
            boolean expectFeaturesHeading,
            @Nullable String expectedFeatureCell
    ) {}

    static Stream<FeatureSectionParameters> appendTo__featureSectionVisibility() {
        return Stream.of(
                new FeatureSectionParameters("omits_section_when_feature_rows_null", null, false, null),
                new FeatureSectionParameters(
                        "renders_section_when_present",
                        List.of(new TaggingViewModel.FeatureRow("1", "The", "CAP")),
                        true,
                        "CAP"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void appendTo__featureSectionVisibility(FeatureSectionParameters parameters) {
        // ARRANGE //
        TaggingViewModel viewModel = new TaggingViewModel(
                "Sequence 1 of 1: The fox",
                List.of(new TaggingViewModel.TokenRow("1", "The", "DT", "0.9000", false)),
                parameters.featureRows(),
                "PROMPT"
        );

        // ACT //
        String output = render(viewModel);

        // ASSERT //
        assertEquals(
                parameters.expectFeaturesHeading(),
                output.contains("Features"),
                "expected the feature-section heading presence to match"
        );
        if (parameters.expectedFeatureCell() != null) {
            assertTrue(
                    output.contains(parameters.expectedFeatureCell()),
                    "expected the feature cell: " + parameters.expectedFeatureCell()
            );
        }
    }

    @Test
    void appendTo__rendersHeaderTableAndPrompt() {
        // ARRANGE //
        TaggingViewModel viewModel = new TaggingViewModel(
                "Sequence 1 of 1: The fox",
                List.of(
                        new TaggingViewModel.TokenRow("1", "The", "DT", "0.9000", false),
                        new TaggingViewModel.TokenRow("2", "fox", "NN", "0.5000", true)
                ),
                null,
                "Enter A to accept, the number to edit the token, S to skip, U to undo, or X to exit."
        );

        // ACT //
        String output = render(viewModel);

        // ASSERT //
        assertTrue(output.contains("Sequence 1 of 1: The fox"), "expected the header line");
        assertTrue(
                output.lines().anyMatch(
                        line -> line.contains("##") && line.contains("Token") && line.contains("Tag")
                                && line.contains("Confidence")
                ),
                "expected the column header row"
        );
        assertTrue(
                output.lines().anyMatch(line -> line.trim().matches("1\\s+The\\s+DT\\s+0\\.9000")),
                "expected the first token row"
        );
        assertTrue(
                output.contains("Enter A to accept, the number to edit the token, S to skip, U to undo, or X to exit."),
                "expected the footer prompt"
        );
    }

    @Test
    void appendTo__stylesLowConfidenceRowsOnly() {
        // ARRANGE //
        TaggingViewModel viewModel = new TaggingViewModel(
                "Sequence 1 of 1: The fox",
                List.of(
                        new TaggingViewModel.TokenRow("1", "The", "DT", "0.9000", false),
                        new TaggingViewModel.TokenRow("2", "fox", "NN", "0.5000", true)
                ),
                null,
                "PROMPT"
        );

        // ACT //
        String output = render(viewModel);

        // ASSERT //
        assertEquals(1, output.lines().filter(line -> line.contains(BOLD_YELLOW)).count());
        assertTrue(
                output.lines().anyMatch(line -> line.contains(BOLD_YELLOW) && line.contains("fox")),
                "expected the low-confidence row to be styled"
        );
    }

    private static String render(TaggingViewModel viewModel) {
        AttributedStringBuilder builder = new AttributedStringBuilder();
        new SequenceScreenRenderer(30).appendTo(builder, viewModel, 80);
        return builder.toAttributedString().toAnsi();
    }
}
