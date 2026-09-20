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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.mallet.fst.CRF;
import org.coordinatekit.crf.core.util.Serializables;
import org.coordinatekit.crf.mallet.model.PartsOfSpeechModel;
import org.coordinatekit.crf.mallet.model.RecordedModels;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SortedSet;
import java.util.TreeSet;

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
    private static final String REGENERATE_COMMAND = "./gradlew :mallet:test -PregenerateSerializationConfig";
    private static final String REGENERATE_PROPERTY = "crf.regenerateSerializationConfig";

    private static SortedSet<String> committedClassNames(Path path) throws IOException {
        SortedSet<String> committed = new TreeSet<>();
        JsonNode root = JsonMapper.builder().build().readTree(Files.readString(path, StandardCharsets.UTF_8));
        for (JsonNode type : root.get("types")) {
            committed.add(type.get("name").asString());
        }
        return committed;
    }

    private static Path configurationPath() {
        String path = System.getProperty(CONFIGURATION_PATH_PROPERTY);
        if (path == null) {
            throw new IllegalStateException("The " + CONFIGURATION_PATH_PROPERTY + " system property is not set.");
        }
        return Path.of(path);
    }

    private static SortedSet<String> recordClassNames(Path temporaryDirectory) throws IOException {
        var recorder = new RecordingObjectInputFilter();

        Serializables.deserialize(CRF.class, PartsOfSpeechModel.INSTANCE.modelPath(), recorder);
        for (Path model : RecordedModels.trainAll(temporaryDirectory)) {
            Serializables.deserialize(CRF.class, model, recorder);
        }

        SortedSet<String> recorded = recorder.recordedClassNames();
        assertTrue(
                recorded.contains(CRF.class.getName()) && recorded.stream().anyMatch(name -> name.endsWith("[]")),
                "The recorder should have seen " + CRF.class.getName() + " and at least one array type, but recorded "
                        + recorded + "."
        );
        return recorded;
    }

    @DisabledIfSystemProperty(named = REGENERATE_PROPERTY, matches = "true")
    @Test
    void serializationConfig__coversRecordedClasses(@TempDir Path temporaryDirectory) throws IOException {
        // ARRANGE //
        SortedSet<String> recorded = recordClassNames(temporaryDirectory);
        SortedSet<String> committed = committedClassNames(configurationPath());

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

    @EnabledIfSystemProperty(named = REGENERATE_PROPERTY, matches = "true")
    @Test
    void serializationConfig__regenerates(@TempDir Path temporaryDirectory) throws IOException {
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
        assertEquals(recorded, committedClassNames(path));
    }
}
