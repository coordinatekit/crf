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

import java.util.List;

/**
 * An immutable, terminal-free description of one edit screen: the header line, the token under
 * edit, one row per candidate tag, and the selection prompt. Cell contents are pre-formatted
 * strings, so a renderer only has to lay them out and a test can assert on them without scraping a
 * terminal.
 *
 * @param headerLine the sequence header shown above the screen
 * @param tokenLine the line naming the token under edit
 * @param tagRows the candidate tag rows, in canonical order
 * @param prompt the selection prompt shown below the table
 */
record EditViewModel(String headerLine, String tokenLine, List<TagRow> tagRows, String prompt) {
    EditViewModel {
        tagRows = List.copyOf(tagRows);
    }

    /**
     * A candidate tag row: the row number, the tag, and its confidence.
     *
     * @param number the one-based row number, as displayed
     * @param tagText the tag, as displayed
     * @param confidenceText the formatted confidence, or a placeholder when absent
     */
    record TagRow(String number, String tagText, String confidenceText) {}
}
