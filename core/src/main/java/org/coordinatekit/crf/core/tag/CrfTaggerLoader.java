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
package org.coordinatekit.crf.core.tag;

import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.preprocessing.FeatureExtractor;
import org.coordinatekit.crf.core.preprocessing.Tokenizer;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Loads a {@link CrfTagger} from a serialized model on disk, given the beans the tagger needs at
 * construction time.
 *
 * <p>
 * This is a service-provider interface: an implementation that can deserialize a particular model
 * format (for example MALLET) is discovered through {@link java.util.ServiceLoader}, so a
 * downstream launcher can load a model without a compile-time dependency on the implementing
 * module. It is a dedicated loader rather than a directly discovered {@link CrfTagger} because a
 * tagger cannot be constructed without the model path plus the feature extractor, tag provider, and
 * tokenizer it must agree with — runtime arguments a no-argument service factory cannot supply.
 *
 * <p>
 * The method is generic in the feature type {@code <F>} and tag type {@code <T>}: the supplied
 * {@code featureExtractor} and {@code tagProvider} bind those parameters, so the returned tagger's
 * types must agree with them. Because {@code <F>} and {@code <T>} are the method's own type
 * variables, the only {@link FeatureExtractor} and {@link TagProvider} an implementation can build
 * the tagger over are the supplied ones — substituting its own would not compile. Coherence with
 * the rest of the assembly is therefore enforced by the signature rather than left to convention. A
 * caller holding the beans as wildcards invokes this through wildcard capture, binding {@code <F>}
 * and {@code <T>} afresh on each call.
 *
 * @see CrfTagger
 */
@NullMarked
public interface CrfTaggerLoader {
    /**
     * Loads a tagger from the serialized model at {@code modelPath}, wiring in the feature extractor,
     * tag provider, and tokenizer it was trained against.
     *
     * @param <F> the feature type, bound by {@code featureExtractor}
     * @param <T> the tag type, bound by {@code tagProvider}
     * @param modelPath the path to the serialized model file
     * @param featureExtractor the feature extractor matching the one the model was trained with
     * @param tagProvider the provider that decodes the model's tag names into typed tag values
     * @param tokenizer the tokenizer that splits input text into tokens
     * @return a tagger backed by the loaded model
     * @throws IOException if the model file cannot be read or deserialized
     */
    <F, T extends Comparable<T>> CrfTagger<F, T> load(
            Path modelPath,
            FeatureExtractor<F> featureExtractor,
            TagProvider<T> tagProvider,
            Tokenizer tokenizer
    ) throws IOException;
}
