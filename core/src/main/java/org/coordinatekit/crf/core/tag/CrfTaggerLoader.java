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
import org.coordinatekit.crf.core.feature.FeatureExtractor;
import org.coordinatekit.crf.core.feature.FeatureFormat;
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
 * tagger cannot be constructed without the model path plus the feature extractor, feature format,
 * tag provider, and tokenizer it must agree with — runtime arguments a no-argument service factory
 * cannot supply.
 *
 * <p>
 * The method is generic in the tag type {@code <T>}: the supplied {@code tagProvider} binds that
 * parameter, so the returned tagger's tag type must agree with it. Because {@code <T>} is the
 * method's own type variable, the only {@link TagProvider} an implementation can build the tagger
 * over is the supplied one — substituting its own would not compile. The {@code featureExtractor}
 * and {@code featureFormat} must likewise agree with what the model was trained against: the
 * extractor produces the same structured features and the format renders them to the same alphabet
 * entries, or the model's suggestions are meaningless. A caller holding the tag provider as a
 * wildcard invokes this through wildcard capture, binding {@code <T>} afresh on each call.
 *
 * @see CrfTagger
 */
@NullMarked
public interface CrfTaggerLoader {
    /**
     * Loads a tagger from the serialized model at {@code modelPath}, wiring in the feature extractor,
     * feature format, tag provider, and tokenizer it was trained against.
     *
     * @param <T> the tag type, bound by {@code tagProvider}
     * @param modelPath the path to the serialized model file
     * @param featureExtractor the feature extractor matching the one the model was trained with
     * @param featureFormat the format rendering features to the alphabet entries the model was trained
     *        with
     * @param tagProvider the provider that decodes the model's tag names into typed tag values
     * @param tokenizer the tokenizer that splits input text into tokens
     * @return a tagger backed by the loaded model
     * @throws IOException if the model file cannot be read or deserialized
     */
    <T extends Comparable<T>> CrfTagger<T> load(
            Path modelPath,
            FeatureExtractor featureExtractor,
            FeatureFormat featureFormat,
            TagProvider<T> tagProvider,
            Tokenizer tokenizer
    ) throws IOException;

    /**
     * Returns this loader's short, stable identifier, unique among the loaders on the classpath (for
     * example {@code "mallet"}).
     *
     * <p>
     * The name is the disambiguator a launcher exposes when more than one loader is registered: a user
     * picks a loader by its name rather than by its implementation class. Implementations return a
     * lowercase ASCII identifier that does not change between releases, since users and scripts come to
     * depend on it.
     *
     * @return the loader's stable lowercase identifier
     */
    String name();
}
