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

import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.TAG_PROVIDER;
import static org.coordinatekit.crf.annotator.AnnotatorTestSupport.readOutput;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.coordinatekit.crf.core.preprocessing.WhitespaceTokenizer;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

class AnnotatorIntegrationTest {
    private static final List<String> INPUT_LINES = List
            .of("the quick brown", "fox jumps over", "the lazy dog", "a second sentence", "one more line");

    record AnnotateParameters(
            String name,
            List<String> scripts,
            List<List<String>> expectedTokens,
            @Nullable List<List<String>> expectedTags
    ) {}

    static Stream<AnnotateParameters> annotate() {
        List<List<String>> threeAcceptedTokens = List.of(
                List.of("the", "quick", "brown"),
                List.of("the", "lazy", "dog"),
                List.of("a", "second", "sentence")
        );
        List<List<String>> threeAcceptedTags = List
                .of(List.of("NN", "NN", "NN"), List.of("NN", "VB", "NN"), List.of("NN", "NN", "NN"));
        return Stream.of(
                new AnnotateParameters(
                        "single_run_writes_three",
                        List.of("A\nS\n2\n3\nA\nA\nX\n"),
                        threeAcceptedTokens,
                        threeAcceptedTags
                ),
                new AnnotateParameters(
                        "skipped_line_reappears_and_accepted_on_rerun",
                        List.of("A\nS\n2\n3\nA\nA\nX\n", "A\nX\n"),
                        List.of(
                                List.of("the", "quick", "brown"),
                                List.of("the", "lazy", "dog"),
                                List.of("a", "second", "sentence"),
                                List.of("fox", "jumps", "over")
                        ),
                        null
                ),
                new AnnotateParameters(
                        "immediate_exit_rerun_leaves_accepted_unchanged",
                        List.of("A\nS\n2\n3\nA\nA\nX\n", "X\n"),
                        threeAcceptedTokens,
                        threeAcceptedTags
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void annotate(AnnotateParameters parameters, @TempDir Path tempDirectory) throws IOException {
        // ARRANGE //
        Path inputFile = writeInput(tempDirectory);
        Path outputFile = tempDirectory.resolve("out.xml");

        // ACT //
        for (String script : parameters.scripts()) {
            runAnnotator(inputFile, outputFile, script);
        }

        // ASSERT //
        List<TrainingSequence<String>> written = readOutput(outputFile);
        assertEquals(parameters.expectedTokens(), written.stream().map(AnnotatorTestSupport::tokensOf).toList());
        if (parameters.expectedTags() != null) {
            assertEquals(parameters.expectedTags(), written.stream().map(AnnotatorTestSupport::tagsOf).toList());
        }
    }

    private static void runAnnotator(Path inputFile, Path outputFile, String scriptedInput) throws IOException {
        ByteArrayInputStream stdin = new ByteArrayInputStream(scriptedInput.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        try (Terminal terminal = new DumbTerminal("test", "ansi", stdin, stdout, StandardCharsets.UTF_8)) {
            JLineTaggingInterface<String, String> ui = JLineTaggingInterface.<String, String>builder()
                    .tagProvider(TAG_PROVIDER).terminal(terminal).build();
            Annotator<String, String> annotator = Annotator.<String, String>builder().tagProvider(TAG_PROVIDER)
                    .taggingInterface(ui).terminal(terminal).tokenizer(new WhitespaceTokenizer()).build();
            annotator.annotate(inputFile, outputFile);
        }
    }

    private static Path writeInput(Path tempDirectory) throws IOException {
        Path inputFile = tempDirectory.resolve("input.txt");
        Files.writeString(inputFile, String.join("\n", INPUT_LINES) + "\n", StandardCharsets.UTF_8);
        return inputFile;
    }
}
