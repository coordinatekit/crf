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

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Maps a line of sequence-screen input to a {@link TaggingCommand}. The mapping is total and pure:
 * it has no terminal, performs no I/O, and never throws. Letter commands are case-insensitive; any
 * input that is neither a recognized command nor a valid token index becomes
 * {@link TaggingCommand.Noop}.
 */
final class InputParser {
    /**
     * Parses {@code input} against {@code tokenCount} tokens.
     *
     * @param input the raw line of input
     * @param tokenCount the number of tokens in the sequence, bounding a valid token index
     * @return the parsed command
     */
    TaggingCommand parse(String input, int tokenCount) {
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return new TaggingCommand.Noop();
        }
        return switch (trimmed.toUpperCase(Locale.ROOT)) {
            case "A" -> new TaggingCommand.Accept();
            case "S" -> new TaggingCommand.Skip();
            case "X" -> new TaggingCommand.Exit();
            case "U" -> new TaggingCommand.Undo();
            case "F" -> new TaggingCommand.ToggleKeyFeatures();
            case "FA" -> new TaggingCommand.ToggleAllFeatures();
            default -> {
                Integer index = parseTokenIndex(trimmed, tokenCount);
                yield index == null ? new TaggingCommand.Noop() : new TaggingCommand.EditToken(index - 1);
            }
        };
    }

    /**
     * Parses {@code input} as a one-based token index and returns it when it falls within
     * {@code [1, tokenCount]}. Returns null when the input is not an integer or is out of range.
     *
     * @param input the trimmed user input
     * @param tokenCount the number of tokens in the sequence
     * @return the parsed index, or null when the input is not a valid token index
     */
    private @Nullable Integer parseTokenIndex(String input, int tokenCount) {
        try {
            int value = Integer.parseInt(input);
            if (value >= 1 && value <= tokenCount) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return null;
    }
}
