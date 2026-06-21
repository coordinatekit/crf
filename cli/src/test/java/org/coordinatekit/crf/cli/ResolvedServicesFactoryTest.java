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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.coordinatekit.crf.annotator.Annotator;
import org.coordinatekit.crf.annotator.AnnotatorConfiguration;
import org.coordinatekit.crf.annotator.AnnotatorRunner;
import org.coordinatekit.crf.annotator.RetokenizeConfiguration;
import org.coordinatekit.crf.annotator.RetokenizeReviewer;
import org.coordinatekit.crf.core.StringTagProvider;
import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.preprocessing.FeatureExtractor;
import org.coordinatekit.crf.core.preprocessing.Tokenizer;
import org.coordinatekit.crf.core.preprocessing.WhitespaceTokenizer;
import org.coordinatekit.crf.core.tag.CrfTagger;
import org.coordinatekit.crf.core.tag.CrfTaggerLoader;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Tests {@link ResolvedServicesFactory}: that a fully wired {@link ResolvedServices} builder yields
 * factories whose {@code create} assembles a real {@link Annotator} or {@link RetokenizeReviewer},
 * both with a model (stub tagger) and without one (null tagger), and that the default-extractor
 * warning is emitted only when a model is loaded over an unresolved feature extractor. The
 * assembled objects expose no seam to observe the wired tagger, so behavioral coverage of tagging
 * lives in {@code AnnotatorRunnerTest} / {@code RetokenizeRunnerTest}; here we assert only that
 * assembly succeeds. The resolution precedence and the model-loading guards are covered by
 * {@link ResolvedServicesTest}.
 */
class ResolvedServicesFactoryTest {
    private static final FeatureExtractor<String> FEATURE_EXTRACTOR = (sequence, position) -> Set.of();
    private static final CrfTagger<String, String> TAGGER = input -> {
        throw new UnsupportedOperationException("not used");
    };
    private static final CrfTaggerLoader TAGGER_LOADER = new CrfTaggerLoader() {
        @SuppressWarnings("unchecked")
        @NullMarked
        @Override
        public <F, T extends Comparable<T>> CrfTagger<F, T> load(
                Path modelPath,
                FeatureExtractor<F> featureExtractor,
                TagProvider<T> tagProvider,
                Tokenizer tokenizer
        ) {
            return (CrfTagger<F, T>) TAGGER;
        }
    };
    private static final TagProvider<String> TAG_PROVIDER = new StringTagProvider(Set.of("NN"), "NN");

    /** Assembles a CLI component from a factory, given a terminal; mirrors {@code create}. */
    interface Assembly {
        Object create(Terminal terminal) throws IOException;
    }

    record AssemblyParameters(String name, Assembly assembly) {}

    private static AnnotatorConfiguration annotatorConfiguration() {
        return AnnotatorConfiguration.builder().input(Path.of("in.txt")).output(Path.of("out.xml")).build();
    }

    @Test
    void annotatorFactory__explicitFeatureExtractorEmitsNoWarning() throws IOException {
        // ARRANGE //
        AnnotatorRunner.AnnotatorFactory factory = ResolvedServicesFactory.annotatorFactory(builder(), null);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // ACT //
        try (Terminal terminal = dumbTerminal(output)) {
            factory.create(annotatorConfiguration(), terminal);
        }

        // ASSERT //
        assertFalse(
                output.toString(StandardCharsets.UTF_8).contains("no FeatureExtractor is registered"),
                "no default-extractor warning should be written; was: " + output
        );
    }

    @Test
    void annotatorFactory__missingFeatureExtractorEmitsWarning() throws IOException {
        // ARRANGE //
        ResolvedServices.Builder servicesBuilder = ResolvedServices.builder().tagProvider(TAG_PROVIDER)
                .tokenizer(new WhitespaceTokenizer()).taggerLoader(TAGGER_LOADER);
        AnnotatorRunner.AnnotatorFactory factory = ResolvedServicesFactory
                .annotatorFactory(servicesBuilder, Path.of("model.bin"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // ACT //
        try (Terminal terminal = dumbTerminal(output)) {
            factory.create(annotatorConfiguration(), terminal);
        }

        // ASSERT //
        assertTrue(
                output.toString(StandardCharsets.UTF_8).contains("no FeatureExtractor is registered"),
                "the default-extractor warning should be written; was: " + output
        );
    }

    @Test
    void annotatorFactory__missingFeatureExtractorWithoutModelEmitsNoWarning() throws IOException {
        // ARRANGE //
        ResolvedServices.Builder servicesBuilder = ResolvedServices.builder().tagProvider(TAG_PROVIDER)
                .tokenizer(new WhitespaceTokenizer());
        AnnotatorRunner.AnnotatorFactory factory = ResolvedServicesFactory.annotatorFactory(servicesBuilder, null);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // ACT //
        try (Terminal terminal = dumbTerminal(output)) {
            factory.create(annotatorConfiguration(), terminal);
        }

        // ASSERT //
        assertFalse(
                output.toString(StandardCharsets.UTF_8).contains("no FeatureExtractor is registered"),
                "no warning should be written when no model is loaded; was: " + output
        );
    }

    private static ResolvedServices.Builder builder() {
        return ResolvedServices.builder().tagProvider(TAG_PROVIDER).featureExtractor(FEATURE_EXTRACTOR)
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
}
