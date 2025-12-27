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
import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.io.TrainingDataSequencer;
import org.coordinatekit.crf.core.preprocessing.FeatureExtractor;
import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.coordinatekit.crf.core.train.CrfTrainer;
import org.coordinatekit.crf.core.util.Serializables;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A CRF trainer implementation using the MALLET (MAchine Learning for LanguagE Toolkit) library.
 *
 * <p>
 * This class provides functionality to train Conditional Random Field models using MALLET's
 * {@link CRFTrainerByThreadedLabelLikelihood} for multithreaded training with L-BFGS optimization.
 * The trainer supports configurable parameters for regularization, threading, and weight storage
 * strategies.
 *
 * <p>
 * Example usage:
 *
 * <pre>
 * <code>
 * FeatureExtractor&lt;String&gt; extractor = ...;
 * TagProvider&lt;String&gt; tagProvider = new StringTagProvider("O");
 * TrainingDataSequencer&lt;String&gt; sequencer = new XmlTrainingDataSequencer&lt;&gt;(tagProvider);
 *
 * MalletCrfTrainer&lt;String, String&gt; trainer = new MalletCrfTrainer&lt;&gt;(
 *     extractor, tagProvider, sequencer
 * );
 * trainer.train(Path.of("training.xml"), Path.of("model.ser"));
 * </code>
 * </pre>
 *
 * @param <F> the type of features produced by the feature extractor
 * @param <T> the type of tags used for sequence labeling
 * @see MalletCrfTrainerConfiguration
 * @see CrfTrainer
 */
@NullMarked
public class MalletCrfTrainer<F, T extends Comparable<T>> implements CrfTrainer {
    private static final Logger logger = LoggerFactory.getLogger(MalletCrfTrainer.class);

    protected final FeatureExtractor<F> featureExtractor;
    protected final TagProvider<T> tagProvider;
    protected final TrainingDataSequencer<T> trainingDataSequencer;
    protected final MalletCrfTrainerConfiguration configuration;

    /**
     * Creates a new trainer with the specified components and default configuration.
     *
     * @param featureExtractor the feature extractor for converting tokens to feature sets
     * @param tagProvider the tag provider defining available tags and encoding
     * @param trainingDataSequencer the sequencer for reading training data
     */
    public MalletCrfTrainer(
            FeatureExtractor<F> featureExtractor,
            TagProvider<T> tagProvider,
            TrainingDataSequencer<T> trainingDataSequencer
    ) {
        this(featureExtractor, tagProvider, trainingDataSequencer, MalletCrfTrainerConfiguration.defaults());
    }

    /**
     * Creates a new trainer with the specified components and configuration.
     *
     * @param featureExtractor the feature extractor for converting tokens to feature sets
     * @param tagProvider the tag provider defining available tags and encoding
     * @param trainingDataSequencer the sequencer for reading training data
     * @param config the training configuration parameters
     */
    public MalletCrfTrainer(
            FeatureExtractor<F> featureExtractor,
            TagProvider<T> tagProvider,
            TrainingDataSequencer<T> trainingDataSequencer,
            MalletCrfTrainerConfiguration config
    ) {
        this.featureExtractor = featureExtractor;
        this.tagProvider = tagProvider;
        this.trainingDataSequencer = trainingDataSequencer;
        this.configuration = config;
    }

    /**
     * Creates a CRF model initialized with states derived from the training data.
     *
     * <p>
     * The CRF is configured with order-1 states (bigram label dependencies) using the tag provider's
     * starting tag as the initial state. All states except the start state are initialized with
     * impossible weight, ensuring sequences begin from the designated start state.
     *
     * @param training the training instances used to initialize the CRF structure
     * @return a new CRF model ready for training
     */
    protected CRF createCrf(InstanceList training) {
        Pattern forbiddenPat = Pattern.compile("\\s");
        Pattern allowedPat = Pattern.compile(".*");

        // Ensure the tags exist in the label alphabet
        for (T tag : tagProvider.tags()) {
            training.getTargetAlphabet().lookupIndex(tagProvider.encode(tag), true);
        }

        // Create the CRF object
        CRF crf = new CRF(training.getPipe(), null);

        // Add the label states
        String startName = crf.addOrderNStates(
                training,
                new int[] {1},
                null,
                tagProvider.encode(tagProvider.startingTag()),
                forbiddenPat,
                allowedPat,
                configuration.fullyConnected()
        );

        // Ensure the non-starting states have an impossible initial weight
        for (int i = 0; i < crf.numStates(); i++) {
            crf.getState(i).setInitialWeight(Transducer.IMPOSSIBLE_WEIGHT);
        }

        // Ensure the starting state has an initial weight of 0
        crf.getState(startName).setInitialWeight(0.0);

        return crf;
    }

    /**
     * Creates and configures a threaded CRF trainer for the given model.
     *
     * <p>
     * The trainer is configured with parameters from the current configuration, including the number of
     * threads, Gaussian prior variance for L2 regularization, and weight storage strategy.
     *
     * @param crf the CRF model to train
     * @return a configured trainer ready to optimize the model
     */
    protected CRFTrainerByThreadedLabelLikelihood createCrfTrainer(CRF crf) {
        var trainer = new CRFTrainerByThreadedLabelLikelihood(crf, configuration.threads());
        trainer.setGaussianPriorVariance(configuration.gaussianVariance());
        trainer.setUseSparseWeights(configuration.weightsType() != WeightsType.DENSE);
        trainer.setUseSomeUnsupportedTrick(configuration.weightsType() == WeightsType.SOME_DENSE);

        return trainer;
    }

    /**
     * Converts a training sequence into a MALLET {@link Instance}.
     *
     * <p>
     * This method extracts features from each token in the sequence using the configured feature
     * extractor, then constructs a {@link FeatureVectorSequence} for the input data and a
     * {@link LabelSequence} for the target labels. Features and labels are registered in the provided
     * alphabets.
     *
     * @param dataAlphabet the alphabet for mapping feature names to indices
     * @param targetAlphabet the alphabet for mapping label names to indices
     * @param trainingSequence the training sequence to convert
     * @return a MALLET instance containing feature vectors and label sequence
     */
    protected Instance mapSequenceToInstance(
            Alphabet dataAlphabet,
            LabelAlphabet targetAlphabet,
            TrainingSequence<T> trainingSequence
    ) {
        var featureTrainingSequence = featureExtractor.extractTraining(trainingSequence);

        int sequenceLength = featureTrainingSequence.size();
        FeatureVector[] featureVectors = new FeatureVector[sequenceLength];

        for (int i = 0; i < sequenceLength; i++) {
            var tokenFeatures = featureTrainingSequence.get(i).features();
            int[] featureIndices = tokenFeatures.stream().mapToInt(f -> dataAlphabet.lookupIndex(f, true)).toArray();
            featureVectors[i] = new FeatureVector(dataAlphabet, featureIndices);
        }

        FeatureVectorSequence data = new FeatureVectorSequence(featureVectors);

        int[] labelIndices = featureTrainingSequence.stream()
                .mapToInt(token -> targetAlphabet.lookupIndex(tagProvider.encode(token.tag()), true)).toArray();
        LabelSequence target = new LabelSequence(targetAlphabet, labelIndices);

        return new Instance(data, target, null, trainingSequence);
    }

    /**
     * Reads training data and splits it into training and test sets.
     *
     * <p>
     * This method reads sequences from the specified paths using the configured
     * {@link TrainingDataSequencer}, converts each sequence to a MALLET {@link Instance}, and splits
     * the resulting data according to {@link MalletCrfTrainerConfiguration#trainingFraction()}.
     *
     * <p>
     * The split is performed using {@link MalletCrfTrainerConfiguration#randomSeed()} for
     * reproducibility. If the training fraction is 1.0 or greater, all data is placed in the training
     * set and the test set will be empty.
     *
     * @param trainingPaths the paths to the training data file
     * @return a {@link TrainingTestSplit} containing the partitioned data
     * @throws IOException if an error occurs reading the training data
     */
    protected TrainingTestSplit splitTrainingData(Collection<Path> trainingPaths) throws IOException {
        Alphabet dataAlphabet = new Alphabet();
        LabelAlphabet targetAlphabet = new LabelAlphabet();
        InstanceList allTrainingData = new InstanceList(dataAlphabet, targetAlphabet);

        for (Path trainingPath : trainingPaths) {
            try (Stream<TrainingSequence<T>> trainingData = trainingDataSequencer.read(trainingPath)) {
                trainingData
                        .map(trainingSequence -> mapSequenceToInstance(dataAlphabet, targetAlphabet, trainingSequence))
                        .forEach(allTrainingData::add);
            }
        }

        if (configuration.trainingFraction() >= 1.0) {
            return new SimpleTrainingTestSplit(allTrainingData, new InstanceList(dataAlphabet, targetAlphabet));
        }

        var trainingLists = allTrainingData.split(
                new Random(configuration.randomSeed()),
                new double[] {configuration.trainingFraction(), 1 - configuration.trainingFraction()}
        );

        return new SimpleTrainingTestSplit(trainingLists[0], trainingLists[1]);
    }

    @Override
    public void train(Collection<Path> trainingPaths, Path modelPath) throws IOException {
        TrainingTestSplit trainingTestSplit = splitTrainingData(trainingPaths);
        CompositeTestAccuracyEvaluator evaluator = new CompositeTestAccuracyEvaluator(trainingTestSplit.test(), "test");

        CRF crf = createCrf(trainingTestSplit.training());
        logger.info("{} instances provided", trainingTestSplit.size());
        logger.info("Training on {} instances", trainingTestSplit.training().size());

        if (!trainingTestSplit.test().isEmpty()) {
            logger.info("Testing on {} instances", trainingTestSplit.test().size());
        }

        CRFTrainerByThreadedLabelLikelihood trainer = null;

        try {
            trainer = createCrfTrainer(crf);
            trainer.addEvaluator(evaluator);

            if (configuration.conllOutputEnabled()) {
                trainer.addEvaluator(
                        new ConllOutputEvaluator(
                                trainingTestSplit.test(),
                                "test",
                                configuration.conllOutputConfiguration()
                        )
                );
            }

            if (configuration.modelOutputEnabled()) {
                trainer.addEvaluator(new ModelOutputEvaluator(configuration.modelOutputConfiguration()));
            }

            trainer.train(trainingTestSplit.training(), configuration.iterations());
        } finally {
            if (trainer != null) {
                trainer.shutdown();
            }
        }

        Serializables.serialize(crf, modelPath);
    }
}
