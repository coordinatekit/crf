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
package org.coordinatekit.crf.mallet.train;

import cc.mallet.fst.CRF;
import cc.mallet.fst.CRFTrainerByThreadedLabelLikelihood;
import cc.mallet.fst.Transducer;
import cc.mallet.types.*;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@NullMarked
class ConllOutputEvaluatorTest {
    private enum TestSequenceFixture {
        NONE, MULTIPLE, SINGLE_WITH_SPACES_IN_TOKENS
    }

    private static final String TEST_DESCRIPTION = "test";
    private static final String FILE_PREFIX = "output";
    private static final String FILE_SUFFIX = ".txt";

    private Alphabet dataAlphabet;
    private LabelAlphabet targetAlphabet;

    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void setUp() {
        dataAlphabet = new Alphabet();
        targetAlphabet = new LabelAlphabet();
        targetAlphabet.lookupIndex("O", true);
        targetAlphabet.lookupIndex("B-LOC", true);
        targetAlphabet.lookupIndex("I-LOC", true);
    }

    record EvaluateParameters(
            String name,
            ConllOutputConfiguration.Builder conllOutputConfigurationBuilder,
            @Nullable Path outputDirectory,
            boolean relativeOutputDirectory,
            TestSequenceFixture testSequenceFixture,
            int iterations,
            Set<Path> expectedFiles
    ) {}

    static Stream<EvaluateParameters> evaluate() {
        return Stream.of(
                new EvaluateParameters(
                        "doesNotWriteOutputAtNonMatchingIteration",
                        ConllOutputConfiguration.builder().filePrefix(FILE_PREFIX).fileSuffix(FILE_SUFFIX)
                                .iterationInterval(10),
                        null,
                        false,
                        TestSequenceFixture.MULTIPLE,
                        3,
                        Set.of()
                ),
                new EvaluateParameters(
                        "doesNotWriteOutputForEmptyInstances",
                        ConllOutputConfiguration.builder().iterationInterval(1),
                        null,
                        false,
                        TestSequenceFixture.NONE,
                        1,
                        Set.of()
                ),
                new EvaluateParameters(
                        "writesOutputFileAtSpecifiedIteration",
                        ConllOutputConfiguration.builder().iterationInterval(5),
                        null,
                        false,
                        TestSequenceFixture.MULTIPLE,
                        5,
                        Set.of(Paths.get("output_iter5.conll"))
                ),
                new EvaluateParameters(
                        "usesCustomFilePrefixAndSuffix",
                        ConllOutputConfiguration.builder().filePrefix("predictions_iter").fileSuffix(".tsv")
                                .iterationInterval(1),
                        null,
                        false,
                        TestSequenceFixture.MULTIPLE,
                        1,
                        Set.of(Paths.get("predictions_iter1.tsv"))
                ),

                new EvaluateParameters(
                        "writesOutputFileAtNestedOutputDirectory",
                        ConllOutputConfiguration.builder().iterationInterval(1),
                        Paths.get("nested", "output", "dir"),
                        false,
                        TestSequenceFixture.MULTIPLE,
                        1,
                        Set.of(Paths.get("nested", "output", "dir", "output_iter1.conll"))
                ),
                new EvaluateParameters(
                        "writesOutputFileWhenOutputDirectoryIsEmptyPath",
                        ConllOutputConfiguration.builder().iterationInterval(1),
                        null,
                        true,
                        TestSequenceFixture.MULTIPLE,
                        1,
                        Set.of(Path.of("output_iter1.conll"))
                ),
                new EvaluateParameters(
                        "writesMultipleOutputFilesAtDifferentIterations",
                        ConllOutputConfiguration.builder().iterationInterval(1),
                        null,
                        false,
                        TestSequenceFixture.MULTIPLE,
                        2,
                        Set.of(Path.of("output_iter1.conll"), Path.of("output_iter2.conll"))
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void evaluate(EvaluateParameters parameters) throws IOException {
        // ARRANGE //
        InstanceList trainingData = createTrainingData();
        InstanceList testData = createTestData(parameters.testSequenceFixture());

        ConllOutputConfiguration.Builder configurationBuilder = parameters.conllOutputConfigurationBuilder();

        if (!parameters.relativeOutputDirectory() && parameters.outputDirectory() != null) {
            configurationBuilder.outputDirectory(temporaryDirectory.resolve(parameters.outputDirectory()));
        } else if (!parameters.relativeOutputDirectory()) {
            configurationBuilder.outputDirectory(temporaryDirectory);
        } else if (parameters.outputDirectory() != null) {
            configurationBuilder.outputDirectory(parameters.outputDirectory());
        }

        ConllOutputEvaluator evaluator = new ConllOutputEvaluator(
                testData,
                TEST_DESCRIPTION,
                configurationBuilder.build()
        );

        if (parameters.relativeOutputDirectory()) {
            evaluator.setBasePath(temporaryDirectory);
        }

        CRF crf = createAndInitializeCrf(trainingData);
        CRFTrainerByThreadedLabelLikelihood trainer = null;

        // ACT //
        try {
            trainer = new CRFTrainerByThreadedLabelLikelihood(crf, 1);
            trainer.addEvaluator(evaluator);
            trainer.train(trainingData, parameters.iterations());
        } finally {
            if (trainer != null) {
                trainer.shutdown();
            }
        }

        // ASSERT //
        assertIterableEquals(new TreeSet<>(parameters.expectedFiles()), listDirectory(temporaryDirectory));
        for (Path expectedFile : parameters.expectedFiles()) {
            validateOutputFile(temporaryDirectory.resolve(expectedFile));
        }
    }

    @Test
    void evaluate_ioException() throws IOException {
        // ARRANGE //
        InstanceList trainingData = createTrainingData();
        InstanceList testData = createTestData(TestSequenceFixture.MULTIPLE);

        ConllOutputEvaluator evaluator = new ConllOutputEvaluator(
                testData,
                TEST_DESCRIPTION,
                ConllOutputConfiguration.builder().iterationInterval(1).build()
        );
        evaluator.setBasePath(temporaryDirectory);

        CRF crf = createAndInitializeCrf(trainingData);
        CRFTrainerByThreadedLabelLikelihood trainer = null;

        Files.createDirectories(temporaryDirectory.resolve("output_iter1.conll"));

        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory
                .getLogger(ConllOutputEvaluator.class);

        // Create and attach a ListAppender
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        // ACT //
        try {
            try {
                trainer = new CRFTrainerByThreadedLabelLikelihood(crf, 1);
                trainer.addEvaluator(evaluator);
                trainer.train(trainingData, 1);
            } finally {
                if (trainer != null) {
                    trainer.shutdown();
                }
            }

            // ASSERT //
            List<ILoggingEvent> logs = listAppender.list;
            assertEquals(1, logs.size());
            assertEquals(Level.ERROR, logs.getFirst().getLevel());
            assertTrue(
                    logs.getFirst().getFormattedMessage().startsWith("Iteration 1: Failed to write CoNLL output to ")
            );
            if (logs.getFirst().getThrowableProxy() instanceof ThrowableProxy) {
                assertInstanceOf(
                        IOException.class,
                        ((ThrowableProxy) logs.getFirst().getThrowableProxy()).getThrowable()
                );
            }
        } finally {
            logger.detachAppender(listAppender);
        }
    }

    private static CRF createAndInitializeCrf(InstanceList trainingData) {
        CRF crf = new CRF(trainingData.getPipe(), null);

        String startName = crf.addOrderNStates(
                trainingData,
                new int[] {1},
                null,
                "O",
                Pattern.compile("\\s"),
                Pattern.compile(".*"),
                true
        );

        for (int i = 0; i < crf.numStates(); i++) {
            crf.getState(i).setInitialWeight(Transducer.IMPOSSIBLE_WEIGHT);
        }
        crf.getState(startName).setInitialWeight(0.0);

        return crf;
    }

    private Instance createInstance(String[][] features, String[] labels, String[] tokens) {
        FeatureVector[] featureVectors = new FeatureVector[features.length];
        for (int i = 0; i < features.length; i++) {
            int[] indices = new int[features[i].length];
            for (int j = 0; j < features[i].length; j++) {
                indices[j] = dataAlphabet.lookupIndex(features[i][j], true);
            }
            featureVectors[i] = new FeatureVector(dataAlphabet, indices);
        }

        int[] labelIndices = new int[labels.length];
        for (int i = 0; i < labels.length; i++) {
            labelIndices[i] = targetAlphabet.lookupIndex(labels[i], true);
        }

        return new Instance(
                new FeatureVectorSequence(featureVectors),
                new LabelSequence(targetAlphabet, labelIndices),
                null,
                new TrainingSequence<>(Arrays.asList(tokens), Arrays.asList(labels))
        );
    }

    private InstanceList createMultiSequenceTestData() {
        InstanceList instances = new InstanceList(dataAlphabet, targetAlphabet);

        instances.add(
                createInstance(
                        new String[][] {{"word=visit", "cap=no"}, {"word=paris", "cap=yes"}},
                        new String[] {"O", "B-LOC"},
                        new String[] {"visit", "Paris"}
                )
        );

        instances.add(
                createInstance(
                        new String[][] {{"word=in", "cap=no"}, {"word=rome", "cap=yes"}},
                        new String[] {"O", "B-LOC"},
                        new String[] {"in", "Rome"}
                )
        );

        return instances;
    }

    private InstanceList createTestData(TestSequenceFixture testSequences) {
        return switch (testSequences) {
            case NONE -> new InstanceList(dataAlphabet, targetAlphabet);
            case MULTIPLE -> createMultiSequenceTestData();
            case SINGLE_WITH_SPACES_IN_TOKENS -> createTestDataWithSpacesInTokens();
        };
    }

    private InstanceList createTestDataWithSpacesInTokens() {
        InstanceList instances = new InstanceList(dataAlphabet, targetAlphabet);

        // Feature vectors don't contain spaces, so this tests that the output format is correct
        instances.add(
                createInstance(
                        new String[][] {{"word=new_york", "cap=yes"}},
                        new String[] {"B-LOC"},
                        new String[] {"New York"}
                )
        );

        return instances;
    }

    private InstanceList createTrainingData() {
        InstanceList instances = new InstanceList(dataAlphabet, targetAlphabet);

        instances
                .add(
                        createInstance(
                                new String[][] {{"word=new", "cap=yes"}, {"word=york", "cap=yes"},
                                                {"word=city", "cap=yes"}},
                                new String[] {"B-LOC", "I-LOC", "I-LOC"},
                                new String[] {"New", "York", "City"}
                        )
                );

        instances.add(
                createInstance(
                        new String[][] {{"word=the", "cap=no"}, {"word=cat", "cap=no"}, {"word=sat", "cap=no"}},
                        new String[] {"O", "O", "O"},
                        new String[] {"the", "cat", "sat"}
                )
        );

        instances
                .add(
                        createInstance(
                                new String[][] {{"word=in", "cap=no"}, {"word=london", "cap=yes"},
                                                {"word=today", "cap=no"}},
                                new String[] {"O", "B-LOC", "O"},
                                new String[] {"in", "London", "today"}
                        )
                );

        return instances;
    }

    private static SortedSet<Path> listDirectory(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile).map(directory::relativize)
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    private static void validateOutputFile(Path outputFile) throws IOException {
        String content = Files.readString(outputFile);
        List<String> lines = content.lines().toList();

        // Verify file contains header
        assertTrue(
                content.startsWith("token actual predicted confidence"),
                String.format("File %s should start with header", outputFile)
        );

        // Verify file contains label information
        assertTrue(
                content.contains("O") || content.contains("B-LOC") || content.contains("I-LOC"),
                String.format("File %s should contain label information. Content: %s", outputFile, content)
        );

        // Count blank lines (excluding header blank line)
        // With 2 sequences, there should be 1 blank line between them
        long blankLineCount = content.lines().filter(String::isEmpty).count();
        assertTrue(
                blankLineCount >= 2,
                String.format(
                        "File %s should have blank lines: after header and between sequences: %s",
                        outputFile,
                        content
                )
        );

        // Verify file contains a confidence score (decimal number)
        if (lines.size() >= 3) {
            Pattern linePattern = Pattern.compile("^(?:|\\S+\\s\\S+\\s\\S+\\s\\d+\\.\\d+)$");
            assertAll(
                    IntStream.range(1, lines.size()).mapToObj(
                            i -> () -> assertTrue(
                                    linePattern.matcher(lines.get(i)).matches(),
                                    String.format("Line %d should match pattern: '%s'", i + 1, lines.get(i))
                            )
                    )
            );
        }
        assertTrue(
                content.matches("(?s).*\\d+\\.\\d+.*"),
                String.format("File %s should contain a confidence score", outputFile)
        );
    }
}
