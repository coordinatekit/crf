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

import org.coordinatekit.crf.core.feature.configuration.FeatureExtractorParameters;
import org.coordinatekit.crf.core.feature.configuration.NestingFeatureExtractorFactory;
import org.coordinatekit.crf.core.feature.configuration.ParameterDescriptor;
import org.coordinatekit.crf.core.feature.configuration.ParameterKind;
import org.coordinatekit.crf.core.feature.FeatureExtractor;
import org.coordinatekit.crf.core.feature.WindowFeatureExtractor;

import java.util.List;
import java.util.Set;

/**
 * The {@code window} factory: stamps its single child's features with their window offset.
 *
 * <p>
 * It takes exactly one child and reads {@code before} (integer, default {@code 0}), {@code after}
 * (integer, default {@code 0}), and {@code includeCurrentToken} (boolean, default {@code true}),
 * backing them with {@link WindowFeatureExtractor}. At least one of a non-zero {@code before}, a
 * non-zero {@code after}, or {@code includeCurrentToken} must be set, or the extractor would
 * silently emit nothing.
 */
public final class WindowFeatureExtractorFactory implements NestingFeatureExtractorFactory {
    /** Creates the factory, for {@link java.util.ServiceLoader}. */
    public WindowFeatureExtractorFactory() {}

    @Override
    public FeatureExtractor create(FeatureExtractorParameters parameters, List<FeatureExtractor> children) {
        return WindowFeatureExtractor.builder(children.getFirst()).windowBefore(parameters.getInteger("before"))
                .windowAfter(parameters.getInteger("after"))
                .includeCurrentToken(parameters.getBoolean("includeCurrentToken")).build();
    }

    @Override
    public int maximumChildren() {
        return 1;
    }

    @Override
    public Set<ParameterDescriptor> parameters() {
        return Set.of(
                ParameterDescriptor.builder("after", ParameterKind.INTEGER).defaultValue("0").minimumValue(0)
                        .description("number of following tokens to stamp").build(),
                ParameterDescriptor.builder("before", ParameterKind.INTEGER).defaultValue("0").minimumValue(0)
                        .description("number of preceding tokens to stamp").build(),
                ParameterDescriptor.builder("includeCurrentToken", ParameterKind.BOOLEAN).defaultValue("true")
                        .description("whether to include the current token at offset 0").build()
        );
    }

    @Override
    public String type() {
        return "window";
    }

    @Override
    public void validate(FeatureExtractorParameters parameters) {
        if (parameters.getInteger("before") == 0 && parameters.getInteger("after") == 0
                && !parameters.getBoolean("includeCurrentToken")) {
            throw new IllegalArgumentException(
                    "at least one of a non-zero 'before', a non-zero 'after', or 'includeCurrentToken'" + " must be set"
            );
        }
    }
}
