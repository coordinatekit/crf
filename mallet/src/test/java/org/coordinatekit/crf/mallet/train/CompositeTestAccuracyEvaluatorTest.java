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
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelSequence;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositeTestAccuracyEvaluatorTest {
    private static final String TEST_DESCRIPTION = "test";

    private Alphabet dataAlphabet;
    private LabelAlphabet targetAlphabet;
    private ListAppender<ILoggingEvent> logAppender;
    private ch.qos.logback.classic.Logger evaluatorLogger;
    private Level previousLevel;

    @BeforeEach
    void setUp() {
        dataAlphabet = new Alphabet();
        targetAlphabet = new LabelAlphabet();
        targetAlphabet.lookupIndex("O", true);
        targetAlphabet.lookupIndex("B-LOC", true);
        targetAlphabet.lookupIndex("I-LOC", true);

        evaluatorLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(CompositeTestAccuracyEvaluator.class);
        previousLevel = evaluatorLogger.getLevel();
        evaluatorLogger.setLevel(Level.INFO);
        logAppender = new ListAppender<>();
        logAppender.start();
        evaluatorLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        evaluatorLogger.detachAppender(logAppender);
        evaluatorLogger.setLevel(previousLevel);
    }

    @Test
    void constructorCreatesEvaluatorWithTestData() {
        InstanceList testData = new InstanceList(dataAlphabet, targetAlphabet);

        CompositeTestAccuracyEvaluator evaluator = new CompositeTestAccuracyEvaluator(testData, TEST_DESCRIPTION);

        assertNotNull(evaluator);
    }

    @Test
    void evaluate__logsLikelihoodOnlyForEmptyInstances() {
        // ARRANGE //
        InstanceList trainingData = createTrainingData();
        InstanceList emptyTestData = new InstanceList(dataAlphabet, targetAlphabet);

        CRF crf = createAndInitializeCrf(trainingData);
        CRFTrainerByThreadedLabelLikelihood trainer = new CRFTrainerByThreadedLabelLikelihood(crf, 1);

        try {
            trainer.train(trainingData, 1);

            CompositeTestAccuracyEvaluator evaluator = new CompositeTestAccuracyEvaluator(
                    emptyTestData,
                    TEST_DESCRIPTION
            );

            // ACT //
            evaluator.evaluate(trainer);

            // ASSERT //
            ILoggingEvent event = singleLoggedEvent();
            assertEquals(Level.INFO, event.getLevel());
            String message = event.getFormattedMessage();
            assertTrue(message.contains("log likelihood = "), message);
            assertFalse(message.contains("instance accuracy"), message);
            assertFalse(message.contains("token accuracy"), message);
        } finally {
            trainer.shutdown();
        }
    }

    @Test
    void evaluate__logsAccuracyForNonEmptyInstances() {
        // ARRANGE //
        InstanceList trainingData = createTrainingData();
        InstanceList testData = createTestData();

        CRF crf = createAndInitializeCrf(trainingData);
        CRFTrainerByThreadedLabelLikelihood trainer = new CRFTrainerByThreadedLabelLikelihood(crf, 1);

        try {
            trainer.train(trainingData, 5);

            CompositeTestAccuracyEvaluator evaluator = new CompositeTestAccuracyEvaluator(testData, TEST_DESCRIPTION);

            // ACT //
            evaluator.evaluate(trainer);

            // ASSERT //
            ILoggingEvent event = singleLoggedEvent();
            assertEquals(Level.INFO, event.getLevel());
            String message = event.getFormattedMessage();
            assertTrue(message.contains("log likelihood = "), message);
            assertTrue(message.contains("instance accuracy = "), message);
            assertTrue(message.contains("token accuracy = "), message);
        } finally {
            trainer.shutdown();
        }
    }

    @Test
    void evaluate__accuracyIsBetweenZeroAndOne() {
        // ARRANGE //
        InstanceList trainingData = createTrainingData();
        InstanceList testData = createTestData();

        CRF crf = createAndInitializeCrf(trainingData);
        CRFTrainerByThreadedLabelLikelihood trainer = new CRFTrainerByThreadedLabelLikelihood(crf, 1);

        try {
            trainer.train(trainingData, 10);

            CompositeTestAccuracyEvaluator evaluator = new CompositeTestAccuracyEvaluator(testData, TEST_DESCRIPTION);

            // ACT //
            evaluator.evaluate(trainer);

            // ASSERT //
            ILoggingEvent event = singleLoggedEvent();
            double instanceAccuracy = loggedMetric(event, "instance accuracy");
            double tokenAccuracy = loggedMetric(event, "token accuracy");
            assertTrue(
                    instanceAccuracy >= 0.0 && instanceAccuracy <= 1.0,
                    "Instance accuracy should be between 0 and 1"
            );
            assertTrue(tokenAccuracy >= 0.0 && tokenAccuracy <= 1.0, "Token accuracy should be between 0 and 1");
        } finally {
            trainer.shutdown();
        }
    }

    @Test
    void evaluate__reportsHighTokenAccuracyForTrainedData() {
        // ARRANGE //
        InstanceList trainingData = createSimpleTrainingData();

        CRF crf = createAndInitializeCrf(trainingData);
        CRFTrainerByThreadedLabelLikelihood trainer = new CRFTrainerByThreadedLabelLikelihood(crf, 1);

        try {
            // Train extensively on simple data
            trainer.train(trainingData, 50);

            CompositeTestAccuracyEvaluator evaluator = new CompositeTestAccuracyEvaluator(
                    trainingData,
                    TEST_DESCRIPTION
            );

            // ACT //
            evaluator.evaluate(trainer);

            // ASSERT //
            double tokenAccuracy = loggedMetric(singleLoggedEvent(), "token accuracy");
            assertTrue(tokenAccuracy > 0.5, "Token accuracy should be reasonable after training");
        } finally {
            trainer.shutdown();
        }
    }

    @Test
    void evaluateReportsIterationNumber() {
        // ARRANGE //
        InstanceList trainingData = createTrainingData();
        InstanceList testData = createTestData();

        CRF crf = createAndInitializeCrf(trainingData);
        CRFTrainerByThreadedLabelLikelihood trainer = new CRFTrainerByThreadedLabelLikelihood(crf, 1);

        try {
            trainer.train(trainingData, 3);

            CompositeTestAccuracyEvaluator evaluator = new CompositeTestAccuracyEvaluator(testData, TEST_DESCRIPTION);

            // ACT //
            evaluator.evaluate(trainer);

            // ASSERT //
            assertTrue(
                    singleLoggedEvent().getFormattedMessage().startsWith("Iteration 3:"),
                    "Should report correct iteration number"
            );
        } finally {
            trainer.shutdown();
        }
    }

    @Test
    void evaluateReportsLogLikelihood() {
        // ARRANGE //
        InstanceList trainingData = createTrainingData();
        InstanceList testData = createTestData();

        CRF crf = createAndInitializeCrf(trainingData);
        CRFTrainerByThreadedLabelLikelihood trainer = new CRFTrainerByThreadedLabelLikelihood(crf, 1);

        try {
            trainer.train(trainingData, 3);

            CompositeTestAccuracyEvaluator evaluator = new CompositeTestAccuracyEvaluator(testData, TEST_DESCRIPTION);

            // ACT //
            evaluator.evaluate(trainer);

            // ASSERT //
            assertFalse(
                    Double.isNaN(loggedMetric(singleLoggedEvent(), "log likelihood")),
                    "Log likelihood should be a valid number"
            );
        } finally {
            trainer.shutdown();
        }
    }

    private ILoggingEvent singleLoggedEvent() {
        List<ILoggingEvent> events = logAppender.list;
        assertEquals(1, events.size(), "expected exactly one log event");
        return events.getFirst();
    }

    private static double loggedMetric(ILoggingEvent event, String label) {
        Matcher matcher = Pattern.compile(label + " = ([^,]+)").matcher(event.getFormattedMessage());
        assertTrue(matcher.find(), () -> "expected '" + label + "' in log: " + event.getFormattedMessage());
        return Double.parseDouble(matcher.group(1).trim());
    }

    private InstanceList createTrainingData() {
        InstanceList instances = new InstanceList(dataAlphabet, targetAlphabet);

        // Sequence 1: "New York City" -> B-LOC I-LOC I-LOC
        instances
                .add(
                        createInstance(
                                new String[][] {{"word=new", "cap=yes"}, {"word=york", "cap=yes"},
                                                {"word=city", "cap=yes"}},
                                new String[] {"B-LOC", "I-LOC", "I-LOC"}
                        )
                );

        // Sequence 2: "the cat sat" -> O O O
        instances.add(
                createInstance(
                        new String[][] {{"word=the", "cap=no"}, {"word=cat", "cap=no"}, {"word=sat", "cap=no"}},
                        new String[] {"O", "O", "O"}
                )
        );

        // Sequence 3: "in London today" -> O B-LOC O
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

    private InstanceList createTestData() {
        InstanceList instances = new InstanceList(dataAlphabet, targetAlphabet);

        // Test sequence: "visit Paris soon" -> O B-LOC O
        instances
                .add(
                        createInstance(
                                new String[][] {{"word=visit", "cap=no"}, {"word=paris", "cap=yes"},
                                                {"word=soon", "cap=no"}},
                                new String[] {"O", "B-LOC", "O"}
                        )
                );

        return instances;
    }

    private InstanceList createSimpleTrainingData() {
        InstanceList instances = new InstanceList(dataAlphabet, targetAlphabet);

        // Very simple pattern: capitalized words are locations
        for (int i = 0; i < 10; i++) {
            instances.add(createInstance(new String[][] {{"cap=yes"}, {"cap=no"}}, new String[] {"B-LOC", "O"}));
            instances.add(createInstance(new String[][] {{"cap=no"}, {"cap=yes"}}, new String[] {"O", "B-LOC"}));
        }

        return instances;
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
}
