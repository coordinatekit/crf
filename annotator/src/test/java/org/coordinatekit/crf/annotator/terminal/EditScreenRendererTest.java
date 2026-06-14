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

import org.jline.utils.AttributedStringBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EditScreenRendererTest {
    record AppendToParameters(String name, List<EditViewModel.TagRow> tagRows, List<String> expectedRowPatterns) {}

    static Stream<AppendToParameters> appendTo() {
        return Stream.of(
                new AppendToParameters(
                        "populated_rows_render_number_tag_and_confidence",
                        List.of(
                                new EditViewModel.TagRow("1", "DT", "0.9000"),
                                new EditViewModel.TagRow("2", "NN", "0.1000")
                        ),
                        List.of("1\\s+DT\\s+0\\.9000", "2\\s+NN\\s+0\\.1000")
                ),
                new AppendToParameters("empty_rows_render_header_and_prompt_only", List.of(), List.of())
        );
    }

    @MethodSource
    @ParameterizedTest
    void appendTo(AppendToParameters parameters) {
        // ARRANGE //
        EditViewModel viewModel = new EditViewModel(
                "Sequence 1 of 1: the fox",
                "Token 1 of 2: the",
                parameters.tagRows(),
                "Enter the number to select the correct tag or C to cancel."
        );

        // ACT //
        String output = render(viewModel);

        // ASSERT //
        assertTrue(output.contains("Sequence 1 of 1: the fox"), "expected the header line");
        assertTrue(output.contains("Token 1 of 2: the"), "expected the token line");
        assertTrue(
                output.lines()
                        .anyMatch(line -> line.contains("##") && line.contains("Tag") && line.contains("Confidence")),
                "expected the column header row"
        );
        for (String rowPattern : parameters.expectedRowPatterns()) {
            assertTrue(
                    output.lines().anyMatch(line -> line.trim().matches(rowPattern)),
                    "expected a tag row matching: " + rowPattern
            );
        }
        assertTrue(
                output.contains("Enter the number to select the correct tag or C to cancel."),
                "expected the selection prompt"
        );
    }

    private static String render(EditViewModel viewModel) {
        AttributedStringBuilder builder = new AttributedStringBuilder();
        new EditScreenRenderer().appendTo(builder, viewModel);
        return builder.toAttributedString().toAnsi();
    }
}
