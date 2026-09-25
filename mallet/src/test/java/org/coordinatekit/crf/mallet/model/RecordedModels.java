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
package org.coordinatekit.crf.mallet.model;

import static org.coordinatekit.crf.core.feature.Feature.createFeatureWithValue;

import org.coordinatekit.crf.core.StringTagProvider;
import org.coordinatekit.crf.core.feature.FeatureExtractor;
import org.coordinatekit.crf.core.io.XmlTrainingData;
import org.coordinatekit.crf.mallet.train.MalletCrfTrainer;
import org.coordinatekit.crf.mallet.train.MalletCrfTrainerConfiguration;
import org.coordinatekit.crf.mallet.train.ModelOutputConfiguration;
import org.coordinatekit.crf.mallet.train.WeightsType;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Trains the CRF models whose serialized graphs the native image metadata must cover.
 *
 * <p>
 * One model per trainer option that can change the serialized graph, plus the checkpoint files the
 * model output path writes. The JVM metadata guard deserializes these through a recording filter to
 * build {@code serialization-config.json}, and the {@code verification} native image loads the same
 * set to prove GraalVM accepts that file. Both callers train from here so the two can never cover
 * different graphs.
 */
@NullMarked
public final class RecordedModels {
    /**
     * A named trainer configuration whose model joins the recorded set.
     *
     * @param name a descriptive snake_case name for the case
     * @param configuration the trainer configuration to train under
     */
    private record RecordedCase(String name, MalletCrfTrainerConfiguration configuration) {}

    private static final FeatureExtractor FEATURE_EXTRACTOR = (sequence, position) -> {
        String token = sequence.get(position).token();
        return Set.of(
                createFeatureWithValue("LENGTH", String.valueOf(token.length())),
                createFeatureWithValue("LOWER", token.toLowerCase(Locale.ROOT))
        );
    };
    private static final StringTagProvider TAG_PROVIDER = new StringTagProvider("0");
    private static final String TRAINING_DATA_RESOURCE = "/org/coordinatekit/crf/mallet/test_addresses.xml";

    private RecordedModels() {}

    private static MalletCrfTrainerConfiguration.Builder baseConfiguration() {
        return MalletCrfTrainerConfiguration.builder()
                .iterations(1)
                .trainingFraction(1.0)
                .conllOutputEnabled(false)
                .modelOutputEnabled(false);
    }

    /**
     * Trains every recorded model into a directory and copies in the parts-of-speech fixture.
     *
     * @param arguments the command-line arguments; the only one is the output directory
     * @throws IOException if training or writing a model fails
     */
    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected exactly one argument, the output directory.");
        }
        Path outputDirectory = Path.of(arguments[0]);
        trainAll(outputDirectory);
        Path fixture = PartsOfSpeechModel.INSTANCE.modelPath();
        Files.copy(fixture, outputDirectory.resolve(fixture.getFileName()), StandardCopyOption.REPLACE_EXISTING);
    }

    private static List<RecordedCase> recordedCases(Path checkpointDirectory) {
        return List.of(
                new RecordedCase("defaults", baseConfiguration().build()),
                new RecordedCase("dense_weights", baseConfiguration().weightsType(WeightsType.DENSE).build()),
                new RecordedCase(
                        "model_checkpoints",
                        baseConfiguration().iterations(2)
                                .modelOutputEnabled(true)
                                .modelOutputConfiguration(
                                        ModelOutputConfiguration.builder()
                                                .outputDirectory(checkpointDirectory)
                                                .iterationInterval(1)
                                                .build()
                                )
                                .build()
                ),
                new RecordedCase("not_fully_connected", baseConfiguration().fullyConnected(false).build()),
                new RecordedCase("some_dense_weights", baseConfiguration().weightsType(WeightsType.SOME_DENSE).build()),
                new RecordedCase("sparse_weights", baseConfiguration().weightsType(WeightsType.SPARSE).build())
        );
    }

    /**
     * Trains each recorded case into {@code <outputDirectory>/<name>.crf}.
     *
     * <p>
     * The {@code model_checkpoints} case also writes its checkpoints into
     * {@code <outputDirectory>/checkpoints/}.
     *
     * @param outputDirectory the directory to write models into, created if missing
     * @return every model file written, checkpoints included, in a stable order
     * @throws IOException if training or writing a model fails
     * @throws IllegalStateException if the checkpoint directory is missing or empty
     */
    public static List<Path> trainAll(Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        Path checkpointDirectory = outputDirectory.resolve("checkpoints");
        Path trainingPath;
        try {
            trainingPath = Path
                    .of(Objects.requireNonNull(RecordedModels.class.getResource(TRAINING_DATA_RESOURCE)).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Cannot resolve " + TRAINING_DATA_RESOURCE + ".", e);
        }

        List<Path> models = new ArrayList<>();
        for (RecordedCase recordedCase : recordedCases(checkpointDirectory)) {
            Path modelPath = outputDirectory.resolve(recordedCase.name() + ".crf");
            new MalletCrfTrainer<>(
                    FEATURE_EXTRACTOR,
                    TAG_PROVIDER,
                    new XmlTrainingData<>(TAG_PROVIDER),
                    recordedCase.configuration()
            ).train(trainingPath, modelPath);
            models.add(modelPath);
        }

        if (!Files.isDirectory(checkpointDirectory)) {
            throw new IllegalStateException("The model_checkpoints case should create the checkpoint directory.");
        }
        try (Stream<Path> checkpoints = Files.list(checkpointDirectory)) {
            List<Path> files = checkpoints.sorted().toList();
            if (files.isEmpty()) {
                throw new IllegalStateException("The model_checkpoints case should write at least one checkpoint.");
            }
            models.addAll(files);
        }
        return models;
    }
}
