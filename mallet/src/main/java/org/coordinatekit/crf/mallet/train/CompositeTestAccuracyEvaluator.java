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

import cc.mallet.fst.CRFTrainerByThreadedLabelLikelihood;
import cc.mallet.fst.InstanceAccuracyEvaluator;
import cc.mallet.fst.TokenAccuracyEvaluator;
import cc.mallet.fst.TransducerEvaluator;
import cc.mallet.fst.TransducerTrainer;
import cc.mallet.optimize.Optimizable;
import cc.mallet.types.InstanceList;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A composite evaluator that logs iteration statistics along with both instance and token accuracy
 * metrics on test data.
 *
 * <p>
 * This evaluator combines the functionality of MALLET's {@code InstanceAccuracyEvaluator} and
 * {@code TokenAccuracyEvaluator} into a single evaluator that logs results using SLF4J. It
 * evaluates only on test data and logs:
 * <ul>
 * <li>Iteration number</li>
 * <li>Log likelihood (training cost)</li>
 * <li>Instance accuracy (if test data is present)</li>
 * <li>Token accuracy (if test data is present)</li>
 * </ul>
 *
 * <p>
 * Instance accuracy measures the proportion of sequences where all tokens are correctly labeled.
 * Token accuracy measures the proportion of individual tokens that are correctly labeled.
 *
 * @see cc.mallet.fst.InstanceAccuracyEvaluator
 * @see cc.mallet.fst.TokenAccuracyEvaluator
 */
@NullMarked
public class CompositeTestAccuracyEvaluator extends TransducerEvaluator {
    private static final Logger logger = LoggerFactory.getLogger(CompositeTestAccuracyEvaluator.class);

    private final InstanceAccuracyEvaluator instanceAccuracyEvaluator;
    private final TokenAccuracyEvaluator tokenAccuracyEvaluator;

    /**
     * Creates a new composite evaluator for the specified test data.
     *
     * @param testData the test instances to evaluate on; may be empty but not null
     * @param description the description for this test data set
     */
    public CompositeTestAccuracyEvaluator(InstanceList testData, String description) {
        super(new InstanceList[] {testData}, new String[] {description});
        this.instanceAccuracyEvaluator = new InstanceAccuracyEvaluator();
        this.tokenAccuracyEvaluator = new TokenAccuracyEvaluator(new InstanceList[0], new String[0]);
    }

    @Override
    public void evaluateInstanceList(TransducerTrainer trainer, InstanceList instances, String description) {
        if (!logger.isInfoEnabled()) {
            return;
        }

        int iteration = trainer.getIteration();
        double logLikelihood = getLogLikelihood(trainer);

        if (instances.isEmpty()) {
            logger.atInfo().addArgument(iteration).addArgument(logLikelihood).log("Iteration {}: log likelihood = {}");
        } else {
            instanceAccuracyEvaluator.evaluateInstanceList(trainer, instances, description);
            tokenAccuracyEvaluator.evaluateInstanceList(trainer, instances, description);

            double instanceAccuracy = instanceAccuracyEvaluator.getAccuracy(description);
            double tokenAccuracy = tokenAccuracyEvaluator.getAccuracy(description);

            logger.atInfo().addArgument(iteration).addArgument(logLikelihood).addArgument(instanceAccuracy)
                    .addArgument(tokenAccuracy)
                    .log("Iteration {}: log likelihood = {}, instance accuracy = {}, token accuracy = {}");
        }
    }

    private double getLogLikelihood(TransducerTrainer trainer) {
        if (trainer instanceof CRFTrainerByThreadedLabelLikelihood crfTrainer) {
            return ((Optimizable.ByGradientValue) crfTrainer.getOptimizer().getOptimizable()).getValue();
        }
        return Double.NaN;
    }
}
