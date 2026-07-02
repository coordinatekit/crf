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

import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.feature.FeatureExtractor;
import org.coordinatekit.crf.core.feature.FeatureFormat;
import org.coordinatekit.crf.core.preprocessing.Tokenizer;
import org.coordinatekit.crf.core.tag.CrfTagger;
import org.coordinatekit.crf.core.tag.CrfTaggerLoader;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A package-private fake {@link CrfTaggerLoader} for the CLI tests: it either returns a configured
 * tagger (capturing the feature extractor it was handed) or throws a configured failure. Replaces
 * the hand-rolled anonymous loaders the resolution tests used to repeat.
 */
@NullMarked
final class TestCrfTaggerLoader implements CrfTaggerLoader {
    private final @Nullable CrfTagger<?> tagger;
    private final @Nullable Exception failure;
    private @Nullable FeatureExtractor capturedFeatureExtractor;
    private @Nullable FeatureFormat capturedFeatureFormat;

    private TestCrfTaggerLoader(@Nullable CrfTagger<?> tagger, @Nullable Exception failure) {
        this.tagger = tagger;
        this.failure = failure;
    }

    /**
     * A loader whose {@link #load} returns {@code tagger}, capturing the feature extractor it receives.
     */
    static TestCrfTaggerLoader returning(CrfTagger<?> tagger) {
        return new TestCrfTaggerLoader(tagger, null);
    }

    /**
     * A loader whose {@link #load} throws {@code failure} (an {@link IOException} or a runtime
     * exception).
     */
    static TestCrfTaggerLoader throwing(Exception failure) {
        return new TestCrfTaggerLoader(null, failure);
    }

    /** Returns the feature extractor passed to the most recent {@link #load} call, or {@code null}. */
    @Nullable
    FeatureExtractor capturedFeatureExtractor() {
        return capturedFeatureExtractor;
    }

    /** Returns the feature format passed to the most recent {@link #load} call, or {@code null}. */
    @Nullable
    FeatureFormat capturedFeatureFormat() {
        return capturedFeatureFormat;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Comparable<T>> CrfTagger<T> load(
            Path modelPath,
            FeatureExtractor featureExtractor,
            FeatureFormat featureFormat,
            TagProvider<T> tagProvider,
            Tokenizer tokenizer
    ) throws IOException {
        capturedFeatureExtractor = featureExtractor;
        capturedFeatureFormat = featureFormat;
        if (failure instanceof IOException ioException) {
            throw ioException;
        }
        if (failure != null) {
            throw (RuntimeException) failure;
        }
        return (CrfTagger<T>) Objects.requireNonNull(tagger, "a returning loader must have a tagger");
    }

    @Override
    public String name() {
        return "test";
    }
}
