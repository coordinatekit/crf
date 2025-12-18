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

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StringTagProviderTest {
    @Test
    void constructor_startingTagNotInTags() {
        assertThrows(IllegalArgumentException.class, () -> new StringTagProvider(List.of("NOUN", "VERB"), "O"));
    }

    @Test
    void decode() {
        var provider = new StringTagProvider(List.of("O", "NOUN", "VERB"), "O");

        assertEquals("NOUN", provider.decode("NOUN"));
        assertEquals("O", provider.decode("UNKNOWN"));
        assertEquals("O", provider.decode(null));
    }

    @Test
    void decode_noTags() {
        var provider = new StringTagProvider("O");

        assertEquals("NOUN", provider.decode("NOUN"));
        assertEquals("UNKNOWN", provider.decode("UNKNOWN"));
        assertEquals("O", provider.decode(null));
    }

    @Test
    void encode() {
        var provider = new StringTagProvider(List.of("O", "NOUN", "VERB"), "O");

        assertEquals("NOUN", provider.encode("NOUN"));
    }

    @Test
    void startingTag() {
        var provider = new StringTagProvider(List.of("O", "NOUN", "VERB"), "O");

        assertEquals("O", provider.startingTag());
    }

    @Test
    void startingTag__noTags() {
        var provider = new StringTagProvider("O");

        assertEquals("O", provider.startingTag());
    }

    @Test
    void tags() {
        var provider = new StringTagProvider(List.of("O", "NOUN", "VERB"), "O");

        assertEquals(Set.of("O", "NOUN", "VERB"), provider.tags());
    }

    @Test
    void tags__returnsUnmodifiableSet() {
        var provider = new StringTagProvider(List.of("O", "NOUN", "VERB"), "O");

        assertThrows(UnsupportedOperationException.class, () -> provider.tags().add("ADJ"));
    }
}
