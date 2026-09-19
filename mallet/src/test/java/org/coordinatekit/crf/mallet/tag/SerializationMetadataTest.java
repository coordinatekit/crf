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
package org.coordinatekit.crf.mallet.tag;

import static org.coordinatekit.crf.core.feature.Feature.createFeatureWithValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.mallet.fst.CRF;
import org.coordinatekit.crf.core.StringTagProvider;
import org.coordinatekit.crf.core.feature.FeatureExtractor;
import org.coordinatekit.crf.core.io.XmlTrainingData;
import org.coordinatekit.crf.core.util.Serializables;
import org.coordinatekit.crf.mallet.model.PartsOfSpeechModel;
import org.coordinatekit.crf.mallet.train.MalletCrfTrainer;
import org.coordinatekit.crf.mallet.train.MalletCrfTrainerConfiguration;
import org.coordinatekit.crf.mallet.train.ModelOutputConfiguration;
import org.coordinatekit.crf.mallet.train.WeightsType;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Guards the native image serialization configuration for the MALLET model graph.
 *
 * <p>
 * A native image can deserialize only the classes its serialization configuration lists. This test
 * deserializes real models through a {@link RecordingObjectInputFilter}, covering the fixture model
 * plus models trained under each trainer option that can change the serialized graph, and compares
 * the recorded classes with the committed {@code serialization-config.json}. The guard passes when
 * the committed file covers every recorded class, so entries left over from a removed class do no
 * harm.
 *
 * <p>
 * Run {@code ./gradlew :mallet:test -PregenerateSerializationConfig} to rewrite the committed file
 * from the recorded classes, then review the diff.
 */
@NullMarked
class SerializationMetadataTest {
    private static final String CONFIGURATION_PATH_PROPERTY = "crf.serializationConfigPath";
    private static final FeatureExtractor FEATURE_EXTRACTOR = (sequence, position) -> {
        String token = sequence.get(position).token();
        return Set.of(
                createFeatureWithValue("LENGTH", String.valueOf(token.length())),
                createFeatureWithValue("LOWER", token.toLowerCase(Locale.ROOT))
        );
    };
    private static final String REGENERATE_COMMAND = "./gradlew :mallet:test -PregenerateSerializationConfig";
    private static final String TRAINING_DATA_RESOURCE = "/org/coordinatekit/crf/mallet/test_addresses.xml";

    private static final StringTagProvider TAG_PROVIDER = new StringTagProvider("0");

    /**
     * A named trainer configuration whose model joins the recorded set.
     *
     * @param name a descriptive snake_case name for the case
     * @param configuration the trainer configuration to train under
     */
    record RecordedCase(String name, MalletCrfTrainerConfiguration configuration) {}

    private static MalletCrfTrainerConfiguration.Builder baseConfiguration() {
        return MalletCrfTrainerConfiguration.builder().iterations(1).trainingFraction(1.0).conllOutputEnabled(false);
    }

    private static Path configurationPath() {
        String path = System.getProperty(CONFIGURATION_PATH_PROPERTY);
        if (path == null) {
            throw new IllegalStateException("The " + CONFIGURATION_PATH_PROPERTY + " system property is not set.");
        }
        return Path.of(path);
    }

    private static List<RecordedCase> recordedCases(Path checkpointDirectory) {
        return List.of(
                new RecordedCase("defaults", baseConfiguration().modelOutputEnabled(false).build()),
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

    private static SortedSet<String> recordClassNames(Path temporaryDirectory) throws IOException, URISyntaxException {
        var recorder = new RecordingObjectInputFilter();
        Path trainingPath = Path.of(
                Objects.requireNonNull(SerializationMetadataTest.class.getResource(TRAINING_DATA_RESOURCE)).toURI()
        );

        Serializables.deserialize(CRF.class, PartsOfSpeechModel.INSTANCE.modelPath(), recorder);

        for (RecordedCase recordedCase : recordedCases(temporaryDirectory.resolve("checkpoints"))) {
            Path modelPath = temporaryDirectory.resolve(recordedCase.name() + ".crf");
            new MalletCrfTrainer<>(
                    FEATURE_EXTRACTOR,
                    TAG_PROVIDER,
                    new XmlTrainingData<>(TAG_PROVIDER),
                    recordedCase.configuration()
            ).train(trainingPath, modelPath);
            Serializables.deserialize(CRF.class, modelPath, recorder);
        }

        Path checkpointDirectory = temporaryDirectory.resolve("checkpoints");
        try (Stream<Path> checkpoints = Files.list(checkpointDirectory)) {
            List<Path> files = checkpoints.sorted().toList();
            assertTrue(!files.isEmpty(), "The model_checkpoints case should write at least one checkpoint.");
            for (Path file : files) {
                Serializables.deserialize(CRF.class, file, recorder);
            }
        }

        return new TreeSet<>(recorder.recordedClassNames());
    }

    @DisabledIfSystemProperty(named = "crf.regenerateSerializationConfig", matches = "true")
    @Test
    void serializationConfig__coversRecordedClasses(@TempDir Path temporaryDirectory)
            throws IOException, URISyntaxException {
        // ARRANGE //
        SortedSet<String> recorded = recordClassNames(temporaryDirectory);
        SortedSet<String> committed = new TreeSet<>();
        JsonNode root = JsonMapper.builder().build().readTree(Files.readString(configurationPath()));
        for (JsonNode type : root.get("types")) {
            committed.add(type.get("name").asString());
        }

        // ACT //
        SortedSet<String> missing = new TreeSet<>(recorded);
        missing.removeAll(committed);

        // ASSERT //
        assertTrue(
                missing.isEmpty(),
                "serialization-config.json is missing " + missing.size() + " class(es) the recorded models load: "
                        + missing + ". Regenerate it with " + REGENERATE_COMMAND + " and review the diff."
        );
    }

    @EnabledIfSystemProperty(named = "crf.regenerateSerializationConfig", matches = "true")
    @Test
    void serializationConfig__regenerates(@TempDir Path temporaryDirectory) throws IOException, URISyntaxException {
        // ARRANGE //
        SortedSet<String> recorded = recordClassNames(temporaryDirectory);
        JsonMapper mapper = JsonMapper.builder().build();
        ObjectNode root = mapper.createObjectNode();
        ArrayNode types = root.putArray("types");
        for (String name : recorded) {
            types.addObject().put("name", name);
        }
        root.putArray("lambdaCapturingTypes");
        root.putArray("proxies");

        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter(
                Separators.createDefaultInstance()
                        .withObjectNameValueSpacing(Separators.Spacing.AFTER)
                        .withObjectEmptySeparator("")
                        .withArrayEmptySeparator("")
        ).withObjectIndenter(indenter).withArrayIndenter(indenter);

        // ACT //
        String json = mapper.writer().with(printer).writeValueAsString(root) + "\n";
        Path path = configurationPath();
        Files.createDirectories(path.getParent());
        Files.writeString(path, json, StandardCharsets.UTF_8);

        // ASSERT //
        assertEquals(json, Files.readString(path, StandardCharsets.UTF_8));
    }
}
