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
package org.coordinatekit.crf.core.align;

import static org.coordinatekit.crf.core.align.AlignmentModels.tokenDifference;
import static org.coordinatekit.crf.core.align.AlignmentTestSupport.assertThrowsWithMessage;
import static org.coordinatekit.crf.core.align.AlignmentTestSupport.defaultDetector;
import static org.coordinatekit.crf.core.align.AlignmentTestSupport.sequenceOf;
import static org.coordinatekit.crf.core.preprocessing.TrainingSegments.excluded;
import static org.coordinatekit.crf.core.preprocessing.TrainingSegments.token;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.coordinatekit.crf.core.StringTagProvider;
import org.coordinatekit.crf.core.align.AlignmentTestSupport.ExceptionCase;
import org.coordinatekit.crf.core.io.XmlTrainingData;
import org.coordinatekit.crf.core.preprocessing.InvalidInputException;
import org.coordinatekit.crf.core.preprocessing.Tokenizer;
import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.coordinatekit.crf.core.preprocessing.WhitespaceTokenizer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

class AlignmentDetectorTest {
    record AlignParameters(
            String name,
            boolean rejecting,
            int sequenceIndex,
            TrainingSequence<String> sequence,
            AlignmentStatus expectedStatus,
            List<String> expectedStoredTokens,
            List<String> expectedRetokenizedTokens,
            @Nullable String expectedFailureReasonContains
    ) {}

    record DetectParameters(
            String name,
            String xml,
            AlignmentStatus expectedStatus,
            List<String> expectedStoredTokens,
            List<String> expectedRetokenizedTokens,
            List<TokenDifference> expectedDifferences
    ) {}

    // language=XML
    private static final String ALIGNED_XML = """
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema">
                <crf:Sequence>
                    <Adjective>Brown</Adjective>
                    <crf:Excluded> </crf:Excluded>
                    <Noun>Fox</Noun>
                </crf:Sequence>
            </crf:Collection>
            """;
    // language=XML
    private static final String EMPTY_XML = """
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema" />
            """;
    // language=XML
    private static final String MERGE_XML = """
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema">
                <crf:Sequence>
                    <Unknown>Salt</Unknown>
                    <Unknown>Lake</Unknown>
                </crf:Sequence>
            </crf:Collection>
            """;
    // language=XML
    private static final String MIXED_XML = """
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema">
                <crf:Sequence>
                    <Adjective>Brown</Adjective>
                    <crf:Excluded> </crf:Excluded>
                    <Noun>Fox</Noun>
                </crf:Sequence>
                <crf:Sequence>
                    <Noun>New York</Noun>
                </crf:Sequence>
            </crf:Collection>
            """;
    // language=XML
    private static final String SPLIT_XML = """
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema">
                <crf:Sequence>
                    <Noun>New York</Noun>
                </crf:Sequence>
            </crf:Collection>
            """;
    // language=XML
    private static final String TRAILING_DIVERGENCE_XML = """
            <crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema">
                <crf:Sequence>
                    <Word>Alpha</Word>
                    <crf:Excluded> </crf:Excluded>
                    <Word>Beta Gamma</Word>
                    <crf:Excluded> </crf:Excluded>
                    <Word>Delta</Word>
                </crf:Sequence>
            </crf:Collection>
            """;

    private final AlignmentDetector<String> detector = defaultDetector();

    @TempDir
    Path temporaryDirectory;

    static Stream<AlignParameters> align() {
        return Stream
                .of(
                        new AlignParameters(
                                "aligned",
                                false,
                                0,
                                TrainingSequence
                                        .ofSegments(List.of(token("0", "Brown"), excluded(" "), token("0", "Fox"))),
                                AlignmentStatus.ALIGNED,
                                List.of("Brown", "Fox"),
                                List.of("Brown", "Fox"),
                                null
                        ),
                        new AlignParameters(
                                "misaligned",
                                false,
                                3,
                                sequenceOf("New York"),
                                AlignmentStatus.MISALIGNED,
                                List.of("New York"),
                                List.of("New", "York"),
                                null
                        ),
                        new AlignParameters(
                                "untokenizable",
                                true,
                                0,
                                sequenceOf("Salt", "Lake"),
                                AlignmentStatus.UNTOKENIZABLE,
                                List.of("Salt", "Lake"),
                                List.of(),
                                "blank"
                        )
                );
    }

    @SuppressWarnings({"DataFlowIssue", "NullAway"})
    static Stream<ExceptionCase> constructor__exception() {
        return Stream.of(
                new ExceptionCase(
                        "nullTokenizer",
                        () -> new AlignmentDetector<>(null, new XmlTrainingData<>(new StringTagProvider("0"))),
                        NullPointerException.class,
                        "tokenizer must not be null"
                ),
                new ExceptionCase(
                        "nullSequencer",
                        () -> new AlignmentDetector<>(new WhitespaceTokenizer(), null),
                        NullPointerException.class,
                        "sequencer must not be null"
                ),
                new ExceptionCase(
                        "nullStrategy",
                        () -> new AlignmentDetector<>(
                                new WhitespaceTokenizer(),
                                new XmlTrainingData<>(new StringTagProvider("0")),
                                null
                        ),
                        NullPointerException.class,
                        "strategy must not be null"
                ),
                new ExceptionCase(
                        "detectStreamingNullFile",
                        () -> defaultDetector().detectStreaming(null),
                        NullPointerException.class,
                        "trainingDataFile must not be null"
                ),
                new ExceptionCase(
                        "detectMaterializedNullFile",
                        () -> defaultDetector().detectMaterialized(null),
                        NullPointerException.class,
                        "trainingDataFile must not be null"
                )
        );
    }

    static Stream<DetectParameters> sequences() {
        return Stream.of(
                new DetectParameters(
                        "aligned",
                        ALIGNED_XML,
                        AlignmentStatus.ALIGNED,
                        List.of("Brown", "Fox"),
                        List.of("Brown", "Fox"),
                        List.of()
                ),
                new DetectParameters(
                        "merge",
                        MERGE_XML,
                        AlignmentStatus.MISALIGNED,
                        List.of("Salt", "Lake"),
                        List.of("SaltLake"),
                        List.of(tokenDifference(DifferenceKind.REPLACEMENT, 0, 2, 0, 1))
                ),
                new DetectParameters(
                        "split",
                        SPLIT_XML,
                        AlignmentStatus.MISALIGNED,
                        List.of("New York"),
                        List.of("New", "York"),
                        List.of(tokenDifference(DifferenceKind.REPLACEMENT, 0, 1, 0, 2))
                ),
                new DetectParameters(
                        "trailingDivergence",
                        TRAILING_DIVERGENCE_XML,
                        AlignmentStatus.MISALIGNED,
                        List.of("Alpha", "Beta Gamma", "Delta"),
                        List.of("Alpha", "Beta", "Gamma", "Delta"),
                        List.of(tokenDifference(DifferenceKind.REPLACEMENT, 1, 3, 1, 4))
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void align(AlignParameters parameters) {
        // ARRANGE //
        AlignmentDetector<String> activeDetector = parameters.rejecting() ? rejectingDetector() : detector;

        // ACT //
        SequenceAlignment<String> alignment = activeDetector.align(parameters.sequenceIndex(), parameters.sequence());

        // ASSERT //
        assertEquals(parameters.sequenceIndex(), alignment.sequenceIndex());
        assertEquals(parameters.expectedStatus(), alignment.status());
        assertEquals(parameters.expectedStatus() == AlignmentStatus.ALIGNED, alignment.isAligned());
        assertEquals(parameters.expectedStoredTokens(), alignment.storedTokens());
        assertEquals(parameters.expectedRetokenizedTokens(), alignment.retokenizedTokens());

        TokenComparison comparison = alignment.comparison();
        if (parameters.expectedStatus() == AlignmentStatus.UNTOKENIZABLE) {
            assertNull(comparison);
        } else {
            assertNotNull(comparison);
            assertEquals(parameters.expectedStatus() == AlignmentStatus.ALIGNED, comparison.aligned());
        }

        String failureReason = alignment.failureReason();
        String expectedFailureReasonContains = parameters.expectedFailureReasonContains();
        if (expectedFailureReasonContains == null) {
            assertNull(failureReason);
        } else {
            assertNotNull(failureReason);
            assertTrue(failureReason.contains(expectedFailureReasonContains));
        }
    }

    @MethodSource
    @ParameterizedTest
    void constructor__exception(ExceptionCase parameters) {
        assertThrowsWithMessage(parameters);
    }

    @Test
    void detectMaterialized__readsCapturedResultsWithoutPerCallIo() throws IOException {
        // ARRANGE //
        Path file = write("mixed.xml", MIXED_XML);

        // ACT //
        MaterializedAlignmentReport<String> report = detector.detectMaterialized(file);

        // ASSERT //
        // Accessors on a materialized report do not declare IOException and may be read repeatedly,
        // returning identical results without per-call IO.
        assertEquals(2, report.summary().total());
        assertEquals(1, report.summary().misaligned());
        assertEquals(List.of(1), report.misaligned().map(SequenceAlignment::sequenceIndex).toList());
        assertEquals(2, report.sequences().count());

        assertEquals(2, report.summary().total());
        assertEquals(2, report.sequences().count());
        assertEquals(List.of(1), report.misaligned().map(SequenceAlignment::sequenceIndex).toList());
    }

    @Test
    void detectStreaming__emptyFile__reportsNoSequences() throws IOException {
        // ARRANGE //
        Path file = write("empty.xml", EMPTY_XML);

        // ACT //
        AlignmentReport<String> report = detector.detectStreaming(file);

        // ASSERT //
        AlignmentSummary summary = report.summary();
        assertEquals(0, summary.total());
        assertTrue(summary.allAligned());
        assertEquals(0, summary.aligned());
        assertEquals(0, summary.misaligned());
        assertEquals(0, summary.untokenizable());
        try (Stream<SequenceAlignment<String>> misaligned = report.misaligned()) {
            assertTrue(misaligned.toList().isEmpty());
        }
    }

    @Test
    void detectStreaming__multipleSequences__mixedResults() throws IOException {
        // ARRANGE //
        Path file = write("mixed.xml", MIXED_XML);

        // ACT //
        AlignmentReport<String> report = detector.detectStreaming(file);

        // ASSERT //
        AlignmentSummary summary = report.summary();
        assertEquals(2, summary.total());
        assertFalse(summary.allAligned());
        assertEquals(1, summary.aligned());
        assertEquals(1, summary.misaligned());
        assertEquals(0, summary.untokenizable());

        List<SequenceAlignment<String>> misaligned;
        try (Stream<SequenceAlignment<String>> stream = report.misaligned()) {
            misaligned = stream.toList();
        }
        assertEquals(1, misaligned.size());
        assertEquals(1, misaligned.getFirst().sequenceIndex());

        List<SequenceAlignment<String>> sequences;
        try (Stream<SequenceAlignment<String>> stream = report.sequences()) {
            sequences = stream.toList();
        }
        assertEquals(List.of(0, 1), sequences.stream().map(SequenceAlignment::sequenceIndex).toList());
        assertEquals(AlignmentStatus.ALIGNED, sequences.getFirst().status());
    }

    @Test
    void detectStreaming__untokenizableSurface__reportsUntokenizable() throws IOException {
        // ARRANGE //
        Path file = write("untokenizable.xml", ALIGNED_XML);

        // ACT //
        AlignmentReport<String> report = rejectingDetector().detectStreaming(file);

        // ASSERT //
        assertEquals(1, report.summary().untokenizable());

        List<SequenceAlignment<String>> misaligned;
        try (Stream<SequenceAlignment<String>> stream = report.misaligned()) {
            misaligned = stream.toList();
        }
        assertEquals(1, misaligned.size());
        SequenceAlignment<String> sequence = misaligned.getFirst();
        assertEquals(AlignmentStatus.UNTOKENIZABLE, sequence.status());
        assertFalse(sequence.isAligned());
        assertTrue(sequence.retokenizedTokens().isEmpty());
        assertNull(sequence.comparison());
        String failureReason = sequence.failureReason();
        assertNotNull(failureReason);
        assertTrue(failureReason.contains("blank"));
    }

    @Test
    void materialize__snapshotIsDecoupledFromLaterFileEdits() throws IOException {
        // ARRANGE //
        Path file = write("mixed.xml", MIXED_XML);
        MaterializedAlignmentReport<String> snapshot = detector.detectStreaming(file).materialize();

        // ACT //
        Files.writeString(file, EMPTY_XML);

        // ASSERT //
        // The snapshot still reflects the two original sequences...
        assertEquals(2, snapshot.summary().total());
        // ...while a streaming report over the same file now sees none.
        assertEquals(0, detector.detectStreaming(file).summary().total());
    }

    @MethodSource
    @ParameterizedTest
    void sequences(DetectParameters parameters) throws IOException {
        // ARRANGE //
        Path file = write(parameters.name() + ".xml", parameters.xml());

        // ACT //
        List<SequenceAlignment<String>> sequences;
        try (Stream<SequenceAlignment<String>> stream = detector.detectStreaming(file).sequences()) {
            sequences = stream.toList();
        }

        // ASSERT //
        assertEquals(1, sequences.size());
        SequenceAlignment<String> sequence = sequences.getFirst();
        assertEquals(parameters.expectedStatus(), sequence.status());
        assertEquals(parameters.expectedStoredTokens(), sequence.storedTokens());
        assertEquals(parameters.expectedRetokenizedTokens(), sequence.retokenizedTokens());

        TokenComparison comparison = sequence.comparison();
        assertNotNull(comparison);
        assertEquals(parameters.expectedDifferences(), comparison.differences());
        assertEquals(parameters.expectedDifferences().isEmpty(), comparison.aligned());
    }

    @Test
    void sequences__missingFile__throwsIOException() {
        // ARRANGE //
        Path missing = temporaryDirectory.resolve("does-not-exist.xml");
        AlignmentReport<String> report = detector.detectStreaming(missing);

        // ACT & ASSERT //
        assertThrows(IOException.class, report::sequences);
    }

    private static AlignmentDetector<String> rejectingDetector() {
        Tokenizer rejecting = input -> {
            throw new InvalidInputException(input, "blank");
        };
        return new AlignmentDetector<>(rejecting, new XmlTrainingData<>(new StringTagProvider("0")));
    }

    private Path write(String name, String xml) throws IOException {
        Path file = temporaryDirectory.resolve(name);
        Files.writeString(file, xml);
        return file;
    }
}
