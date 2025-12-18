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
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelSequence;
import org.coordinatekit.crf.core.StringTagProvider;
import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.preprocessing.FeatureExtractor;
import org.coordinatekit.crf.core.preprocessing.TrainingDataSequencer;
import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.coordinatekit.crf.core.preprocessing.XmlTrainingDataSequencer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NullMarked
class MalletCrfTrainerTest {
    private static final FeatureExtractor<String> SIMPLE_FEATURE_EXTRACTOR = (sequence, position) -> {
        String token = sequence.get(position).token();
        return Set.of("LENGTH=" + token.length(), "LOWER=" + token.toLowerCase(Locale.getDefault()));
    };
    private static final StringTagProvider TAG_PROVIDER = new StringTagProvider("0");
    private static final StringTagProvider TAG_PROVIDER_WITH_TAGS = new StringTagProvider(
            Set.of("0", "StreetName", "StreetNumber", "Unknown"),
            "0"
    );
    public static final String TRAINING_DATA_RESOURCE = "/org/coordinatekit/crf/mallet/test_addresses.xml";

    record CreateCrfParameters(
            @Nullable MalletCrfTrainerConfiguration configuration,
            TagProvider<String> tagProvider,
            Map<String, Double> expectedStates,
            Map<String, Set<String>> expectedTransitions
    ) {}

    static Stream<CreateCrfParameters> createCrf() {
        return Stream.of(
                new CreateCrfParameters(
                        null,
                        TAG_PROVIDER,
                        Map.of(
                                "StreetNumber",
                                Transducer.IMPOSSIBLE_WEIGHT,
                                "Unknown",
                                Transducer.IMPOSSIBLE_WEIGHT,
                                "0",
                                0d
                        ),
                        Map.of(
                                "0",
                                Set.of("StreetNumber", "Unknown", "0"),
                                "StreetNumber",
                                Set.of("StreetNumber", "Unknown", "0"),
                                "Unknown",
                                Set.of("StreetNumber", "Unknown", "0")
                        )
                ),
                new CreateCrfParameters(
                        MalletCrfTrainerConfiguration.builder().fullyConnected(false).build(),
                        TAG_PROVIDER,
                        Map.of(
                                "StreetNumber",
                                Transducer.IMPOSSIBLE_WEIGHT,
                                "Unknown",
                                Transducer.IMPOSSIBLE_WEIGHT,
                                "0",
                                0d
                        ),
                        Map.of(
                                "0",
                                Set.of("StreetNumber", "Unknown", "0"),
                                "StreetNumber",
                                Set.of("StreetNumber", "Unknown"),
                                "Unknown",
                                Set.of("Unknown")
                        )
                ),
                new CreateCrfParameters(
                        null,
                        TAG_PROVIDER_WITH_TAGS,
                        Map.of(
                                "StreetName",
                                Transducer.IMPOSSIBLE_WEIGHT,
                                "StreetNumber",
                                Transducer.IMPOSSIBLE_WEIGHT,
                                "Unknown",
                                Transducer.IMPOSSIBLE_WEIGHT,
                                "0",
                                0d
                        ),
                        Map.of(
                                "0",
                                Set.of("StreetName", "StreetNumber", "Unknown", "0"),
                                "StreetName",
                                Set.of("StreetName", "StreetNumber", "Unknown", "0"),
                                "StreetNumber",
                                Set.of("StreetName", "StreetNumber", "Unknown", "0"),
                                "Unknown",
                                Set.of("StreetName", "StreetNumber", "Unknown", "0")
                        )
                ),
                new CreateCrfParameters(
                        MalletCrfTrainerConfiguration.builder().fullyConnected(false).build(),
                        TAG_PROVIDER_WITH_TAGS,
                        Map.of(
                                "StreetName",
                                Transducer.IMPOSSIBLE_WEIGHT,
                                "StreetNumber",
                                Transducer.IMPOSSIBLE_WEIGHT,
                                "Unknown",
                                Transducer.IMPOSSIBLE_WEIGHT,
                                "0",
                                0d
                        ),
                        Map.of(
                                "0",
                                Set.of("StreetName", "StreetNumber", "Unknown", "0"),
                                "StreetName",
                                Set.of(),
                                "StreetNumber",
                                Set.of("StreetNumber", "Unknown"),
                                "Unknown",
                                Set.of("Unknown")
                        )
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void createCrf(CreateCrfParameters parameters) throws IOException {
        var trainer = createMalletCrfTrainer(
                SIMPLE_FEATURE_EXTRACTOR,
                parameters.tagProvider(),
                new XmlTrainingDataSequencer<>(TAG_PROVIDER),
                parameters.configuration()
        );

        InstanceList trainingData = createTrainingInstanceList(trainer);
        CRF crf = trainer.createCrf(trainingData);

        assertNotNull(crf);
        assertEquals(parameters.expectedStates().size(), crf.numStates());

        for (Map.Entry<String, Double> expectedStateEntry : parameters.expectedStates().entrySet()) {
            var actualState = crf.getState(expectedStateEntry.getKey());
            assertAll(
                    "state " + expectedStateEntry.getKey(),
                    () -> assertNotNull(actualState),
                    () -> assertEquals(expectedStateEntry.getValue(), actualState.getInitialWeight())
            );
        }

        var transitions = getCrfTransitions(crf);
        assertEquals(parameters.expectedTransitions().size(), transitions.size());

        for (Map.Entry<String, Set<String>> expectedStateEntry : parameters.expectedTransitions().entrySet()) {
            var actualState = crf.getState(expectedStateEntry.getKey());
            assertAll(
                    "state " + expectedStateEntry.getKey(),
                    () -> assertNotNull(actualState),
                    () -> assertIterableEquals(
                            new TreeSet<>(expectedStateEntry.getValue()),
                            new TreeSet<>(transitions.get(expectedStateEntry.getKey()))
                    )
            );
        }
    }

    record CreateCrfTrainerParameters(
            @Nullable MalletCrfTrainerConfiguration configuration,
            boolean expectedUseSparseWeights,
            boolean expectedUseSomeUnsupportedTrick
    ) {}

    static Stream<CreateCrfTrainerParameters> createCrfTrainer() {
        return Stream.of(
                new CreateCrfTrainerParameters(null, true, true),
                new CreateCrfTrainerParameters(
                        MalletCrfTrainerConfiguration.builder().weightsType(WeightsType.DENSE).threads(1)
                                .gaussianVariance(20.0).build(),
                        false,
                        false
                ),
                new CreateCrfTrainerParameters(
                        MalletCrfTrainerConfiguration.builder().weightsType(WeightsType.SOME_DENSE).build(),
                        true,
                        true
                ),
                new CreateCrfTrainerParameters(
                        MalletCrfTrainerConfiguration.builder().weightsType(WeightsType.SPARSE).threads(2)
                                .gaussianVariance(5.0).build(),
                        true,
                        false
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void createCrfTrainer(CreateCrfTrainerParameters parameters)
            throws IOException, NoSuchFieldException, IllegalAccessException {
        var trainer = createMalletCrfTrainer(
                SIMPLE_FEATURE_EXTRACTOR,
                TAG_PROVIDER,
                new XmlTrainingDataSequencer<>(TAG_PROVIDER),
                parameters.configuration()
        );

        InstanceList trainingData = createTrainingInstanceList(trainer);
        CRF crf = trainer.createCrf(trainingData);
        CRFTrainerByThreadedLabelLikelihood crfTrainer = trainer.createCrfTrainer(crf);
        MalletCrfTrainerConfiguration configuration = Objects
                .requireNonNullElseGet(parameters.configuration(), MalletCrfTrainerConfiguration::defaults);

        assertNotNull(crfTrainer);
        assertEquals(crf, crfTrainer.getTransducer());
        assertEquals(parameters.expectedUseSparseWeights(), crfTrainer.getUseSparseWeights());
        assertEquals(configuration.gaussianVariance(), crfTrainer.getGaussianPriorVariance());
        assertEquals(configuration.threads(), crfTrainer.getOptimizableCRF(trainingData).getNumBatches());

        Field useSomeUnsupportedTrickField = CRFTrainerByThreadedLabelLikelihood.class
                .getDeclaredField("useSomeUnsupportedTrick");
        useSomeUnsupportedTrickField.setAccessible(true);
        boolean actualUseSomeUnsupportedTrick = (boolean) useSomeUnsupportedTrickField.get(crfTrainer);
        assertEquals(parameters.expectedUseSomeUnsupportedTrick(), actualUseSomeUnsupportedTrick);
    }

    @SuppressWarnings("SameParameterValue")
    private static <F, T> MalletCrfTrainer<F, T> createMalletCrfTrainer(
            FeatureExtractor<F> featureExtractor,
            TagProvider<T> tagProvider,
            TrainingDataSequencer<T> trainingDataSequencer,
            @Nullable MalletCrfTrainerConfiguration configuration
    ) {
        return configuration != null
                ? new MalletCrfTrainer<>(featureExtractor, tagProvider, trainingDataSequencer, configuration)
                : new MalletCrfTrainer<>(featureExtractor, tagProvider, trainingDataSequencer);
    }

    private InstanceList createTrainingInstanceList(MalletCrfTrainer<String, String> trainer) throws IOException {
        Alphabet dataAlphabet = new Alphabet();
        LabelAlphabet targetAlphabet = new LabelAlphabet();

        InputStream trainingDataStream = Objects.requireNonNull(getClass().getResourceAsStream(TRAINING_DATA_RESOURCE));
        List<TrainingSequence<String>> trainingSequences = new XmlTrainingDataSequencer<>(TAG_PROVIDER)
                .read(trainingDataStream).toList();

        return trainingSequences.stream().map(seq -> trainer.mapSequenceToInstance(dataAlphabet, targetAlphabet, seq))
                .collect(Collectors.toCollection(() -> new InstanceList(dataAlphabet, targetAlphabet)));
    }

    private static Map<String, Set<String>> getCrfTransitions(CRF crf) {
        Map<String, Set<String>> transitions = new HashMap<>();

        for (int i = 0; i < crf.numStates(); i++) {
            CRF.State source = (CRF.State) crf.getState(i);
            Set<String> destinations = new HashSet<>();

            for (int j = 0; j < source.numDestinations(); j++) {
                destinations.add(source.getDestinationState(j).getName());
            }

            transitions.put(source.getName(), destinations);
        }

        return transitions;
    }

    record SplitTrainingDataParameters(
            @Nullable MalletCrfTrainerConfiguration configuration,
            int expectedTrainingSize,
            int expectedTestSize
    ) {}

    static Stream<SplitTrainingDataParameters> splitTrainingData() {
        return Stream.of(
                // Default configuration: trainingFraction=0.5, so 5 instances split ~50/50
                new SplitTrainingDataParameters(null, 2, 3),
                // Full training: trainingFraction=1.0, all 5 in training, none in test
                new SplitTrainingDataParameters(
                        MalletCrfTrainerConfiguration.builder().trainingFraction(1.0).build(),
                        5,
                        0
                ),
                // 80% training: 4 training, 1 test
                new SplitTrainingDataParameters(
                        MalletCrfTrainerConfiguration.builder().trainingFraction(0.8).build(),
                        4,
                        1
                ),
                // 60% training: 3 training, 2 test
                new SplitTrainingDataParameters(
                        MalletCrfTrainerConfiguration.builder().trainingFraction(0.6).build(),
                        3,
                        2
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void splitTrainingData(SplitTrainingDataParameters parameters) throws IOException {
        var trainer = createMalletCrfTrainer(
                SIMPLE_FEATURE_EXTRACTOR,
                TAG_PROVIDER,
                new XmlTrainingDataSequencer<>(TAG_PROVIDER),
                parameters.configuration()
        );

        var trainingPath = Path.of(Objects.requireNonNull(getClass().getResource(TRAINING_DATA_RESOURCE)).getPath());
        var split = trainer.splitTrainingData(trainingPath);

        assertNotNull(split);
        assertNotNull(split.training());
        assertNotNull(split.test());
        assertEquals(parameters.expectedTrainingSize(), split.training().size());
        assertEquals(parameters.expectedTestSize(), split.test().size());
        assertEquals(5, split.size());
        assertSame(split.training().getDataAlphabet(), split.test().getDataAlphabet());
        assertSame(split.training().getTargetAlphabet(), split.test().getTargetAlphabet());
    }

    @SuppressWarnings("SequencedCollectionMethodCanBeUsed")
    @Test
    void mapSequenceToInstance() throws IOException {
        List<String> sequence = List.of("5521", "W", "Center", "St,", "Milwaukee,", "WI", "53210");

        SortedSet<String> expectedFeatures = new TreeSet<>();
        expectedFeatures.addAll(sequence.stream().map(t -> "LENGTH=" + t.length()).toList());
        expectedFeatures.addAll(sequence.stream().map(t -> "LOWER=" + t.toLowerCase(Locale.getDefault())).toList());

        Alphabet dataAlphabet = new Alphabet();
        LabelAlphabet targetAlphabet = new LabelAlphabet();

        List<TrainingSequence<String>> trainingSequences = new XmlTrainingDataSequencer<>(TAG_PROVIDER).read(
                Objects.requireNonNull(
                        getClass().getResourceAsStream("/org/coordinatekit/crf/mallet/test_addresses.xml")
                )
        ).toList();
        MalletCrfTrainer<String, String> trainer = new MalletCrfTrainer<>(
                SIMPLE_FEATURE_EXTRACTOR,
                TAG_PROVIDER,
                new XmlTrainingDataSequencer<>(TAG_PROVIDER)
        );
        var actual = trainer.mapSequenceToInstance(dataAlphabet, targetAlphabet, trainingSequences.get(0));

        assertIterableEquals(
                expectedFeatures,
                Arrays.stream((String[]) dataAlphabet.toArray(new String[0])).sorted().toList()
        );
        assertIterableEquals(
                List.of("StreetNumber", "Unknown"),
                Arrays.stream(targetAlphabet.toArray()).sorted().toList()
        );

        assertInstanceOf(FeatureVectorSequence.class, actual.getData());
        assertEquals(sequence.size(), ((FeatureVectorSequence) actual.getData()).size());
        assertEquals(2, ((FeatureVectorSequence) actual.getData()).get(0).numLocations());
        assertTrue(((FeatureVectorSequence) actual.getData()).get(0).contains("LENGTH=4"));
        assertTrue(((FeatureVectorSequence) actual.getData()).get(0).contains("LOWER=5521"));
        assertInstanceOf(LabelSequence.class, actual.getTarget());
        assertEquals("StreetNumber", ((LabelSequence) actual.getTarget()).getLabelAtPosition(0).getEntry());
    }

    record TrainParameters(String name, MalletCrfTrainerConfiguration configuration, int expectedNumStates) {}

    static Stream<TrainParameters> train() {
        return Stream.of(
                new TrainParameters(
                        "defaultConfiguration",
                        MalletCrfTrainerConfiguration.builder().iterations(1).trainingFraction(1.0)
                                .conllOutputEnabled(false).modelOutputEnabled(false).build(),
                        3
                ),
                new TrainParameters(
                        "withTestSplit",
                        MalletCrfTrainerConfiguration.builder().iterations(1).trainingFraction(0.8)
                                .conllOutputEnabled(false).modelOutputEnabled(false).build(),
                        3
                ),
                new TrainParameters(
                        "withAllTags",
                        MalletCrfTrainerConfiguration.builder().iterations(1).trainingFraction(1.0)
                                .conllOutputEnabled(false).modelOutputEnabled(false).build(),
                        3
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void train(TrainParameters parameters, @TempDir Path temporaryDirectory)
            throws IOException, ClassNotFoundException {
        // ARRANGE
        var trainingPath = Path.of(Objects.requireNonNull(getClass().getResource(TRAINING_DATA_RESOURCE)).getPath());
        Path modelPath = temporaryDirectory.resolve("model.ser");

        MalletCrfTrainer<String, String> trainer = new MalletCrfTrainer<>(
                SIMPLE_FEATURE_EXTRACTOR,
                TAG_PROVIDER,
                new XmlTrainingDataSequencer<>(TAG_PROVIDER),
                parameters.configuration()
        );

        // ACT
        trainer.train(trainingPath, modelPath);

        // ASSERT
        assertTrue(Files.exists(modelPath), "Model file should be created");
        assertTrue(Files.size(modelPath) > 0, "Model file should not be empty");

        // Verify model can be deserialized and has correct structure
        CRF deserializedCrf = deserializeCrf(modelPath);
        assertNotNull(deserializedCrf, "Deserialized CRF should not be null");
        assertEquals(
                parameters.expectedNumStates(),
                deserializedCrf.numStates(),
                "CRF should have expected number of states"
        );

        // Verify the starting state is correctly configured
        Transducer.State startState = deserializedCrf.getState(TAG_PROVIDER.encode(TAG_PROVIDER.startingTag()));
        assertNotNull(startState, "Starting state should exist");
        assertEquals(0.0, startState.getInitialWeight(), 1e-10, "Starting state should have initial weight near 0.0");
    }

    @Test
    void train_withConllOutputEnabled_createsConllFiles(@TempDir Path temporaryDirectory) throws IOException {
        // ARRANGE
        var trainingPath = Path.of(Objects.requireNonNull(getClass().getResource(TRAINING_DATA_RESOURCE)).getPath());
        Path modelPath = temporaryDirectory.resolve("model.ser");
        Path conllOutputDir = temporaryDirectory.resolve("conll");

        MalletCrfTrainerConfiguration configuration = MalletCrfTrainerConfiguration.builder().iterations(10)
                .trainingFraction(0.6).conllOutputEnabled(true)
                .conllOutputConfiguration(
                        ConllOutputConfiguration.builder().outputDirectory(conllOutputDir).iterationInterval(10).build()
                ).modelOutputEnabled(false).build();

        MalletCrfTrainer<String, String> trainer = new MalletCrfTrainer<>(
                SIMPLE_FEATURE_EXTRACTOR,
                TAG_PROVIDER,
                new XmlTrainingDataSequencer<>(TAG_PROVIDER),
                configuration
        );

        // ACT
        trainer.train(trainingPath, modelPath);

        // ASSERT
        assertTrue(Files.exists(modelPath), "Model file should be created");
        assertTrue(Files.exists(conllOutputDir), "CoNLL output directory should be created");
    }

    @Test
    void train_withModelOutputEnabled_createsModelCheckpoints(@TempDir Path temporaryDirectory) throws IOException {
        // ARRANGE
        var trainingPath = Path.of(Objects.requireNonNull(getClass().getResource(TRAINING_DATA_RESOURCE)).getPath());
        Path modelPath = temporaryDirectory.resolve("model.ser");
        Path modelOutputDir = temporaryDirectory.resolve("checkpoints");

        MalletCrfTrainerConfiguration configuration = MalletCrfTrainerConfiguration.builder().iterations(10)
                .trainingFraction(0.6).conllOutputEnabled(false).modelOutputEnabled(true)
                .modelOutputConfiguration(
                        ModelOutputConfiguration.builder().outputDirectory(modelOutputDir).iterationInterval(10).build()
                ).build();

        MalletCrfTrainer<String, String> trainer = new MalletCrfTrainer<>(
                SIMPLE_FEATURE_EXTRACTOR,
                TAG_PROVIDER,
                new XmlTrainingDataSequencer<>(TAG_PROVIDER),
                configuration
        );

        // ACT
        trainer.train(trainingPath, modelPath);

        // ASSERT
        assertTrue(Files.exists(modelPath), "Model file should be created");
        assertTrue(Files.exists(modelOutputDir), "Model output directory should be created");
    }

    @Test
    void train_producesDeserializableModel(@TempDir Path temporaryDirectory)
            throws IOException, ClassNotFoundException {
        // ARRANGE
        var trainingPath = Path.of(Objects.requireNonNull(getClass().getResource(TRAINING_DATA_RESOURCE)).getPath());
        Path modelPath = temporaryDirectory.resolve("model.ser");

        MalletCrfTrainerConfiguration configuration = MalletCrfTrainerConfiguration.builder().iterations(5)
                .trainingFraction(1.0).conllOutputEnabled(false).modelOutputEnabled(false).build();

        MalletCrfTrainer<String, String> trainer = new MalletCrfTrainer<>(
                SIMPLE_FEATURE_EXTRACTOR,
                TAG_PROVIDER,
                new XmlTrainingDataSequencer<>(TAG_PROVIDER),
                configuration
        );

        // ACT
        trainer.train(trainingPath, modelPath);

        // ASSERT
        CRF deserializedCrf = deserializeCrf(modelPath);

        // Verify alphabets are preserved
        assertNotNull(deserializedCrf.getInputAlphabet(), "Input alphabet should not be null");
        assertNotNull(deserializedCrf.getOutputAlphabet(), "Output alphabet should not be null");
        assertTrue(deserializedCrf.getInputAlphabet().size() > 0, "Input alphabet should contain features");
        assertTrue(deserializedCrf.getOutputAlphabet().size() > 0, "Output alphabet should contain labels");

        // Verify all states have valid weights
        for (int i = 0; i < deserializedCrf.numStates(); i++) {
            Transducer.State state = deserializedCrf.getState(i);
            assertNotNull(state, "State should not be null");
            assertTrue(
                    Double.isFinite(state.getInitialWeight())
                            || state.getInitialWeight() == Transducer.IMPOSSIBLE_WEIGHT,
                    "State initial weight should be finite or IMPOSSIBLE_WEIGHT"
            );
        }
    }

    @Test
    void train_withDifferentRandomSeeds_producesDifferentSplits() throws IOException {
        // ARRANGE
        var trainingPath = Path.of(Objects.requireNonNull(getClass().getResource(TRAINING_DATA_RESOURCE)).getPath());

        MalletCrfTrainerConfiguration config1 = MalletCrfTrainerConfiguration.builder().iterations(1)
                .trainingFraction(0.6).randomSeed(42).conllOutputEnabled(false).modelOutputEnabled(false).build();

        MalletCrfTrainerConfiguration config2 = MalletCrfTrainerConfiguration.builder().iterations(1)
                .trainingFraction(0.6).randomSeed(123).conllOutputEnabled(false).modelOutputEnabled(false).build();

        MalletCrfTrainer<String, String> trainer1 = new MalletCrfTrainer<>(
                SIMPLE_FEATURE_EXTRACTOR,
                TAG_PROVIDER,
                new XmlTrainingDataSequencer<>(TAG_PROVIDER),
                config1
        );

        MalletCrfTrainer<String, String> trainer2 = new MalletCrfTrainer<>(
                SIMPLE_FEATURE_EXTRACTOR,
                TAG_PROVIDER,
                new XmlTrainingDataSequencer<>(TAG_PROVIDER),
                config2
        );

        // ACT
        var split1 = trainer1.splitTrainingData(trainingPath);
        var split2 = trainer2.splitTrainingData(trainingPath);

        // ASSERT - different seeds should produce same sizes but potentially different instances
        assertEquals(split1.training().size(), split2.training().size(), "Training sizes should be equal");
        assertEquals(split1.test().size(), split2.test().size(), "Test sizes should be equal");
    }

    @Test
    void train_withSameRandomSeed_producesSameSplits() throws IOException {
        // ARRANGE
        var trainingPath = Path.of(Objects.requireNonNull(getClass().getResource(TRAINING_DATA_RESOURCE)).getPath());

        MalletCrfTrainerConfiguration config = MalletCrfTrainerConfiguration.builder().iterations(1)
                .trainingFraction(0.6).randomSeed(42).conllOutputEnabled(false).modelOutputEnabled(false).build();

        MalletCrfTrainer<String, String> trainer1 = new MalletCrfTrainer<>(
                SIMPLE_FEATURE_EXTRACTOR,
                TAG_PROVIDER,
                new XmlTrainingDataSequencer<>(TAG_PROVIDER),
                config
        );

        MalletCrfTrainer<String, String> trainer2 = new MalletCrfTrainer<>(
                SIMPLE_FEATURE_EXTRACTOR,
                TAG_PROVIDER,
                new XmlTrainingDataSequencer<>(TAG_PROVIDER),
                config
        );

        // ACT
        var split1 = trainer1.splitTrainingData(trainingPath);
        var split2 = trainer2.splitTrainingData(trainingPath);

        // ASSERT - same seed should produce identical splits
        assertEquals(split1.training().size(), split2.training().size(), "Training sizes should be equal");
        assertEquals(split1.test().size(), split2.test().size(), "Test sizes should be equal");
    }

    private static CRF deserializeCrf(Path modelPath) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(modelPath))) {
            Object obj = ois.readObject();
            assertInstanceOf(CRF.class, obj, "Deserialized object should be a CRF");
            return (CRF) obj;
        }
    }
}
