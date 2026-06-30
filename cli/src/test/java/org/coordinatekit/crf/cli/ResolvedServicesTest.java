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

import static org.coordinatekit.crf.cli.CommandExecutionTestSupport.assertMessageContains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.coordinatekit.crf.core.InputSequence;
import org.coordinatekit.crf.core.StringTagProvider;
import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.UncheckedCrfException;
import org.coordinatekit.crf.core.preprocessing.FeatureExtractor;
import org.coordinatekit.crf.core.preprocessing.Tokenizer;
import org.coordinatekit.crf.core.preprocessing.WhitespaceTokenizer;
import org.coordinatekit.crf.core.spi.AmbiguousServiceException;
import org.coordinatekit.crf.core.spi.CrfServices;
import org.coordinatekit.crf.core.spi.UnknownServiceException;
import org.coordinatekit.crf.core.tag.CrfTagger;
import org.coordinatekit.crf.core.tag.CrfTaggerLoader;
import org.coordinatekit.crf.mallet.tag.MalletCrfTaggerLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Tests {@link ResolvedServices}: the per-slot resolution precedence
 * ({@code explicit > single service implementation > built-in default}), the built-in defaults and
 * fail-fast on a missing tag provider, and the model-loading guards in
 * {@link ResolvedServices#loadTagger}.
 */
class ResolvedServicesTest {
    private static final TagProvider<String> TAG_PROVIDER = new StringTagProvider(Set.of("NN"), "NN");
    private static final CrfTagger<String> UNUSED_TAGGER = input -> {
        throw new UnsupportedOperationException("not used");
    };

    @Test
    void loadTagger__bundledLoaderReportsUnreadableModel() {
        // ARRANGE //
        // mallet is bundled on the CLI classpath, so resolve() discovers MalletCrfTaggerLoader and the
        // no-loader branch is unreachable here; a missing model now fails at load time instead.
        ResolvedServices resolvedServices = ResolvedServices.builder().tagProvider(TAG_PROVIDER).resolve();

        // ACT //
        CrfStartupException exception = assertThrows(
                CrfStartupException.class,
                () -> resolvedServices.loadTagger(Path.of("does-not-exist.crf"))
        );

        // ASSERT //
        assertMessageContains(exception, "failed to load", "does-not-exist.crf");
    }

    record LoadFailureParameters(String name, Exception thrown) {}

    static Stream<LoadFailureParameters> loadTagger__loadFailuresWrappedAsStartupException() {
        return Stream.of(
                new LoadFailureParameters("io_exception", new IOException("disk gone")),
                new LoadFailureParameters(
                        "class_not_found_unchecked",
                        new UncheckedCrfException(new ClassNotFoundException("cc.mallet.fst.CRF"))
                ),
                new LoadFailureParameters(
                        "wrong_type_class_cast",
                        new ClassCastException("class java.lang.String cannot be cast to class cc.mallet.fst.CRF")
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void loadTagger__loadFailuresWrappedAsStartupException(LoadFailureParameters parameters) {
        // ARRANGE //
        FeatureExtractor featureExtractor = (sequence, position) -> Set.of();
        ResolvedServices resolvedServices = ResolvedServices.builder().tagProvider(TAG_PROVIDER)
                .fullFeatureExtractor(featureExtractor).taggerLoader(TestCrfTaggerLoader.throwing(parameters.thrown()))
                .resolve();

        // ACT //
        CrfStartupException exception = assertThrows(
                CrfStartupException.class,
                () -> resolvedServices.loadTagger(Path.of("model.bin"))
        );

        // ASSERT //
        assertMessageContains(exception, "failed to load", "model.bin");
        assertSame(parameters.thrown(), exception.getCause(), "the boundary should preserve the cause");
    }

    @Test
    void loadTagger__loadsViaTaggerLoader() {
        // ARRANGE //
        FeatureExtractor featureExtractor = (sequence, position) -> Set.of();
        ResolvedServices resolvedServices = ResolvedServices.builder().tagProvider(TAG_PROVIDER)
                .fullFeatureExtractor(featureExtractor).taggerLoader(TestCrfTaggerLoader.returning(UNUSED_TAGGER))
                .resolve();

        // ACT //
        CrfTagger<?> loaded = resolvedServices.loadTagger(Path.of("model.bin"));

        // ASSERT //
        assertSame(UNUSED_TAGGER, loaded);
    }

    @Test
    void loadTagger__modelUsesFullFeatureExtractor() {
        // ARRANGE //
        FeatureExtractor fullFeatureExtractor = (sequence, position) -> Set.of();
        TestCrfTaggerLoader loader = TestCrfTaggerLoader.returning(UNUSED_TAGGER);
        ResolvedServices resolvedServices = ResolvedServices.builder().tagProvider(TAG_PROVIDER)
                .fullFeatureExtractor(fullFeatureExtractor).taggerLoader(loader).resolve();

        // ACT //
        resolvedServices.loadTagger(Path.of("model.bin"));

        // ASSERT //
        assertSame(
                fullFeatureExtractor,
                loader.capturedFeatureExtractor(),
                "the loader should receive the full feature extractor"
        );
        assertNotNull(loader.capturedFeatureFormat(), "the loader should receive the resolved feature format");
    }

    @Test
    void loadTagger__modelWithoutFeatureExtractorLoads() {
        // ARRANGE //
        TestCrfTaggerLoader loader = TestCrfTaggerLoader.returning(UNUSED_TAGGER);
        ResolvedServices resolvedServices = ResolvedServices.builder().tagProvider(TAG_PROVIDER).taggerLoader(loader)
                .resolve();

        // ACT //
        CrfTagger<?> loaded = resolvedServices.loadTagger(Path.of("model.bin"));

        // ASSERT //
        assertSame(UNUSED_TAGGER, loaded);
        assertNull(resolvedServices.fullFeatureExtractor(), "no full feature extractor should be resolved");
        FeatureExtractor substituted = loader.capturedFeatureExtractor();
        assertNotNull(substituted, "an empty feature extractor should be substituted for the absent full extractor");
        assertTrue(
                substituted.extractAt(new InputSequence(List.of("token")), 0).isEmpty(),
                "the substituted extractor yields no features"
        );
    }

    @Test
    void loadTagger__nullModelReturnsNull() {
        // ARRANGE //
        ResolvedServices resolvedServices = ResolvedServices.builder().tagProvider(TAG_PROVIDER).resolve();

        // ACT & ASSERT //
        assertNull(resolvedServices.loadTagger(null));
    }

    @Test
    void resolve__ambiguousServiceRewordsToStartupGuidance() {
        // ARRANGE //
        // reversed implementations so the class names arrive out of order; the expected message proves
        // AmbiguousServiceException sorts them.
        AmbiguousServiceException exception = new AmbiguousServiceException(TagProvider.class, List.of("second", 1));

        // ACT //
        CrfStartupException startupException = ResolvedServices.ambiguityStartupException(exception);

        // ASSERT //
        assertEquals(
                "multiple TagProvider service implementations found on the classpath: "
                        + "java.lang.Integer, java.lang.String; leave exactly one on the classpath"
                        + " or provide one explicitly",
                startupException.getMessage()
        );
        assertSame(exception, startupException.getCause());
    }

    @Test
    void resolve__ambiguousTaggerLoaderSuggestsFlag() {
        // ARRANGE //
        AmbiguousServiceException exception = new AmbiguousServiceException(
                CrfTaggerLoader.class,
                List.of(1, "second")
        );

        // ACT //
        CrfStartupException startupException = ResolvedServices.ambiguityStartupException(exception);

        // ASSERT //
        assertMessageContains(startupException, "CrfTaggerLoader", "--tagger-loader <name>");
    }

    @Test
    void resolve__explicitTokenizerOverridesDefault() {
        // ARRANGE //
        Tokenizer tokenizer = new WhitespaceTokenizer();
        ResolvedServices resolvedServices = ResolvedServices.builder().tagProvider(TAG_PROVIDER).tokenizer(tokenizer)
                .resolve();

        // ACT & ASSERT //
        assertSame(tokenizer, resolvedServices.tokenizer());
    }

    @Test
    void resolve__keyFeatureExtractorFallsBackToFull() {
        // ARRANGE //
        FeatureExtractor fullFeatureExtractor = (sequence, position) -> Set.of();
        ResolvedServices resolvedServices = ResolvedServices.builder().tagProvider(TAG_PROVIDER)
                .fullFeatureExtractor(fullFeatureExtractor).resolve();

        // ACT & ASSERT //
        assertSame(
                fullFeatureExtractor,
                resolvedServices.keyFeatureExtractor(),
                "the key view should fall back to the full extractor when no key extractor is registered"
        );
    }

    @Test
    void resolve__keyFeatureExtractorOverridesFull() {
        // ARRANGE //
        FeatureExtractor fullFeatureExtractor = (sequence, position) -> Set.of();
        FeatureExtractor keyFeatureExtractor = (sequence, position) -> Set.of();
        ResolvedServices resolvedServices = ResolvedServices.builder().tagProvider(TAG_PROVIDER)
                .fullFeatureExtractor(fullFeatureExtractor).keyFeatureExtractor(keyFeatureExtractor).resolve();

        // ACT & ASSERT //
        assertSame(keyFeatureExtractor, resolvedServices.keyFeatureExtractor(), "the explicit key extractor wins");
        assertSame(fullFeatureExtractor, resolvedServices.fullFeatureExtractor(), "the full extractor is unchanged");
    }

    @Test
    void resolve__keyFeatureExtractorWithoutFullStaysExplicit() {
        // ARRANGE //
        FeatureExtractor keyFeatureExtractor = (sequence, position) -> Set.of();

        // ACT //
        ResolvedServices resolvedServices = ResolvedServices.builder().tagProvider(TAG_PROVIDER)
                .keyFeatureExtractor(keyFeatureExtractor).resolve();

        // ASSERT //
        assertSame(keyFeatureExtractor, resolvedServices.keyFeatureExtractor());
        assertNull(resolvedServices.fullFeatureExtractor(), "full stays absent; only the key view is set");
    }

    @Test
    void resolve__missingTagProviderFailsFast() {
        // ACT //
        CrfStartupException exception = assertThrows(
                CrfStartupException.class,
                () -> ResolvedServices.builder().resolve()
        );

        // ASSERT //
        assertMessageContains(exception, "TagProvider");
    }

    @Test
    void resolve__taggerLoaderNameSelectsBundledLoader() {
        // ACT //
        ResolvedServices resolvedServices = ResolvedServices.builder().tagProvider(TAG_PROVIDER)
                .taggerLoaderName("mallet").resolve();

        // ASSERT //
        assertInstanceOf(MalletCrfTaggerLoader.class, resolvedServices.taggerLoader());
    }

    record UnknownRewordParameters(String name, List<String> availableNames, String expectedMessage) {}

    static Stream<UnknownRewordParameters> resolve__unknownServiceRewordsToStartupGuidance() {
        return Stream.of(
                new UnknownRewordParameters(
                        "sorts_available_names",
                        List.of("other", "mallet"),
                        "no CrfTaggerLoader named \"nope\" is on the classpath; available: mallet, other"
                ),
                new UnknownRewordParameters(
                        "empty_available_reports_none",
                        List.of(),
                        "no CrfTaggerLoader named \"nope\" is on the classpath; available: (none)"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void resolve__unknownServiceRewordsToStartupGuidance(UnknownRewordParameters parameters) {
        // ARRANGE //
        UnknownServiceException exception = new UnknownServiceException(
                "CrfTaggerLoader",
                "nope",
                parameters.availableNames()
        );

        // ACT //
        CrfStartupException startupException = ResolvedServices.unknownServiceStartupException(exception);

        // ASSERT //
        assertEquals(parameters.expectedMessage(), startupException.getMessage());
        assertSame(exception, startupException.getCause());
    }

    @Test
    void resolve__unknownTaggerLoaderNameFailsFast() {
        // ACT //
        // mallet is the only loader on the CLI classpath, so an unknown name re-words to startup guidance.
        CrfStartupException exception = assertThrows(
                CrfStartupException.class,
                () -> ResolvedServices.builder().tagProvider(TAG_PROVIDER).taggerLoaderName("nope").resolve()
        );

        // ASSERT //
        assertMessageContains(exception, "nope", "mallet");
    }

    @Test
    void resolve__usesDefaultsWhenNothingRegistered() {
        // ACT //
        ResolvedServices resolvedServices = ResolvedServices.builder().tagProvider(TAG_PROVIDER).resolve();

        // ASSERT //
        assertInstanceOf(WhitespaceTokenizer.class, resolvedServices.tokenizer());
        assertNull(resolvedServices.fullFeatureExtractor());
        assertNull(resolvedServices.keyFeatureExtractor());
        assertSame(TAG_PROVIDER, resolvedServices.tagProvider());
    }

    @Test
    void taggerLoader__malletBundledOnClasspath() {
        // ACT & ASSERT //
        // Regression guard: bundling :mallet in the CLI puts a CrfTaggerLoader on the runtime classpath,
        // so the annotator's --model path works out of the box without a hand-dropped ext/ jar.
        assertInstanceOf(MalletCrfTaggerLoader.class, CrfServices.taggerLoader(null).orElseThrow());
    }
}
