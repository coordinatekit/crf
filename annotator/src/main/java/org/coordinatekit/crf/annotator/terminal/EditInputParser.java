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

/**
 * Maps a line of edit-screen input to an {@link EditCommand}. The mapping is total and pure: it has
 * no terminal, performs no I/O, and never throws. {@code C} cancels (case-insensitive), a number in
 * {@code [1, rowCount]} selects that candidate, and anything else becomes {@link EditCommand.Noop}.
 */
final class EditInputParser {
    /**
     * Parses {@code input} against {@code rowCount} candidate rows.
     *
     * @param input the raw line of input
     * @param rowCount the number of candidate tag rows, bounding a valid selection
     * @return the parsed command
     */
    EditCommand parse(String input, int rowCount) {
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return new EditCommand.Noop();
        }
        if (trimmed.equalsIgnoreCase("C")) {
            return new EditCommand.Cancel();
        }
        try {
            int value = Integer.parseInt(trimmed);
            if (value >= 1 && value <= rowCount) {
                return new EditCommand.SelectTag(value - 1);
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return new EditCommand.Noop();
    }
}
