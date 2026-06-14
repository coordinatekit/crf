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

class InputParserTest {
    record ParseParameters(String name, String input, int tokenCount, TaggingCommand expected) {}

    static Stream<ParseParameters> parse() {
        return Stream.of(
                new ParseParameters("accept", "A", 3, new TaggingCommand.Accept()),
                new ParseParameters("accept_lowercase", "a", 3, new TaggingCommand.Accept()),
                new ParseParameters("accept_trimmed", " a ", 3, new TaggingCommand.Accept()),
                new ParseParameters("skip", "S", 3, new TaggingCommand.Skip()),
                new ParseParameters("skip_lowercase", "s", 3, new TaggingCommand.Skip()),
                new ParseParameters("exit", "X", 3, new TaggingCommand.Exit()),
                new ParseParameters("exit_lowercase", "x", 3, new TaggingCommand.Exit()),
                new ParseParameters("undo", "U", 3, new TaggingCommand.Undo()),
                new ParseParameters("undo_lowercase", "u", 3, new TaggingCommand.Undo()),
                new ParseParameters("toggle_key_features", "F", 3, new TaggingCommand.ToggleKeyFeatures()),
                new ParseParameters("toggle_key_features_lowercase", "f", 3, new TaggingCommand.ToggleKeyFeatures()),
                new ParseParameters("toggle_all_features", "FA", 3, new TaggingCommand.ToggleAllFeatures()),
                new ParseParameters("toggle_all_features_lowercase", "fa", 3, new TaggingCommand.ToggleAllFeatures()),
                new ParseParameters("toggle_all_features_trimmed", " fa ", 3, new TaggingCommand.ToggleAllFeatures()),
                new ParseParameters("empty_is_noop", "", 3, new TaggingCommand.Noop()),
                new ParseParameters("blank_is_noop", "   ", 3, new TaggingCommand.Noop()),
                new ParseParameters("first_token", "1", 3, new TaggingCommand.EditToken(0)),
                new ParseParameters("last_token", "3", 3, new TaggingCommand.EditToken(2)),
                new ParseParameters("token_trimmed", " 2 ", 3, new TaggingCommand.EditToken(1)),
                new ParseParameters("zero_is_noop", "0", 3, new TaggingCommand.Noop()),
                new ParseParameters("above_range_is_noop", "4", 3, new TaggingCommand.Noop()),
                new ParseParameters("non_numeric_is_noop", "abc", 3, new TaggingCommand.Noop())
        );
    }

    @MethodSource
    @ParameterizedTest
    void parse(ParseParameters parameters) {
        // ARRANGE //
        InputParser parser = new InputParser();

        // ACT //
        TaggingCommand command = parser.parse(parameters.input(), parameters.tokenCount());

        // ASSERT //
        assertEquals(parameters.expected(), command);
    }
}
