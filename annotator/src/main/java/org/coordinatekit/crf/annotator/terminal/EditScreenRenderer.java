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
import static org.coordinatekit.crf.annotator.terminal.TerminalDisplay.NUMBER_COLUMN;
import static org.coordinatekit.crf.annotator.terminal.TerminalDisplay.TAG_COLUMN;

/**
 * Lays out an {@link EditViewModel} into a JLine {@link AttributedStringBuilder}: the header, the
 * token under edit, a table of candidate tags with their confidences, and the selection prompt. All
 * of the content it lays out is pre-derived on the view model.
 */
final class EditScreenRenderer {
    /**
     * Appends the edit screen described by {@code viewModel} to {@code builder}.
     *
     * @param builder the builder to append to
     * @param viewModel the screen description to lay out
     */
    void appendTo(AttributedStringBuilder builder, EditViewModel viewModel) {
        builder.append(viewModel.headerLine());
        builder.append(System.lineSeparator());
        builder.append(viewModel.tokenLine());
        builder.append(System.lineSeparator());

        TerminalTable.Builder table = TerminalTable.builder().column(NUMBER_COLUMN).column(TAG_COLUMN)
                .column(CONFIDENCE_COLUMN);
        for (EditViewModel.TagRow row : viewModel.tagRows()) {
            table.row(AttributedStyle.DEFAULT, row.number(), row.tagText(), row.confidenceText());
        }
        table.build().appendTo(builder);

        builder.append(viewModel.prompt());
    }
}
