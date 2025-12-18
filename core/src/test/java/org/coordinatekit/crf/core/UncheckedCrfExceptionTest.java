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
package org.coordinatekit.crf.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UncheckedCrfExceptionTest {
    @Test
    void constructor__noArgs() {
        var exception = new UncheckedCrfException();

        assertNull(exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void constructor__message() {
        var exception = new UncheckedCrfException("test message");

        assertEquals("test message", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void constructor__messageAndCause() {
        var cause = new RuntimeException("cause");
        var exception = new UncheckedCrfException("test message", cause);

        assertEquals("test message", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void constructor__cause() {
        var cause = new RuntimeException("cause");
        var exception = new UncheckedCrfException(cause);

        assertEquals("java.lang.RuntimeException: cause", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void isRuntimeException() {
        assertInstanceOf(RuntimeException.class, new UncheckedCrfException());
    }
}
