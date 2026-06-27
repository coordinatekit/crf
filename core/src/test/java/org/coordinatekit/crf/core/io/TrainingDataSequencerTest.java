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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrainingDataSequencerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void read__opensFileAndDelegatesFileContents() throws IOException {
        // ARRANGE // the delegate reads the file's bytes, so the content is exercised end to end
        Path file = temporaryDirectory.resolve("training-data");
        Files.writeString(file, "Brown Fox");

        TrainingDataSequencer<String> sequencer = input -> {
            List<String> tokens = List.of(new String(input.readAllBytes(), StandardCharsets.UTF_8).split(" "));
            return Stream.of(TrainingSequence.ofTokens(tokens, tokens.stream().map(token -> "Word").toList()));
        };

        // ACT // read(Path) opens the file and feeds its bytes to the delegate
        List<TrainingSequence<String>> sequences;
        try (var stream = sequencer.read(file)) {
            sequences = stream.toList();
        }

        // ASSERT //
        assertEquals(1, sequences.size());
        TrainingSequence<String> sequence = sequences.get(0);
        assertEquals(2, sequence.size());
        assertEquals("Brown", sequence.get(0).token());
        assertEquals("Fox", sequence.get(1).token());
    }
}
