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
package org.coordinatekit.crf.annotator;

import org.coordinatekit.crf.core.StringTagProvider;
import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.io.XmlTrainingData;
import org.coordinatekit.crf.core.preprocessing.TrainingPositionedToken;
import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Shared fixtures and helpers for the annotator unit and integration tests. */
final class AnnotatorTestSupport {
    static final TagProvider<String> TAG_PROVIDER = new StringTagProvider(Set.of("DT", "NN", "VB"), "NN");

    private AnnotatorTestSupport() {}

    static DumbTerminal dumbTerminal(String scriptedInput) throws IOException {
        return new DumbTerminal(
                "test",
                "ansi",
                new ByteArrayInputStream(scriptedInput.getBytes(StandardCharsets.UTF_8)),
                new ByteArrayOutputStream(),
                StandardCharsets.UTF_8
        );
    }

    static DumbTerminal quietTerminal() throws IOException {
        return dumbTerminal("");
    }

    static DumbTerminal rejectedTerminal() throws IOException {
        return new DumbTerminal(
                "test",
                Terminal.TYPE_DUMB,
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(),
                StandardCharsets.UTF_8
        );
    }

    static List<TrainingSequence<String>> readOutput(Path outputFile) throws IOException {
        XmlTrainingData<String> xml = new XmlTrainingData<>(TAG_PROVIDER);
        try (Stream<TrainingSequence<String>> stream = xml.read(outputFile)) {
            return stream.toList();
        }
    }

    static List<String> tagsOf(TrainingSequence<String> sequence) {
        return sequence.stream().map(TrainingPositionedToken::tag).toList();
    }

    static List<String> tokensOf(TrainingSequence<String> sequence) {
        return sequence.stream().map(TrainingPositionedToken::token).toList();
    }

    record TestOptions(Path input, @Nullable Path model, Path output, double threshold)
            implements AnnotatorCli.Options {}
}
