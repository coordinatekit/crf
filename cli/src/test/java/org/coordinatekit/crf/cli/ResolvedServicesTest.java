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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.coordinatekit.crf.core.StringTagProvider;
import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.preprocessing.FeatureExtractor;
import org.coordinatekit.crf.core.preprocessing.Tokenizer;
import org.coordinatekit.crf.core.preprocessing.WhitespaceTokenizer;
import org.coordinatekit.crf.core.spi.AmbiguousServiceException;
import org.coordinatekit.crf.core.tag.CrfTagger;
import org.coordinatekit.crf.core.tag.CrfTaggerLoader;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Tests {@link ResolvedServices}: the per-slot resolution precedence
 * ({@code explicit > single service implementation > built-in default}), the built-in defaults and
 * fail-fast on a missing tag provider, and the model-loading guards in
 * {@link ResolvedServices#loadTagger}.
 */
class ResolvedServicesTest {
    private static final TagProvider<String> TAG_PROVIDER = new StringTagProvider(Set.of("NN"), "NN");
    private static final CrfTagger<String, String> UNUSED_TAGGER = input -> {
        throw new UnsupportedOperationException("not used");
    };

    private static CrfTaggerLoader loaderReturning(CrfTagger<?, ?> tagger) {
        return new CrfTaggerLoader() {
            @SuppressWarnings("unchecked")
            @NullMarked
            @Override
            public <F, T extends Comparable<T>> CrfTagger<F, T> load(
                    Path modelPath,
                    FeatureExtractor<F> featureExtractor,
                    TagProvider<T> tagProvider,
                    Tokenizer tokenizer
            ) {
                return (CrfTagger<F, T>) tagger;
            }
        };
    }

    private static CrfTaggerLoader loaderThrowing(IOException cause) {
        return new CrfTaggerLoader() {
            @NullMarked
            @Override
            public <F, T extends Comparable<T>> CrfTagger<F, T> load(
                    Path modelPath,
                    FeatureExtractor<F> featureExtractor,
                    TagProvider<T> tagProvider,
                    Tokenizer tokenizer
            ) throws IOException {
                throw cause;
            }
        };
    }

    @Test
    void loadTagger__ioExceptionWrappedAsStartupException() {
        // ARRANGE //
        FeatureExtractor<String> featureExtractor = (sequence, position) -> Set.of();
        ResolvedServices resolvedServices = ResolvedServices.builder().tagProvider(TAG_PROVIDER)
                .featureExtractor(featureExtractor).taggerLoader(loaderThrowing(new IOException("disk gone")))
                .resolve();

        // ACT //
        CrfStartupException exception = assertThrows(
                CrfStartupException.class,
                () -> resolvedServices.loadTagger(Path.of("model.bin"))
        );

        // ASSERT //
        assertTrue(
                exception.getMessage().contains("failed to load") && exception.getMessage().contains("model.bin"),
                "message should report the failure and the path; was: " + exception.getMessage()
        );
    }

    @Test
    void loadTagger__loadsViaTaggerLoader() {
        // ARRANGE //
        FeatureExtractor<String> featureExtractor = (sequence, position) -> Set.of();
        ResolvedServices resolvedServices = ResolvedServices.builder().tagProvider(TAG_PROVIDER)
                .featureExtractor(featureExtractor).taggerLoader(loaderReturning(UNUSED_TAGGER)).resolve();

        // ACT //
        CrfTagger<?, ?> loaded = resolvedServices.loadTagger(Path.of("model.bin"));

        // ASSERT //
        assertSame(UNUSED_TAGGER, loaded);
    }

    @Test
    void loadTagger__modelWithoutFeatureExtractorLoads() {
        // ARRANGE //
        ResolvedServices resolvedServices = ResolvedServices.builder().tagProvider(TAG_PROVIDER)
                .taggerLoader(loaderReturning(UNUSED_TAGGER)).resolve();

        // ACT //
        CrfTagger<?, ?> loaded = resolvedServices.loadTagger(Path.of("model.bin"));

        // ASSERT //
        assertSame(UNUSED_TAGGER, loaded);
        assertNull(resolvedServices.featureExtractor(), "no feature extractor should be resolved");
    }

    @Test
    void loadTagger__modelWithoutLoaderFailsFast() {
        // ARRANGE //
        ResolvedServices resolvedServices = ResolvedServices.builder().tagProvider(TAG_PROVIDER).resolve();

        // ACT //
        CrfStartupException exception = assertThrows(
                CrfStartupException.class,
                () -> resolvedServices.loadTagger(Path.of("model.bin"))
        );

        // ASSERT //
        assertTrue(
                exception.getMessage().contains("TaggerLoader"),
                "message should name the missing slot; was: " + exception.getMessage()
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
        AmbiguousServiceException exception = new AmbiguousServiceException("TagProvider", List.of(1, "second"));

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
    void resolve__explicitTokenizerOverridesDefault() {
        // ARRANGE //
        Tokenizer tokenizer = new WhitespaceTokenizer();
        ResolvedServices resolvedServices = ResolvedServices.builder().tagProvider(TAG_PROVIDER).tokenizer(tokenizer)
                .resolve();

        // ACT & ASSERT //
        assertSame(tokenizer, resolvedServices.tokenizer());
    }

    @Test
    void resolve__missingTagProviderFailsFast() {
        // ACT //
        CrfStartupException exception = assertThrows(
                CrfStartupException.class,
                () -> ResolvedServices.builder().resolve()
        );

        // ASSERT //
        assertTrue(
                exception.getMessage().contains("TagProvider"),
                "message should guide the user to register a TagProvider; was: " + exception.getMessage()
        );
    }

    @Test
    void resolve__usesDefaultsWhenNothingRegistered() {
        // ACT //
        ResolvedServices resolvedServices = ResolvedServices.builder().tagProvider(TAG_PROVIDER).resolve();

        // ASSERT //
        assertInstanceOf(WhitespaceTokenizer.class, resolvedServices.tokenizer());
        assertNull(resolvedServices.featureExtractor());
        assertNull(resolvedServices.taggerLoader());
        assertSame(TAG_PROVIDER, resolvedServices.tagProvider());
    }
}
