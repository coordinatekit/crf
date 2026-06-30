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
package org.coordinatekit.crf.cli;

import static org.coordinatekit.crf.annotator.AnnotatorModels.taggingResult;
import static org.coordinatekit.crf.annotator.TaggingAction.EXIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.coordinatekit.crf.annotator.Annotator;
import org.coordinatekit.crf.annotator.AnnotatorConfiguration;
import org.coordinatekit.crf.annotator.AnnotatorRunner;
import org.coordinatekit.crf.annotator.AnnotatorSequence;
import org.coordinatekit.crf.annotator.AnnotatorToken;
import org.coordinatekit.crf.annotator.RetokenizeConfiguration;
import org.coordinatekit.crf.annotator.RetokenizeReviewer;
import org.coordinatekit.crf.annotator.TaggingInterface;
import org.coordinatekit.crf.annotator.TaggingResult;
import org.coordinatekit.crf.core.StringTagProvider;
import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.io.XmlTrainingData;
import org.coordinatekit.crf.core.preprocessing.FeatureExtractor;
import org.coordinatekit.crf.core.preprocessing.Features;
import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.coordinatekit.crf.core.preprocessing.WhitespaceTokenizer;
import org.coordinatekit.crf.core.tag.CrfTagger;
import org.coordinatekit.crf.core.tag.CrfTaggerLoader;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Tests {@link ResolvedServicesFactory}: that a fully wired {@link ResolvedServices} builder yields
 * factories whose {@code create} assembles a real {@link Annotator} or {@link RetokenizeReviewer},
 * both with a model (stub tagger) and without one (null tagger), and that the default-extractor
 * warning is emitted only when a model is loaded over an unresolved feature extractor. The
 * {@code annotator}/{@code reviewer} wiring tests drive an assembled component through the
 * package-private tagging-interface seam with a capturing interface, pinning that the key extractor
 * reaches the key view and the full extractor reaches the verbose view — a swap of the two builder
 * lines fails them. Behavioral coverage of tagging itself lives in {@code AnnotatorRunnerTest} /
 * {@code RetokenizeRunnerTest}. The resolution precedence and the model-loading guards are covered
 * by {@link ResolvedServicesTest}.
 */
class ResolvedServicesFactoryTest {
    private static final FeatureExtractor FEATURE_EXTRACTOR = (sequence, position) -> Set.of();
    private static final FeatureExtractor FULL_EXTRACTOR = (sequence, position) -> Set
            .of(Features.of("VERBOSE_" + sequence.get(position).token()));
    private static final FeatureExtractor KEY_EXTRACTOR = (sequence, position) -> Set
            .of(Features.of("KEY_" + sequence.get(position).token()));
    private static final CrfTagger<String> TAGGER = input -> {
        throw new UnsupportedOperationException("not used");
    };
    private static final CrfTaggerLoader TAGGER_LOADER = TestCrfTaggerLoader.returning(TAGGER);
    private static final TagProvider<String> TAG_PROVIDER = new StringTagProvider(Set.of("NN"), "NN");

    /** Assembles a CLI component from a factory, given a terminal; mirrors {@code create}. */
    interface Assembly {
        Object create(Terminal terminal) throws IOException;
    }

    record AssemblyParameters(String name, Assembly assembly) {}

    record WarningParameters(
            String name,
            Supplier<ResolvedServices.Builder> servicesBuilder,
            @Nullable Path modelPath,
            boolean expectWarning
    ) {}

    private static AnnotatorConfiguration annotatorConfiguration() {
        return AnnotatorConfiguration.builder().input(Path.of("in.txt")).output(Path.of("out.xml")).build();
    }

    private static void assertRoutedToViews(AnnotatorSequence<String> presented) {
        assertEquals(
                List.of(
                        Set.of(Features.of("KEY_the")),
                        Set.of(Features.of("KEY_quick")),
                        Set.of(Features.of("KEY_brown"))
                ),
                presented.tokens().stream().map(AnnotatorToken::features).toList(),
                "the key feature extractor must reach the key view"
        );
        assertEquals(
                List.of(
                        Set.of(Features.of("VERBOSE_the")),
                        Set.of(Features.of("VERBOSE_quick")),
                        Set.of(Features.of("VERBOSE_brown"))
                ),
                presented.tokens().stream().map(AnnotatorToken::verboseFeatures).toList(),
                "the full feature extractor must reach the verbose view"
        );
    }

    @Test
    void annotator__routesKeyToKeyViewAndFullToVerboseView(@TempDir Path tempDirectory) throws IOException {
        // ARRANGE //
        Path inputFile = tempDirectory.resolve("input.txt");
        Files.writeString(inputFile, "the quick brown" + System.lineSeparator(), StandardCharsets.UTF_8);
        Path outputFile = tempDirectory.resolve("output.xml");
        ResolvedServices resolvedServices = routingServices();
        CapturingTaggingInterface tagging = new CapturingTaggingInterface();

        // ACT //
        try (Terminal terminal = dumbTerminal(OutputStream.nullOutputStream())) {
            ResolvedServicesFactory.annotator(resolvedServices, null, terminal, tagging)
                    .annotate(inputFile, outputFile);
        }

        // ASSERT //
        assertEquals(1, tagging.presented.size(), "exactly one sequence should be presented");
        assertRoutedToViews(tagging.presented.getFirst());
    }

    static Stream<WarningParameters> annotatorFactory__warning() {
        return Stream.of(
                new WarningParameters(
                        "explicit_extractor_no_warning",
                        ResolvedServicesFactoryTest::builder,
                        null,
                        false
                ),
                new WarningParameters(
                        "missing_extractor_with_model_warns",
                        () -> ResolvedServices.builder().tagProvider(TAG_PROVIDER).tokenizer(new WhitespaceTokenizer())
                                .taggerLoader(TAGGER_LOADER),
                        Path.of("model.bin"),
                        true
                ),
                new WarningParameters(
                        "missing_extractor_without_model_no_warning",
                        () -> ResolvedServices.builder().tagProvider(TAG_PROVIDER).tokenizer(new WhitespaceTokenizer()),
                        null,
                        false
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void annotatorFactory__warning(WarningParameters parameters) throws IOException {
        // ARRANGE //
        AnnotatorRunner.AnnotatorFactory factory = ResolvedServicesFactory
                .annotatorFactory(parameters.servicesBuilder().get(), parameters.modelPath());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // ACT //
        try (Terminal terminal = dumbTerminal(output)) {
            factory.create(annotatorConfiguration(), terminal);
        }

        // ASSERT //
        boolean warned = output.toString(StandardCharsets.UTF_8).contains("no FullFeatureExtractor is registered");
        assertEquals(
                parameters.expectWarning(),
                warned,
                parameters.name() + ": warning mismatch; output was: " + output
        );
    }

    private static ResolvedServices.Builder builder() {
        return ResolvedServices.builder().tagProvider(TAG_PROVIDER).fullFeatureExtractor(FEATURE_EXTRACTOR)
                .tokenizer(new WhitespaceTokenizer()).taggerLoader(TAGGER_LOADER);
    }

    private static DumbTerminal dumbTerminal(OutputStream output) throws IOException {
        return new DumbTerminal("test", "ansi", new ByteArrayInputStream(new byte[0]), output, StandardCharsets.UTF_8);
    }

    static Stream<AssemblyParameters> factory__assemblesWithoutThrowing() {
        return Stream.of(
                new AssemblyParameters(
                        "annotator_with_model",
                        terminal -> ResolvedServicesFactory.annotatorFactory(builder(), Path.of("model.bin"))
                                .create(annotatorConfiguration(), terminal)
                ),
                new AssemblyParameters(
                        "annotator_without_model",
                        terminal -> ResolvedServicesFactory.annotatorFactory(builder(), null)
                                .create(annotatorConfiguration(), terminal)
                ),
                new AssemblyParameters(
                        "reviewer_with_model",
                        terminal -> ResolvedServicesFactory.reviewerFactory(builder(), Path.of("model.bin"))
                                .create(retokenizeConfiguration(), terminal)
                ),
                new AssemblyParameters(
                        "reviewer_without_model",
                        terminal -> ResolvedServicesFactory.reviewerFactory(builder(), null)
                                .create(retokenizeConfiguration(), terminal)
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void factory__assemblesWithoutThrowing(AssemblyParameters parameters) throws IOException {
        // ACT //
        Object assembled;
        try (Terminal terminal = dumbTerminal(OutputStream.nullOutputStream())) {
            assembled = parameters.assembly().create(terminal);
        }

        // ASSERT //
        assertNotNull(assembled, parameters.name() + " factory should assemble a non-null instance");
    }

    private static RetokenizeConfiguration retokenizeConfiguration() {
        return RetokenizeConfiguration.builder().input(Path.of("in.xml")).output(Path.of("out.xml")).build();
    }

    private static ResolvedServices routingServices() {
        return ResolvedServices.builder().tagProvider(TAG_PROVIDER).tokenizer(new WhitespaceTokenizer())
                .fullFeatureExtractor(FULL_EXTRACTOR).keyFeatureExtractor(KEY_EXTRACTOR).resolve();
    }

    @Test
    void reviewer__routesKeyToKeyViewAndFullToVerboseView(@TempDir Path tempDirectory) throws IOException {
        // ARRANGE //
        Path inputFile = tempDirectory.resolve("input.xml");
        writeMisalignedSequence(inputFile);
        Path outputFile = tempDirectory.resolve("output.xml");
        ResolvedServices resolvedServices = routingServices();
        CapturingTaggingInterface tagging = new CapturingTaggingInterface();

        // ACT //
        try (Terminal terminal = dumbTerminal(OutputStream.nullOutputStream())) {
            ResolvedServicesFactory.reviewer(resolvedServices, null, terminal, tagging).review(inputFile, outputFile);
        }

        // ASSERT //
        assertEquals(1, tagging.presented.size(), "exactly one sequence should be presented");
        assertRoutedToViews(tagging.presented.getFirst());
    }

    /**
     * Writes a one-sequence XML file whose single stored token re-tokenizes into three under
     * {@link WhitespaceTokenizer}, so the reviewer sees it as misaligned and presents it.
     */
    private static void writeMisalignedSequence(Path inputFile) throws IOException {
        XmlTrainingData<String> xml = new XmlTrainingData<>(TAG_PROVIDER);
        try (var writer = xml.appendingWriter(inputFile)) {
            writer.write(TrainingSequence.ofTokens(List.of("the quick brown"), List.of("NN")));
        }
    }

    /** A tagging interface that records the presented sequence and exits to end the loop. */
    private static final class CapturingTaggingInterface implements TaggingInterface<String> {
        private final List<AnnotatorSequence<String>> presented = new ArrayList<>();

        @Override
        public TaggingResult<String> present(AnnotatorSequence<String> sequence) {
            presented.add(sequence);
            return taggingResult(EXIT, List.of());
        }
    }
}
