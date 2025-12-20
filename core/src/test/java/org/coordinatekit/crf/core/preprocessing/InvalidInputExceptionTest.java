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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InvalidInputExceptionTest {
    @Test
    void inputOnly() {
        InvalidInputException exception = new InvalidInputException("test input");

        assertEquals("test input", exception.input());
        assertNull(exception.reason());
        assertEquals("The input sequence `test input` is invalid.", exception.getMessage());
    }

    @Test
    void inputAndReason() {
        InvalidInputException exception = new InvalidInputException("test input", "test reason");

        assertEquals("test input", exception.input());
        assertEquals("test reason", exception.reason());
        assertEquals(
                "The input sequence `test input` is invalid with the following reason: `test reason`.",
                exception.getMessage()
        );
    }

    @Test
    void inputAndNullReason() {
        InvalidInputException exception = new InvalidInputException("test input", null);

        assertEquals("test input", exception.input());
        assertNull(exception.reason());
        assertEquals("The input sequence `test input` is invalid.", exception.getMessage());
    }
}
