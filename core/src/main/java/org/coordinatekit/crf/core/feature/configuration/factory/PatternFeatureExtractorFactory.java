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
import org.coordinatekit.crf.core.feature.Feature;
import org.coordinatekit.crf.core.feature.FeatureExtractor;
import org.coordinatekit.crf.core.feature.PatternMatchingFeatureExtractor;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The {@code pattern} factory: tests each token against a regular expression.
 *
 * <p>
 * It reads {@code regex} (string, required) and {@code caseSensitive} (boolean, default
 * {@code true}) for the match, and the feature names: {@code name} (required) with an optional
 * {@code value} for a matching token — emitting {@code NAME=value}, or a bare {@code NAME} when
 * {@code value} is unset — and an optional {@code notMatchedName} emitted for a non-matching token.
 * It is backed by {@link PatternMatchingFeatureExtractor}.
 */
public final class PatternFeatureExtractorFactory implements LeafFeatureExtractorFactory {
    /** Creates the factory, for {@link java.util.ServiceLoader}. */
    public PatternFeatureExtractorFactory() {}

    @Override
    public FeatureExtractor create(FeatureExtractorParameters parameters) {
        String name = parameters.getString("name");
        Feature matchedFeature = parameters.findString("value").map(value -> createFeatureWithValue(name, value))
                .orElseGet(() -> createFeature(name));
        PatternMatchingFeatureExtractor.Builder builder = PatternMatchingFeatureExtractor
                .builder(parameters.getString("regex"), parameters.getBoolean("caseSensitive"))
                .matchedFeature(matchedFeature);
        parameters.findString("notMatchedName")
                .ifPresent(notMatchedName -> builder.notMatchedFeature(createFeature(notMatchedName)));
        return builder.build();
    }

    @Override
    public Set<ParameterDescriptor> parameters() {
        return Set.of(
                ParameterDescriptor.builder("caseSensitive", ParameterKind.BOOLEAN).defaultValue("true")
                        .description("whether the regular expression matches case-sensitively").build(),
                ParameterDescriptor.builder("name", ParameterKind.STRING).required(true)
                        .description("feature name emitted when the token matches").build(),
                ParameterDescriptor.builder("notMatchedName", ParameterKind.STRING)
                        .description("feature name emitted when the token does not match").build(),
                ParameterDescriptor.builder("regex", ParameterKind.STRING).required(true)
                        .description("the regular expression each token is tested against").build(),
                ParameterDescriptor.builder("value", ParameterKind.STRING)
                        .description("feature value emitted with the name when the token matches").build()
        );
    }

    @Override
    public String type() {
        return "pattern";
    }

    @Override
    public void validate(FeatureExtractorParameters parameters, AssemblyContext context) {
        String regex = parameters.getString("regex");
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException(
                    "parameter 'regex' is not a valid regular expression: '" + regex + "'",
                    exception
            );
        }
    }
}
