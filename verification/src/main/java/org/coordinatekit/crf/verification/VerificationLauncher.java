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
package org.coordinatekit.crf.verification;

import static org.coordinatekit.crf.core.feature.Feature.createFeatureWithValue;

import org.coordinatekit.crf.core.StringTagProvider;
import org.coordinatekit.crf.core.feature.FeatureExtractor;
import org.coordinatekit.crf.core.spi.CrfServices;
import org.coordinatekit.crf.core.tag.CrfTagger;
import org.coordinatekit.crf.core.tag.CrfTaggerLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Entry point of the native image that stands in for a downstream application.
 *
 * <p>
 * The image loads every model in the directory named by its first argument through the
 * {@link org.coordinatekit.crf.core.tag.CrfTaggerLoader} discovered on its classpath, then tags one
 * sequence with each. A class missing from the {@code mallet} module's serialization metadata fails
 * here and nowhere else, so this run is the only check that GraalVM accepts what
 * {@code serialization-config.json} lists.
 */
public final class VerificationLauncher {
    private static final FeatureExtractor FEATURE_EXTRACTOR = (sequence, position) -> {
        String token = sequence.get(position).token();
        return Set.of(
                createFeatureWithValue("LENGTH", String.valueOf(token.length())),
                createFeatureWithValue("LOWER", token.toLowerCase(Locale.ROOT))
        );
    };

    private VerificationLauncher() {}

    /**
     * Loads every model in a directory and tags one sequence with each, printing a line per model.
     *
     * @param arguments the command-line arguments; the only one is the model directory
     * @throws IOException if a model cannot be read or deserialized
     * @throws IllegalArgumentException if the argument count is wrong
     * @throws IllegalStateException if no loader is discovered or the directory holds no model
     */
    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected exactly one argument, the model directory.");
        }
        CrfTaggerLoader loader = CrfServices.taggerLoader()
                .orElseThrow(() -> new IllegalStateException("No CrfTaggerLoader on the image classpath."));
        List<Path> models = sortedModelsIn(Path.of(arguments[0]));
        if (models.isEmpty()) {
            throw new IllegalStateException("No models found in " + arguments[0] + ".");
        }
        for (Path model : models) {
            CrfTagger<String> tagger = loader.load(
                    model,
                    FEATURE_EXTRACTOR,
                    CrfServices.featureFormat(),
                    new StringTagProvider("0"),
                    CrfServices.tokenizer()
            );
            System.out.println(model.getFileName() + " -> " + tagger.tag("5521 W Center St"));
        }
    }

    private static List<Path> sortedModelsIn(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile).sorted().toList();
        }
    }
}
