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

import cc.mallet.fst.Transducer;
import cc.mallet.fst.TransducerEvaluator;
import cc.mallet.fst.TransducerTrainer;
import cc.mallet.types.InstanceList;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * An evaluator that writes the current transducer (model) to file using Java serialization.
 *
 * <p>
 * This evaluator serializes the transducer at specified iteration intervals, allowing model
 * checkpoints to be saved during training. The serialized models can later be deserialized for
 * inference or to resume training.
 *
 * <p>
 * Output files are named using the pattern {@code {prefix}_iter{N}.{suffix}}, where N is the
 * iteration number.
 *
 * <p>
 * Example usage:
 *
 * <pre>
 * {
 *     &#64;code
 *     ModelOutputConfiguration config = ModelOutputConfiguration.builder().outputDirectory(Path.of("models"))
 *             .filePrefix("crf_model").fileSuffix("ser").iterationInterval(10).build();
 *     ModelOutputEvaluator evaluator = new ModelOutputEvaluator(config);
 *
 *     // Add to trainer
 *     trainer.addEvaluator(evaluator);
 * }
 * </pre>
 *
 * @see ModelOutputConfiguration
 */
@NullMarked
public class ModelOutputEvaluator extends TransducerEvaluator {
    private static final Logger logger = LoggerFactory.getLogger(ModelOutputEvaluator.class);

    private Path basePath = Paths.get("").toAbsolutePath();
    private final ModelOutputConfiguration configuration;

    /**
     * Creates a new model output evaluator with the specified configuration.
     *
     * @param configuration the evaluator configuration
     */
    public ModelOutputEvaluator(ModelOutputConfiguration configuration) {
        super();

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
        throw new UnsupportedOperationException("This evaluator does not support evaluating instance lists.");
    }

    @Override
    public void evaluate(TransducerTrainer trainer) {
        int iteration = trainer.getIteration();

        if (iteration % configuration.iterationInterval() != 0) {
            return;
        }

        Path outputFile = basePath.resolve(configuration.outputDirectory())
                .resolve(String.format("%s%d%s", configuration.filePrefix(), iteration, configuration.fileSuffix()));

        try {
            writeModel(trainer.getTransducer(), outputFile);
            logger.atInfo().addArgument(iteration).addArgument(outputFile).log("Iteration {}: Wrote model to {}");
        } catch (IOException e) {
            logger.atError().addArgument(iteration).addArgument(outputFile).setCause(e)
                    .log("Iteration {}: Failed to write model to {}");
        }
    }

    private void writeModel(Transducer transducer, Path outputFile) throws IOException {
        Path parentDir = outputFile.getParent();
        if (parentDir != null) {
            Files.createDirectories(parentDir);
        }

        try (ObjectOutputStream objectOut = new ObjectOutputStream(Files.newOutputStream(outputFile))) {
            objectOut.writeObject(transducer);
        }
    }
}
