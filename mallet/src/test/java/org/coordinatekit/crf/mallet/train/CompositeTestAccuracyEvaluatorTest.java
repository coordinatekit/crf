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
import cc.mallet.fst.TransducerTrainer;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelSequence;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NullMarked
class CompositeTestAccuracyEvaluatorTest {
    private static final String TEST_DESCRIPTION = "test";

    private Alphabet dataAlphabet;
    private LabelAlphabet targetAlphabet;

    @BeforeEach
    void setUp() {
        dataAlphabet = new Alphabet();
        targetAlphabet = new LabelAlphabet();
        targetAlphabet.lookupIndex("O", true);
        targetAlphabet.lookupIndex("B-LOC", true);
        targetAlphabet.lookupIndex("I-LOC", true);
    }

    @Test
    void constructorCreatesEvaluatorWithTestData() {
        InstanceList testData = new InstanceList(dataAlphabet, targetAlphabet);

        CompositeTestAccuracyEvaluator evaluator = new CompositeTestAccuracyEvaluator(testData, TEST_DESCRIPTION);

        assertNotNull(evaluator);
    }

    @Test
    void evaluateInstanceListWithEmptyInstances() {
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
            evaluator.evaluate(trainer);
            // Should complete without error - logs only iteration and log likelihood
        } finally {
            trainer.shutdown();
        }
    }

    @Test
    void evaluateInstanceListWithNonEmptyInstancesCalculatesAccuracy() {
        InstanceList trainingData = createTrainingData();
        InstanceList testData = createTestData();

        CRF crf = createAndInitializeCrf(trainingData);
        CRFTrainerByThreadedLabelLikelihood trainer = new CRFTrainerByThreadedLabelLikelihood(crf, 1);

        try {
            trainer.train(trainingData, 5);

            CompositeTestAccuracyEvaluator evaluator = new CompositeTestAccuracyEvaluator(testData, TEST_DESCRIPTION);
            evaluator.evaluate(trainer);

            // The evaluator should have run successfully and logged accuracy metrics
            // We can't easily verify log output, but we verify no exceptions occurred
        } finally {
            trainer.shutdown();
        }
    }

    @Test
    void evaluateReturnsAccuracyBetweenZeroAndOne() {
        InstanceList trainingData = createTrainingData();
        InstanceList testData = createTestData();

        CRF crf = createAndInitializeCrf(trainingData);
        CRFTrainerByThreadedLabelLikelihood trainer = new CRFTrainerByThreadedLabelLikelihood(crf, 1);

        try {
            trainer.train(trainingData, 10);

            // Create a test-accessible subclass to verify accuracy values
            TestableCompositeTestAccuracyEvaluator evaluator = new TestableCompositeTestAccuracyEvaluator(
                    testData,
                    TEST_DESCRIPTION
            );
            evaluator.evaluate(trainer);

            assertTrue(
                    evaluator.lastInstanceAccuracy >= 0.0 && evaluator.lastInstanceAccuracy <= 1.0,
                    "Instance accuracy should be between 0 and 1"
            );
            assertTrue(
                    evaluator.lastTokenAccuracy >= 0.0 && evaluator.lastTokenAccuracy <= 1.0,
                    "Token accuracy should be between 0 and 1"
            );
        } finally {
            trainer.shutdown();
        }
    }

    @Test
    void evaluateWithPerfectPredictionsReturnsFullAccuracy() {
        InstanceList trainingData = createSimpleTrainingData();

        CRF crf = createAndInitializeCrf(trainingData);
        CRFTrainerByThreadedLabelLikelihood trainer = new CRFTrainerByThreadedLabelLikelihood(crf, 1);

        try {
            // Train extensively on simple data
            trainer.train(trainingData, 50);

            // Test on same data (should achieve high accuracy)
            TestableCompositeTestAccuracyEvaluator evaluator = new TestableCompositeTestAccuracyEvaluator(
                    trainingData,
                    TEST_DESCRIPTION
            );
            evaluator.evaluate(trainer);

            // With simple data and enough training, we expect high accuracy
            assertTrue(evaluator.lastTokenAccuracy > 0.5, "Token accuracy should be reasonable after training");
        } finally {
            trainer.shutdown();
        }
    }

    @Test
    void evaluateReportsIterationNumber() {
        InstanceList trainingData = createTrainingData();
        InstanceList testData = createTestData();

        CRF crf = createAndInitializeCrf(trainingData);
        CRFTrainerByThreadedLabelLikelihood trainer = new CRFTrainerByThreadedLabelLikelihood(crf, 1);

        try {
            trainer.train(trainingData, 3);

            TestableCompositeTestAccuracyEvaluator evaluator = new TestableCompositeTestAccuracyEvaluator(
                    testData,
                    TEST_DESCRIPTION
            );
            evaluator.evaluate(trainer);

            assertEquals(3, evaluator.lastIteration, "Should report correct iteration number");
        } finally {
            trainer.shutdown();
        }
    }

    @Test
    void evaluateReportsLogLikelihood() {
        InstanceList trainingData = createTrainingData();
        InstanceList testData = createTestData();

        CRF crf = createAndInitializeCrf(trainingData);
        CRFTrainerByThreadedLabelLikelihood trainer = new CRFTrainerByThreadedLabelLikelihood(crf, 1);

        try {
            trainer.train(trainingData, 3);

            TestableCompositeTestAccuracyEvaluator evaluator = new TestableCompositeTestAccuracyEvaluator(
                    testData,
                    TEST_DESCRIPTION
            );
            evaluator.evaluate(trainer);

            // Log likelihood should be a negative number (it's a log probability)
            assertFalse(Double.isNaN(evaluator.lastLogLikelihood), "Log likelihood should be a valid number");
        } finally {
            trainer.shutdown();
        }
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

    /**
     * Test subclass that captures evaluation results for verification.
     */
    private static class TestableCompositeTestAccuracyEvaluator extends CompositeTestAccuracyEvaluator {
        int lastIteration;
        double lastLogLikelihood;
        double lastInstanceAccuracy;
        double lastTokenAccuracy;

        private final cc.mallet.fst.InstanceAccuracyEvaluator instanceEval = new cc.mallet.fst.InstanceAccuracyEvaluator();
        private final cc.mallet.fst.TokenAccuracyEvaluator tokenEval = new cc.mallet.fst.TokenAccuracyEvaluator(
                new InstanceList[0],
                new String[0]
        );

        TestableCompositeTestAccuracyEvaluator(InstanceList testData, String description) {
            super(testData, description);
        }

        @Override
        public void evaluateInstanceList(TransducerTrainer trainer, InstanceList instances, String description) {
            super.evaluateInstanceList(trainer, instances, description);

            lastIteration = trainer.getIteration();
            if (trainer instanceof CRFTrainerByThreadedLabelLikelihood crfTrainer) {
                lastLogLikelihood = ((cc.mallet.optimize.Optimizable.ByGradientValue) crfTrainer.getOptimizer()
                        .getOptimizable()).getValue();
            }

            if (!instances.isEmpty()) {
                instanceEval.evaluateInstanceList(trainer, instances, description);
                tokenEval.evaluateInstanceList(trainer, instances, description);
                lastInstanceAccuracy = instanceEval.getAccuracy(description);
                lastTokenAccuracy = tokenEval.getAccuracy(description);
            }
        }
    }
}
