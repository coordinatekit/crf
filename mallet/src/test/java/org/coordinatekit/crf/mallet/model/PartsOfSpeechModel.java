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

import org.coordinatekit.crf.core.StringTagProvider;
import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.io.XmlTrainingData;
import org.coordinatekit.crf.core.preprocessing.FeatureExtractor;
import org.coordinatekit.crf.mallet.train.MalletCrfTrainer;
import org.coordinatekit.crf.mallet.train.MalletCrfTrainerConfiguration;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * A singleton enum providing a pre-trained parts-of-speech CRF model for testing purposes.
 *
 * <p>
 * This model uses simple features (token length and lowercase form) to tag tokens with
 * parts-of-speech labels such as NOUN, VERB, ADJ, etc. It can be used to verify tagging
 * functionality without requiring a full production model.
 */
@NullMarked
public enum PartsOfSpeechModel {
    /** The singleton instance of the parts-of-speech model. */
    INSTANCE;

    private static final String MODEL_PATH = "/org/coordinatekit/crf/mallet/parts_of_speech.crf";
    private static final StringTagProvider TAG_PROVIDER = new StringTagProvider("0");
    private static final String TRAINING_DATA_RESOURCE = "/org/coordinatekit/crf/mallet/test_parts_of_speech.xml";
    private static final SortedSet<String> VALID_TAGS = new TreeSet<>(
            Set.of("0", "ADJ", "ADV", "DET", "NOUN", "PREP", "PRON", "VERB")
    );

    /**
     * Generates a new CRF model file from the training data resource.
     *
     * @param modelPath the path where the trained model should be written
     * @throws IOException if an error occurs during training or writing the model
     */
    public void generate(Path modelPath) throws IOException {
        var trainer = new MalletCrfTrainer<>(
                featureExtractor(),
                tagProvider(),
                new XmlTrainingData<>(tagProvider()),
                MalletCrfTrainerConfiguration.builder().conllOutputEnabled(false).modelOutputEnabled(false)
                        .trainingFraction(1).build()
        );
        trainer.train(
                Path.of(Objects.requireNonNull(PartsOfSpeechModel.class.getResource(TRAINING_DATA_RESOURCE)).getPath()),
                modelPath
        );
    }

    /**
     * Returns the path to the pre-trained model resource.
     *
     * @return the path to the serialized CRF model file
     */
    public Path modelPath() {
        return Path.of(Objects.requireNonNull(getClass().getResource(MODEL_PATH)).getPath());
    }

    /**
     * Returns the feature extractor used by this model.
     *
     * <p>
     * The extractor generates features based on token length and lowercase form.
     *
     * @return the feature extractor for parts-of-speech tagging
     */
    public FeatureExtractor<String> featureExtractor() {
        return (sequence, position) -> {
            String token = sequence.get(position).token();
            return Set.of("LENGTH=" + token.length(), "LOWER=" + token.toLowerCase(Locale.getDefault()));
        };
    }

    /**
     * Returns the tag provider used by this model.
     *
     * @return the tag provider that maps between string tags and model labels
     */
    public TagProvider<String> tagProvider() {
        return TAG_PROVIDER;
    }

    /**
     * Returns the set of valid tags recognized by this model.
     *
     * @return a sorted set of valid parts-of-speech tags
     */
    public SortedSet<String> validTags() {
        return VALID_TAGS;
    }

    /**
     * Command-line entry point to regenerate the model file.
     *
     * @param args command-line arguments where the first argument is the output model path
     * @throws IOException if an error occurs during model generation
     */
    public static void main(String[] args) throws IOException {
        INSTANCE.generate(Path.of(args[0]));
    }
}
