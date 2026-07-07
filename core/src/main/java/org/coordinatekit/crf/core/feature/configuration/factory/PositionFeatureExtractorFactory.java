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

import static org.coordinatekit.crf.core.feature.Feature.createFeature;
import static org.coordinatekit.crf.core.feature.Feature.createFeatureWithValue;

import org.coordinatekit.crf.core.feature.configuration.AssemblyContext;
import org.coordinatekit.crf.core.feature.configuration.FeatureExtractorParameters;
import org.coordinatekit.crf.core.feature.configuration.LeafFeatureExtractorFactory;
import org.coordinatekit.crf.core.feature.configuration.ParameterDescriptor;
import org.coordinatekit.crf.core.feature.configuration.ParameterKind;
import org.coordinatekit.crf.core.feature.FeatureExtractor;
import org.coordinatekit.crf.core.feature.PositionFeatureExtractor;

import java.util.Set;

/**
 * The {@code position} factory: emits features derived from a token's position within its sequence.
 *
 * <p>
 * Every parameter is optional with no default, so a configuration turns on only the features it
 * wants: {@code firstName} and {@code lastName} emit a valueless feature at the first and last
 * token, while {@code fromStartName} and {@code fromEndName} emit {@code NAME=<position>} for the
 * zero-based distance from the start and end; at least one of the four must be set, or the
 * extractor would silently emit nothing. It is backed by {@link PositionFeatureExtractor}.
 */
public final class PositionFeatureExtractorFactory implements LeafFeatureExtractorFactory {
    /** Creates the factory, for {@link java.util.ServiceLoader}. */
    public PositionFeatureExtractorFactory() {}

    @Override
    public FeatureExtractor create(FeatureExtractorParameters parameters) {
        PositionFeatureExtractor.Builder builder = PositionFeatureExtractor.builder();
        parameters.findString("firstName").ifPresent(firstName -> builder.firstFeature(createFeature(firstName)));
        parameters.findString("lastName").ifPresent(lastName -> builder.lastFeature(createFeature(lastName)));
        parameters.findString("fromStartName").ifPresent(
                fromStartName -> builder.positionFromStartFeatureMapper(
                        position -> createFeatureWithValue(fromStartName, "" + position)
                )
        );
        parameters.findString("fromEndName").ifPresent(
                fromEndName -> builder
                        .positionFromEndFeatureMapper(position -> createFeatureWithValue(fromEndName, "" + position))
        );
        return builder.build();
    }

    @Override
    public Set<ParameterDescriptor> parameters() {
        return Set.of(
                ParameterDescriptor.builder("firstName", ParameterKind.STRING)
                        .description("valueless feature name emitted at the first token").build(),
                ParameterDescriptor.builder("fromEndName", ParameterKind.STRING)
                        .description("feature name emitted with the zero-based distance from the end").build(),
                ParameterDescriptor.builder("fromStartName", ParameterKind.STRING)
                        .description("feature name emitted with the zero-based distance from the start").build(),
                ParameterDescriptor.builder("lastName", ParameterKind.STRING)
                        .description("valueless feature name emitted at the last token").build()
        );
    }

    @Override
    public String type() {
        return "position";
    }

    @Override
    public void validate(FeatureExtractorParameters parameters, AssemblyContext context) {
        if (parameters.findString("firstName").isEmpty() && parameters.findString("lastName").isEmpty()
                && parameters.findString("fromStartName").isEmpty() && parameters.findString("fromEndName").isEmpty()) {
            throw new IllegalArgumentException(
                    "at least one of 'firstName', 'lastName', 'fromStartName', or 'fromEndName' must be set"
            );
        }
    }
}
