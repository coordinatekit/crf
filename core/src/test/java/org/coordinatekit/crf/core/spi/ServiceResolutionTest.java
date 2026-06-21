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
package org.coordinatekit.crf.core.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Tests {@link ServiceResolution}: the generic precedence
 * ({@code explicit > single discovered provider > fallback}) and the ambiguity failure, exercised
 * with synthetic {@link String} providers.
 */
class ServiceResolutionTest {
    @Test
    void resolve__explicitWins() {
        // ACT & ASSERT //
        assertEquals("explicit", ServiceResolution.resolve("Slot", "explicit", List.of("candidate"), "fallback"));
    }

    @Test
    void resolve__fallbackWhenEmpty() {
        // ACT & ASSERT //
        assertEquals("fallback", ServiceResolution.resolve("Slot", null, List.of(), "fallback"));
    }

    @Test
    void resolve__multipleProvidersThrowsAmbiguous() {
        // ACT //
        AmbiguousServiceException exception = assertThrows(
                AmbiguousServiceException.class,
                () -> ServiceResolution.resolve("Slot", null, List.of("first", "second"), "fallback")
        );

        // ASSERT //
        assertEquals("Slot", exception.serviceName());
        assertTrue(
                exception.getMessage().contains("multiple Slot"),
                "message should report the conflicting service; was: " + exception.getMessage()
        );
    }

    @Test
    void resolve__nullWhenEmptyAndNoFallback() {
        // ACT & ASSERT //
        assertNull(ServiceResolution.resolve("Slot", null, List.of(), null));
    }

    @Test
    void resolve__singleProviderWins() {
        // ACT & ASSERT //
        assertEquals("candidate", ServiceResolution.resolve("Slot", null, List.of("candidate"), "fallback"));
    }
}
