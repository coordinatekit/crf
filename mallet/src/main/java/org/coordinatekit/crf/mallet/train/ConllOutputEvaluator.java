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

import cc.mallet.fst.SumLatticeDefault;
import cc.mallet.fst.Transducer;
import cc.mallet.fst.TransducerEvaluator;
import cc.mallet.fst.TransducerTrainer;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Sequence;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * An evaluator that outputs predicted and actual labels in CoNLL format.
 *
 * <p>
 * This evaluator writes predictions to a file in a space-separated format with the following
 * columns:
 * <ul>
 * <li>token - the input token</li>
 * <li>actual - the ground truth label</li>
 * <li>predicted - the predicted label</li>
 * <li>confidence - the confidence score for the predicted label</li>
 * </ul>
 *
 * <p>
 * Sequences are separated by blank lines. The file includes a header row followed by a blank line.
 *
 * <p>
 * Example output:
 *
 * <pre>
 * token actual predicted confidence
 *
 * John B-PER B-PER 0.9823
 * lives O O 0.9956
 * in O O 0.9912
 * London B-LOC B-LOC 0.9734
 *
 * The O O 0.9989
 * cat O O 0.9876
 * </pre>
 */
@NullMarked
public class ConllOutputEvaluator extends TransducerEvaluator {
    private static final Logger logger = LoggerFactory.getLogger(ConllOutputEvaluator.class);
    private static final String HEADER = "token actual predicted confidence";

    private Path basePath = Paths.get("").toAbsolutePath();
    private final ConllOutputConfiguration configuration;

    /**
     * Creates a new CoNLL output evaluator with the specified configuration.
     *
     * @param testData the test instances to evaluate on
     * @param description the description for this test data set
     * @param configuration the evaluator configuration
     */
    public ConllOutputEvaluator(InstanceList testData, String description, ConllOutputConfiguration configuration) {
        super(new InstanceList[] {testData}, new String[] {description});
        this.configuration = configuration;
    }

    /**
     * Sets the base path for resolving relative output directories.
     *
     * <p>
     * This is package-private for testing purposes. In production, the base path defaults to the
     * current working directory.
     *
     * @param basePath the base path to use for resolving relative output directories
     */
    void setBasePath(Path basePath) {
        this.basePath = basePath;
    }

    @Override
    public void evaluateInstanceList(TransducerTrainer trainer, InstanceList instances, String description) {
        int iteration = trainer.getIteration();

        if (iteration % configuration.iterationInterval() != 0) {
            return;
        }

        if (instances.isEmpty()) {
            logger.atDebug().addArgument(iteration)
                    .log("Iteration {}: No instances to evaluate, skipping CoNLL output");
            return;
        }

        Path outputFile = basePath.resolve(configuration.outputDirectory())
                .resolve(String.format("%s%d%s", configuration.filePrefix(), iteration, configuration.fileSuffix()));

        try {
            writeConllOutput(trainer.getTransducer(), instances, outputFile);
            logger.atInfo().addArgument(iteration).addArgument(outputFile)
                    .log("Iteration {}: Wrote CoNLL output to {}");
        } catch (IOException e) {
            logger.atError().addArgument(iteration).addArgument(outputFile).setCause(e)
                    .log("Iteration {}: Failed to write CoNLL output to {}");
        }
    }

    private void writeConllOutput(Transducer transducer, InstanceList instances, Path outputFile) throws IOException {
        Path parentDir = outputFile.getParent();
        if (parentDir != null) {
            Files.createDirectories(parentDir);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
            writer.write(HEADER);
            writer.newLine();
            writer.newLine();

            boolean firstSequence = true;
            for (Instance instance : instances) {
                if (!firstSequence) {
                    writer.newLine();
                }
                firstSequence = false;

                writeSequence(transducer, instance, writer);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void writeSequence(Transducer transducer, Instance instance, BufferedWriter writer) throws IOException {
        Sequence<Object> input = (Sequence<Object>) instance.getData();
        Sequence<Object> actualOutput = (Sequence<Object>) instance.getTarget();
        Sequence<Object> predictedOutput = transducer.transduce(input);

        // Compute marginal probabilities for confidence scores
        SumLatticeDefault lattice = new SumLatticeDefault(transducer, input);

        for (int i = 0; i < input.size(); i++) {
            String token = input.get(i).toString();
            String actual = actualOutput.get(i).toString();
            String predicted = predictedOutput.get(i).toString();
            double confidence = getConfidence(transducer, lattice, i, predicted);

            writer.write(String.format("%s %s %s %.4f", escapeToken(token), actual, predicted, confidence));
            writer.newLine();
        }
    }

    private double getConfidence(
            Transducer transducer,
            SumLatticeDefault lattice,
            int position,
            String predictedLabel
    ) {
        // Find the state index for the predicted label and get its marginal probability
        for (int stateIndex = 0; stateIndex < transducer.numStates(); stateIndex++) {
            Transducer.State state = transducer.getState(stateIndex);
            if (state.getName().equals(predictedLabel)) {
                return lattice.getGammaProbability(position + 1, state);
            }
        }
        return 0.0;
    }

    private String escapeToken(String token) {
        // Replace spaces with underscores to maintain column alignment
        return token.replace(' ', '_');
    }
}
