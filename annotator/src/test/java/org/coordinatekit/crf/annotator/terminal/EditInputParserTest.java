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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EditInputParserTest {
    record ParseParameters(String name, String input, int rowCount, EditCommand expected) {}

    static Stream<ParseParameters> parse() {
        return Stream.of(
                new ParseParameters("cancel", "C", 6, new EditCommand.Cancel()),
                new ParseParameters("cancel_lowercase", "c", 6, new EditCommand.Cancel()),
                new ParseParameters("cancel_trimmed", " c ", 6, new EditCommand.Cancel()),
                new ParseParameters("empty_is_noop", "", 6, new EditCommand.Noop()),
                new ParseParameters("blank_is_noop", "   ", 6, new EditCommand.Noop()),
                new ParseParameters("first_row", "1", 6, new EditCommand.SelectTag(0)),
                new ParseParameters("last_row", "6", 6, new EditCommand.SelectTag(5)),
                new ParseParameters("row_trimmed", " 2 ", 6, new EditCommand.SelectTag(1)),
                new ParseParameters("zero_is_noop", "0", 6, new EditCommand.Noop()),
                new ParseParameters("above_range_is_noop", "7", 6, new EditCommand.Noop()),
                new ParseParameters("non_numeric_is_noop", "abc", 6, new EditCommand.Noop())
        );
    }

    @MethodSource
    @ParameterizedTest
    void parse(ParseParameters parameters) {
        // ARRANGE //
        EditInputParser parser = new EditInputParser();

        // ACT //
        EditCommand command = parser.parse(parameters.input(), parameters.rowCount());

        // ASSERT //
        assertEquals(parameters.expected(), command);
    }
}
