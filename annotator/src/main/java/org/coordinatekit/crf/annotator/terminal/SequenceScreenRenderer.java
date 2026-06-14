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
import org.jline.utils.AttributedStyle;

import static org.coordinatekit.crf.annotator.terminal.TerminalDisplay.CONFIDENCE_COLUMN;
import static org.coordinatekit.crf.annotator.terminal.TerminalDisplay.FEATURE_SEPARATOR;
import static org.coordinatekit.crf.annotator.terminal.TerminalDisplay.NUMBER_COLUMN;
import static org.coordinatekit.crf.annotator.terminal.TerminalDisplay.TAG_COLUMN;

/**
 * Lays out a {@link TaggingViewModel} into a JLine {@link AttributedStringBuilder}: the header, the
 * token table (low-confidence rows highlighted), an optional wrapping feature table, and the footer
 * prompt. This is the only place the sequence screen's styling and {@link TerminalTable} assembly
 * live; all the content it lays out is pre-derived on the view model.
 */
final class SequenceScreenRenderer {
    private static final String FEATURES_COLUMN = "Features";
    private static final String TOKEN_COLUMN = "Token";

    private final int maxTokenDisplayWidth;

    /**
     * Creates a renderer that caps the token column at {@code maxTokenDisplayWidth} cells.
     *
     * @param maxTokenDisplayWidth the maximum token-column width, in terminal cells
     */
    SequenceScreenRenderer(int maxTokenDisplayWidth) {
        this.maxTokenDisplayWidth = maxTokenDisplayWidth;
    }

    /**
     * Appends the sequence screen described by {@code viewModel} to {@code builder}, wrapping the
     * feature column to {@code terminalWidth}.
     *
     * @param builder the builder to append to
     * @param viewModel the screen description to lay out
     * @param terminalWidth the terminal width used to wrap the feature column
     */
    void appendTo(AttributedStringBuilder builder, TaggingViewModel viewModel, int terminalWidth) {
        builder.append(viewModel.headerLine());
        builder.append(System.lineSeparator());

        TerminalTable.Builder tokenTable = TerminalTable.builder().column(NUMBER_COLUMN)
                .column(TOKEN_COLUMN, maxTokenDisplayWidth).column(TAG_COLUMN).column(CONFIDENCE_COLUMN);
        for (TaggingViewModel.TokenRow row : viewModel.tokenRows()) {
            AttributedStyle style = row.lowConfidence() ? AttributedStyle.BOLD.foreground(AttributedStyle.YELLOW)
                    : AttributedStyle.DEFAULT;
            tokenTable.row(style, row.number(), row.token(), row.tagText(), row.confidenceText());
        }
        tokenTable.build().appendTo(builder);

        if (viewModel.featureRows() != null) {
            builder.append(System.lineSeparator());
            TerminalTable.Builder featuresTable = TerminalTable.builder().terminalWidth(terminalWidth)
                    .column(NUMBER_COLUMN).column(TOKEN_COLUMN, maxTokenDisplayWidth)
                    .wrappingColumn(FEATURES_COLUMN, FEATURE_SEPARATOR);
            for (TaggingViewModel.FeatureRow row : viewModel.featureRows()) {
                featuresTable.row(AttributedStyle.DEFAULT, row.number(), row.token(), row.featuresText());
            }
            featuresTable.build().appendTo(builder);
        }

        builder.append(viewModel.footerPrompt());
    }
}
