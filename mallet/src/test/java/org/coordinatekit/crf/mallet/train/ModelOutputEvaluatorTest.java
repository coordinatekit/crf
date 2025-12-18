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
import cc.mallet.fst.CRFTrainerByLabelLikelihood;
import cc.mallet.fst.CRFTrainerByThreadedLabelLikelihood;
import cc.mallet.fst.Transducer;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelSequence;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NullMarked
class ModelOutputEvaluatorTest {
    private static final Path CURRENT_DIRECTORY = Paths.get("");
    private static final String FILE_PREFIX = "model_iter";
    private static final String FILE_SUFFIX = ".ser";

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
            ModelOutputConfiguration.Builder modelOutputConfigurationBuilder,
            @Nullable Path outputDirectory,
            boolean relativeOutputDirectory,
            int iterations,
            Set<Path> expectedFiles
    ) {}

    static Stream<EvaluateParameters> evaluate() {
        return Stream.of(
                new EvaluateParameters(
                        "doesNotWriteModelAtNonMatchingIteration",
                        ModelOutputConfiguration.builder().iterationInterval(10),
                        null,
                        false,
                        3,
                        Set.of()
                ),
                new EvaluateParameters(
                        "usesCustomFilePrefixAndSuffix",
                        ModelOutputConfiguration.builder().filePrefix("crf_model_iter").fileSuffix(".bin")
                                .iterationInterval(1),
                        null,
                        false,
                        1,
                        Set.of(Path.of("crf_model_iter1.bin"))
                ),
                new EvaluateParameters(
                        "writesModelAtMatchingIteration",
                        ModelOutputConfiguration.builder().filePrefix(FILE_PREFIX).fileSuffix(FILE_SUFFIX)
                                .iterationInterval(5),
                        CURRENT_DIRECTORY,
                        true,
                        5,
                        Set.of(Path.of("model_iter5.ser"))
                ),
                new EvaluateParameters(
                        "writesModelAtNestedOutputDirectory",
                        ModelOutputConfiguration.builder().iterationInterval(1),
                        Paths.get("nested", "output", "dir"),
                        false,
                        1,
                        Set.of(Paths.get("nested", "output", "dir", "model_1.ser"))
                ),
                new EvaluateParameters(
                        "writesModelWhenOutputDirectoryIsEmptyPath",
                        ModelOutputConfiguration.builder().iterationInterval(1),
                        null,
                        true,
                        1,
                        Set.of(Path.of("model_1.ser"))
                ),
                new EvaluateParameters(
                        "writesMultipleModelsAtDifferentIterations",
                        ModelOutputConfiguration.builder().iterationInterval(1),
                        null,
                        false,
                        2,
                        Set.of(Path.of("model_1.ser"), Path.of("model_2.ser"))
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void evaluate(EvaluateParameters parameters) throws ClassNotFoundException, IOException {
        // ARRANGE //
        InstanceList trainingData = createTrainingData();

        ModelOutputConfiguration.Builder configurationBuilder = parameters.modelOutputConfigurationBuilder();

        if (!parameters.relativeOutputDirectory() && parameters.outputDirectory() != null) {
            configurationBuilder.outputDirectory(temporaryDirectory.resolve(parameters.outputDirectory()));
        } else if (!parameters.relativeOutputDirectory()) {
            configurationBuilder.outputDirectory(temporaryDirectory);
        } else if (parameters.outputDirectory() != null) {
            configurationBuilder.outputDirectory(parameters.outputDirectory());
        }

        ModelOutputEvaluator evaluator = new ModelOutputEvaluator(configurationBuilder.build());

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
            CRF readCrf = deserialize(CRF.class, temporaryDirectory.resolve(expectedFile));
            assertAll(
                    String.format("expectedFile: %s", expectedFile),
                    () -> assertEquals(trainingData.getAlphabet(), readCrf.getInputAlphabet()),
                    () -> assertEquals(trainingData.getTargetAlphabet(), readCrf.getOutputAlphabet())
            );
        }
    }

    @Test
    void evaluate_ioException() throws IOException {
        // ARRANGE //
        InstanceList trainingData = createTrainingData();

        ModelOutputEvaluator evaluator = new ModelOutputEvaluator(
                ModelOutputConfiguration.builder().iterationInterval(1).build()
        );
        evaluator.setBasePath(temporaryDirectory);

        CRF crf = createAndInitializeCrf(trainingData);
        CRFTrainerByThreadedLabelLikelihood trainer = null;

        Files.createDirectories(temporaryDirectory.resolve("model_1.ser"));

        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory
                .getLogger(ModelOutputEvaluator.class);

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
            assertTrue(logs.getFirst().getFormattedMessage().startsWith("Iteration 1: Failed to write model to "));
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

    @Test
    void evaluateInstanceList() {
        // ARRANGE //
        InstanceList trainingData = createTrainingData();

        CRF crf = createAndInitializeCrf(trainingData);
        CRFTrainerByLabelLikelihood trainer = new CRFTrainerByLabelLikelihood(crf);

        // ACT //
        ModelOutputEvaluator evaluator = new ModelOutputEvaluator(ModelOutputConfiguration.defaults());

        // ASSERT //
        Throwable t = assertThrows(
                UnsupportedOperationException.class,
                () -> evaluator.evaluateInstanceList(trainer, trainingData, "Test")
        );
        assertEquals("This evaluator does not support evaluating instance lists.", t.getMessage());
    }

    private CRF createAndInitializeCrf(InstanceList trainingData) {
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

    private Instance createInstance(String[][] features, String[] labels) {
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
                null
        );
    }

    private InstanceList createTrainingData() {
        InstanceList instances = new InstanceList(dataAlphabet, targetAlphabet);

        instances
                .add(
                        createInstance(
                                new String[][] {{"word=new", "cap=yes"}, {"word=york", "cap=yes"},
                                                {"word=city", "cap=yes"}},
                                new String[] {"B-LOC", "I-LOC", "I-LOC"}
                        )
                );

        instances.add(
                createInstance(
                        new String[][] {{"word=the", "cap=no"}, {"word=cat", "cap=no"}, {"word=sat", "cap=no"}},
                        new String[] {"O", "O", "O"}
                )
        );

        instances
                .add(
                        createInstance(
                                new String[][] {{"word=in", "cap=no"}, {"word=london", "cap=yes"},
                                                {"word=today", "cap=no"}},
                                new String[] {"O", "B-LOC", "O"}
                        )
                );

        return instances;
    }

    @SuppressWarnings({"SameParameterValue", "unchecked"})
    private static <T> T deserialize(Class<T> clazz, Path file) throws ClassNotFoundException, IOException {
        try (ObjectInputStream s = new ObjectInputStream(Files.newInputStream(file))) {
            Object readObject = s.readObject();
            assertInstanceOf(clazz, readObject, "Deserialized object should be of expected type");
            return (T) readObject;
        }
    }

    private static SortedSet<Path> listDirectory(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile).map(directory::relativize)
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }
}
