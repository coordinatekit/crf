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
package org.coordinatekit.crf.core.io;

import org.coordinatekit.crf.core.preprocessing.TrainingSequence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.coordinatekit.crf.core.preprocessing.TrainingSegments.excluded;
import static org.coordinatekit.crf.core.preprocessing.TrainingSegments.token;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class TrainingSequenceFixtures {
    // A document carrying a DOCTYPE declaration; with DTDs disabled both the read and
    // validateAppendable paths must reject it.
    // language=XML
    static final String DOCTYPE_DOCUMENT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE crf:Collection>
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema" xmlns="https://example.org/tags">
                <crf:Sequence><Noun>Fox</Noun></crf:Sequence>
            </crf:Collection>
            """;

    private TrainingSequenceFixtures() {}

    static void assertBrownFox(TrainingSequence<String> sequence) {
        assertEquals(2, sequence.size());
        assertEquals("Adjective", sequence.get(0).tag());
        assertEquals("Brown", sequence.get(0).token());
        assertEquals("Noun", sequence.get(1).tag());
        assertEquals("Fox", sequence.get(1).token());
    }

    static void assertContainsBrownFoxThenLazyDog(Path file, XmlTrainingData<String> data) throws IOException {
        try (var sequences = data.read(file)) {
            List<TrainingSequence<String>> actual = sequences.toList();
            assertEquals(2, actual.size());
            assertBrownFox(actual.get(0));
            assertLazySleepingDog(actual.get(1));
        }
    }

    static void assertLazySleepingDog(TrainingSequence<String> sequence) {
        assertEquals(3, sequence.size());
        assertEquals("Adjective", sequence.get(0).tag());
        assertEquals("Lazy", sequence.get(0).token());
        assertEquals("Adjective", sequence.get(1).tag());
        assertEquals("Sleeping", sequence.get(1).token());
        assertEquals("Noun", sequence.get(2).tag());
        assertEquals("Dog", sequence.get(2).token());
    }

    static TrainingSequence<String> brownFox() {
        return TrainingSequence.ofTokens(List.of("Brown", "Fox"), List.of("Adjective", "Noun"));
    }

    static TrainingSequence<String> brownFoxWithExcluded() {
        return TrainingSequence
                .ofSegments(List.of(token("Adjective", "Brown"), excluded(" "), token("Noun", "Fox"), excluded("!")));
    }

    static String emptyTagProviderMessage(Class<?> providerType) {
        return "The tag provider must contain at least one tag. "
                + "This can be accomplished by ensuring `tags()` returns a value on `" + providerType.getName() + "`.";
    }

    static TrainingSequence<String> lazySleepingDog() {
        return TrainingSequence.ofTokens(List.of("Lazy", "Sleeping", "Dog"), List.of("Adjective", "Adjective", "Noun"));
    }
}
