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
            @Nullable String expectedFeatureCell
    ) {}

    record TotalLikelihoodParameters(
            String name,
            @Nullable String totalLikelihoodText,
            @Nullable String expectedText
    ) {}

    static Stream<FeatureSectionParameters> appendTo__featureSectionVisibility() {
        return Stream.of(
                new FeatureSectionParameters("omits_section_when_feature_rows_null", null, null),
                new FeatureSectionParameters(
                        "renders_section_when_present",
                        List.of(new TaggingViewModel.FeatureRow("1", "The", "CAP")),
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
                null,
                "PROMPT"
        );

        // ACT //
        String output = render(viewModel);

        // ASSERT //
        assertEquals(
                parameters.featureRows() != null,
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

    @MethodSource
    @ParameterizedTest
    void appendTo__totalLikelihoodLine(TotalLikelihoodParameters parameters) {
        // ARRANGE //
        TaggingViewModel viewModel = new TaggingViewModel(
                "Sequence 1 of 1: The fox",
                List.of(new TaggingViewModel.TokenRow("1", "The", "DT", "0.9000", false)),
                null,
                parameters.totalLikelihoodText(),
                "PROMPT"
        );

        // ACT //
        String output = render(viewModel);

        // ASSERT //
        assertEquals(
                parameters.totalLikelihoodText() != null,
                output.contains("Total likelihood:"),
                "expected the total-likelihood line presence to match"
        );
        if (parameters.expectedText() != null) {
            assertTrue(
                    output.contains(parameters.expectedText()),
                    "expected the total-likelihood text: " + parameters.expectedText()
            );
        }
    }

    static Stream<TotalLikelihoodParameters> appendTo__totalLikelihoodLine() {
        return Stream.of(
                new TotalLikelihoodParameters("omitted_when_null", null, null),
                new TotalLikelihoodParameters(
                        "rendered_when_present",
                        "0.6210 (was 0.8804)",
                        "Total likelihood: 0.6210 (was 0.8804)"
                )
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
