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

import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.preprocessing.FeatureExtractor;
import org.coordinatekit.crf.core.preprocessing.Tokenizer;
import org.coordinatekit.crf.core.tag.CrfTagger;
import org.coordinatekit.crf.core.tag.CrfTaggerLoader;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The MALLET {@link CrfTaggerLoader}: deserializes a MALLET CRF model into a
 * {@link MalletCrfTagger}.
 *
 * <p>
 * Registered as a {@link java.util.ServiceLoader} provider so a launcher discovers it whenever the
 * {@code mallet} module is on the classpath, without a compile-time dependency on this module.
 *
 * @see MalletCrfTagger
 * @see CrfTaggerLoader
 */
@NullMarked
public final class MalletCrfTaggerLoader implements CrfTaggerLoader {
    /**
     * Creates a loader. A public no-argument constructor is required for
     * {@link java.util.ServiceLoader}.
     */
    public MalletCrfTaggerLoader() {}

    @Override
    public <F, T extends Comparable<T>> CrfTagger<F, T> load(
            Path modelPath,
            FeatureExtractor<F> featureExtractor,
            TagProvider<T> tagProvider,
            Tokenizer tokenizer
    ) throws IOException {
        return new MalletCrfTagger<>(featureExtractor, modelPath, tagProvider, tokenizer);
    }

    @Override
    public String name() {
        return "mallet";
    }
}
