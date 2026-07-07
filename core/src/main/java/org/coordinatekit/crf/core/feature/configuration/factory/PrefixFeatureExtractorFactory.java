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
package org.coordinatekit.crf.core.feature.configuration.factory;

import static org.coordinatekit.crf.core.feature.Feature.createFeatureWithValue;

import org.coordinatekit.crf.core.feature.configuration.FeatureExtractorParameters;
import org.coordinatekit.crf.core.feature.configuration.LeafFeatureExtractorFactory;
import org.coordinatekit.crf.core.feature.configuration.ParameterDescriptor;
import org.coordinatekit.crf.core.feature.configuration.ParameterKind;
import org.coordinatekit.crf.core.feature.FeatureExtractor;
import org.coordinatekit.crf.core.feature.SubstringFeatureExtractor;

import java.util.Set;

/**
 * The {@code prefix} factory: emits {@code NAME=<prefix>} for the leading characters of each token.
 *
 * <p>
 * It reads {@code name} (string, default {@code PREFIX}), {@code length} (integer, required), and
 * {@code includeIfLessThanLength} (boolean, default {@code true}), backing them with
 * {@link SubstringFeatureExtractor} taking from the start of the token.
 */
public final class PrefixFeatureExtractorFactory implements LeafFeatureExtractorFactory {
    /** Creates the factory, for {@link java.util.ServiceLoader}. */
    public PrefixFeatureExtractorFactory() {}

    @Override
    public FeatureExtractor create(FeatureExtractorParameters parameters) {
        String name = parameters.getString("name");
        return SubstringFeatureExtractor.builder(substring -> createFeatureWithValue(name, substring)).ending(false)
                .includeIfLessThanLength(parameters.getBoolean("includeIfLessThanLength"))
                .length(parameters.getInteger("length")).build();
    }

    @Override
    public Set<ParameterDescriptor> parameters() {
        return Set.of(
                ParameterDescriptor.builder("includeIfLessThanLength", ParameterKind.BOOLEAN).defaultValue("true")
                        .description("whether to emit a feature for tokens shorter than the length").build(),
                ParameterDescriptor.builder("length", ParameterKind.INTEGER).required(true).minimumValue(1)
                        .description("number of leading characters to take").build(),
                ParameterDescriptor.builder("name", ParameterKind.STRING).defaultValue("PREFIX")
                        .description("feature name to emit the prefix under").build()
        );
    }

    @Override
    public String type() {
        return "prefix";
    }
}
