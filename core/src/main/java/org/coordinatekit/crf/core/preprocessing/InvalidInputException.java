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
package org.coordinatekit.crf.core.preprocessing;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Exception thrown when input provided to a preprocessing component is invalid.
 *
 * <p>
 * This exception captures the invalid input and an optional reason describing why the input was
 * rejected, which can be useful for error reporting and debugging.
 */
@NullMarked
public class InvalidInputException extends IllegalArgumentException {
    private final String input;
    private final @Nullable String reason;

    /**
     * Creates a new exception for invalid input without a specific reason.
     *
     * @param input the invalid input string
     */
    public InvalidInputException(String input) {
        this(input, null);
    }

    /**
     * Creates a new exception for invalid input with a specific reason.
     *
     * @param input the invalid input string
     * @param reason the reason why the input is invalid, or {@code null} if unspecified
     */
    public InvalidInputException(String input, @Nullable String reason) {
        super(
                String.format(
                        "The input sequence `%s` is invalid%s.",
                        input,
                        reason != null ? String.format(" with the following reason: `%s`", reason) : ""
                )
        );

        this.input = input;
        this.reason = reason;
    }

    /**
     * Returns the invalid input that caused this exception.
     *
     * @return the invalid input string
     */
    public String input() {
        return input;
    }

    /**
     * Returns the reason why the input is invalid.
     *
     * @return the reason string, or {@code null} if no reason was specified
     */
    @Nullable
    public String reason() {
        return reason;
    }
}
