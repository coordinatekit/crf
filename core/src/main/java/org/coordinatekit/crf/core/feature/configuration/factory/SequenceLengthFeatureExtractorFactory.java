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

import org.coordinatekit.crf.core.feature.configuration.AssemblyContext;
import org.coordinatekit.crf.core.feature.configuration.FeatureExtractorParameters;
import org.coordinatekit.crf.core.feature.configuration.LeafFeatureExtractorFactory;
import org.coordinatekit.crf.core.feature.configuration.ParameterDescriptor;
import org.coordinatekit.crf.core.feature.configuration.ParameterKind;
import org.coordinatekit.crf.core.feature.FeatureExtractor;
import org.coordinatekit.crf.core.feature.LengthFeatureExtractor;

import java.util.Set;

/**
 * The {@code sequenceLength} factory: emits features for each length the sequence has or lacks, up
 * to a limit.
 *
 * <p>
 * It reads {@code limit} (integer, required, at least 1) as the upper bound, and the optional
 * feature names {@code hasName} — emitted as {@code hasName=<length>} for each length from one up
 * to the sequence size — and {@code lacksName} — emitted as {@code lacksName=<length>} for each
 * length beyond the sequence size up to the limit; at least one of the two must be set, or the
 * extractor would silently emit nothing. It is backed by {@link LengthFeatureExtractor}. The
 * per-token character length is a separate factory, {@code length}.
 */
public final class SequenceLengthFeatureExtractorFactory implements LeafFeatureExtractorFactory {
    /** Creates the factory, for {@link java.util.ServiceLoader}. */
    public SequenceLengthFeatureExtractorFactory() {}

    @Override
    public FeatureExtractor create(FeatureExtractorParameters parameters) {
        LengthFeatureExtractor.Builder builder = LengthFeatureExtractor.builder(parameters.getInteger("limit"));
        parameters.findString("hasName").ifPresent(
                hasName -> builder.hasLengthFeatureMapper(length -> createFeatureWithValue(hasName, "" + length))
        );
        parameters.findString("lacksName").ifPresent(
                lacksName -> builder.lacksLengthFeatureMapper(length -> createFeatureWithValue(lacksName, "" + length))
        );
        return builder.build();
    }

    @Override
    public Set<ParameterDescriptor> parameters() {
        return Set.of(
                ParameterDescriptor.builder("hasName", ParameterKind.STRING)
                        .description("feature name emitted for each length the sequence has").build(),
                ParameterDescriptor.builder("lacksName", ParameterKind.STRING)
                        .description("feature name emitted for each length the sequence lacks").build(),
                ParameterDescriptor.builder("limit", ParameterKind.INTEGER).required(true).minimumValue(1)
                        .description("the largest length to emit a feature for").build()
        );
    }

    @Override
    public String type() {
        return "sequenceLength";
    }

    @Override
    public void validate(FeatureExtractorParameters parameters, AssemblyContext context) {
        if (parameters.findString("hasName").isEmpty() && parameters.findString("lacksName").isEmpty()) {
            throw new IllegalArgumentException("at least one of 'hasName' or 'lacksName' must be set");
        }
    }
}
