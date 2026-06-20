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
import org.coordinatekit.crf.core.io.TrainingSequenceWriter;
import org.coordinatekit.crf.core.io.XmlTrainingData;
import org.coordinatekit.crf.core.preprocessing.InvalidInputException;
import org.coordinatekit.crf.core.preprocessing.Segment;
import org.coordinatekit.crf.core.preprocessing.Segments;
import org.coordinatekit.crf.core.preprocessing.Tokenization;
import org.coordinatekit.crf.core.preprocessing.Tokenizer;
import org.coordinatekit.crf.core.preprocessing.TrainingPositionedToken;
import org.coordinatekit.crf.core.preprocessing.TrainingSegment;
import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static org.coordinatekit.crf.core.preprocessing.TrainingSegments.excluded;
import static org.coordinatekit.crf.core.preprocessing.TrainingSegments.token;

/** Shared fixtures and helpers for the annotator unit and integration tests. */
public final class AnnotatorTestSupport {
    public static final TagProvider<String> TAG_PROVIDER = new StringTagProvider(Set.of("DT", "NN", "VB"), "NN");

    private AnnotatorTestSupport() {}

    /** Returns the ANSI escape prefix emitted for a bold-yellow style, for asserting styled rows. */
    public static String boldYellowEscape() {
        AttributedStyle style = AttributedStyle.BOLD.foreground(AttributedStyle.YELLOW);
        String ansi = new AttributedString("X", style).toAnsi();
        return ansi.substring(0, ansi.indexOf('X'));
    }

    /**
     * Returns a {@link DumbTerminal} that writes to the caller-supplied {@code output}, so tests can
     * read back what the reviewer rendered (summaries, warnings). Co-locates the capture plumbing with
     * {@link #dumbTerminal} and {@link #quietTerminal}.
     */
    public static Terminal capturingTerminal(ByteArrayOutputStream output) throws IOException {
        return new DumbTerminal("test", "ansi", new ByteArrayInputStream(new byte[0]), output, StandardCharsets.UTF_8);
    }

    public static DumbTerminal dumbTerminal(String scriptedInput) throws IOException {
        return new DumbTerminal(
                "test",
                "ansi",
                new ByteArrayInputStream(scriptedInput.getBytes(StandardCharsets.UTF_8)),
                new ByteArrayOutputStream(),
                StandardCharsets.UTF_8
        );
    }

    /** Returns each token's initial tag, in token order. */
    public static <F, T extends Comparable<T>> List<T> initialTagsOf(AnnotatorSequence<F, T> sequence) {
        return sequence.tokens().stream().map(AnnotatorToken::initialTag).toList();
    }

    public static DumbTerminal quietTerminal() throws IOException {
        return dumbTerminal("");
    }

    public static DumbTerminal rejectedTerminal() throws IOException {
        return new DumbTerminal(
                "test",
                Terminal.TYPE_DUMB,
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(),
                StandardCharsets.UTF_8
        );
    }

    public static List<TrainingSequence<String>> readOutput(Path outputFile) throws IOException {
        XmlTrainingData<String> xml = new XmlTrainingData<>(TAG_PROVIDER);
        try (Stream<TrainingSequence<String>> stream = xml.read(outputFile)) {
            return stream.toList();
        }
    }

    public static <T> Map<T, Double> scoreMap(T firstTag, double firstScore, T secondTag, double secondScore) {
        Map<T, Double> scores = new LinkedHashMap<>();
        scores.put(firstTag, firstScore);
        scores.put(secondTag, secondScore);
        return scores;
    }

    public static <T> Map<T, Double> scoreMap(
            T firstTag,
            double firstScore,
            T secondTag,
            double secondScore,
            T thirdTag,
            double thirdScore
    ) {
        Map<T, Double> scores = new LinkedHashMap<>();
        scores.put(firstTag, firstScore);
        scores.put(secondTag, secondScore);
        scores.put(thirdTag, thirdScore);
        return scores;
    }

    /**
     * Returns a two-token ("the", "fox") annotator sequence whose key and verbose feature presence
     * matches {@code availability}, for exercising feature-view logic.
     */
    public static AnnotatorSequence<String, String> sequenceWith(FeatureAvailability availability) {
        List<String> tokens = List.of("the", "fox");
        List<Set<String>> key = List.of(Set.of("k0"), Set.of("k1"));
        List<Set<String>> verbose = List.of(Set.of("v0"), Set.of("v1"));
        return switch (availability) {
            case NONE -> AnnotatorModels.annotatorSequence(1, 1, tokens, TAG_PROVIDER, null, null);
            case KEY_ONLY -> AnnotatorModels.annotatorSequence(1, 1, tokens, TAG_PROVIDER, key, null);
            case VERBOSE_ONLY -> AnnotatorModels.annotatorSequence(1, 1, tokens, TAG_PROVIDER, null, verbose);
            case BOTH -> AnnotatorModels.annotatorSequence(1, 1, tokens, TAG_PROVIDER, key, verbose);
        };
    }

    public static List<String> tagsOf(TrainingSequence<String> sequence) {
        return sequence.stream().map(TrainingPositionedToken::tag).toList();
    }

    public static List<String> tokensOf(TrainingSequence<String> sequence) {
        return sequence.stream().map(TrainingPositionedToken::token).toList();
    }

    /**
     * Builds a training sequence whose tokens are joined by single spaces, with the spaces captured as
     * excluded runs so the surface round-trips. A token ending in punctuation (such as
     * {@code "Smith,"}) makes the sequence misalign under {@link PunctuationTokenizer}.
     */
    public static TrainingSequence<String> words(List<String> tokens, List<String> tags) {
        List<TrainingSegment<String>> segments = new ArrayList<>();
        for (int index = 0; index < tokens.size(); index++) {
            if (index > 0) {
                segments.add(excluded(" "));
            }
            segments.add(token(tags.get(index), tokens.get(index)));
        }
        return TrainingSequence.ofSegments(segments);
    }

    @SafeVarargs
    public static Path writeInput(Path directory, TrainingSequence<String>... sequences) throws IOException {
        return writeSequences(directory.resolve("input.xml"), sequences);
    }

    @SafeVarargs
    public static Path writeSequences(Path file, TrainingSequence<String>... sequences) throws IOException {
        XmlTrainingData<String> xml = new XmlTrainingData<>(TAG_PROVIDER);
        try (TrainingSequenceWriter<String> writer = xml.appendingWriter(file)) {
            for (TrainingSequence<String> sequence : sequences) {
                writer.write(sequence);
            }
        }
        return file;
    }

    /**
     * A whitespace tokenizer that additionally peels each chunk's trailing run of {@code ','} and
     * {@code '.'} characters into separate single-character tokens, and rejects any surface containing
     * {@code '?'}. Surfaces tokenized whole under the old data therefore misalign here.
     */
    @NullMarked
    public static final class PunctuationTokenizer implements Tokenizer {
        @Override
        public Tokenization tokenize(String input) {
            Objects.requireNonNull(input, "input must not be null");
            if (input.isBlank()) {
                throw new InvalidInputException(input, "The input string is blank");
            }
            if (input.indexOf('?') >= 0) {
                throw new InvalidInputException(input, "The input string contains an unsupported '?' character");
            }

            List<Segment> segments = new ArrayList<>();
            int index = 0;
            int length = input.length();
            while (index < length) {
                int whitespaceStart = index;
                while (index < length && Character.isWhitespace(input.charAt(index))) {
                    index++;
                }
                if (index > whitespaceStart) {
                    segments.add(Segments.excluded(input.substring(whitespaceStart, index)));
                }
                if (index >= length) {
                    break;
                }
                int chunkStart = index;
                while (index < length && !Character.isWhitespace(input.charAt(index))) {
                    index++;
                }
                String chunk = input.substring(chunkStart, index);
                int wordEnd = chunk.length();
                while (wordEnd > 0 && (chunk.charAt(wordEnd - 1) == ',' || chunk.charAt(wordEnd - 1) == '.')) {
                    wordEnd--;
                }
                if (wordEnd > 0) {
                    segments.add(Segments.token(chunk.substring(0, wordEnd)));
                }
                for (int position = wordEnd; position < chunk.length(); position++) {
                    segments.add(Segments.token(String.valueOf(chunk.charAt(position))));
                }
            }
            return new Tokenization(segments);
        }
    }

    record RetokenizeTestOptions(Path input, @Nullable Path model, Path output, double threshold)
            implements RetokenizeCli.Options {}

    record TestOptions(Path input, @Nullable Path model, Path output, double threshold)
            implements AnnotatorCli.Options {}
}
