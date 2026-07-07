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

import org.coordinatekit.crf.core.UncheckedCrfException;
import org.coordinatekit.crf.core.feature.configuration.AssemblyContext;
import org.coordinatekit.crf.core.feature.configuration.FeatureExtractorParameters;
import org.coordinatekit.crf.core.feature.configuration.LeafFeatureExtractorFactory;
import org.coordinatekit.crf.core.feature.configuration.ParameterDescriptor;
import org.coordinatekit.crf.core.feature.configuration.ParameterKind;
import org.coordinatekit.crf.core.feature.Feature;
import org.coordinatekit.crf.core.feature.FeatureExtractor;
import org.coordinatekit.crf.core.feature.XPathFeatureExtractor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

/**
 * The {@code lookup} factory: tests each token for membership in a dictionary loaded from an XML
 * file.
 *
 * <p>
 * It reads {@code dictionary} (path, required) and {@code xpath} (string, required) to load the
 * value set, {@code caseSensitive} (boolean, default {@code true}), and the feature names:
 * {@code name} (required) with an optional {@code value} for a present token — emitting
 * {@code NAME=value}, or a bare {@code NAME} when {@code value} is unset — and an optional
 * {@code absentName} emitted for a token that is not in the set. It is backed by
 * {@link XPathFeatureExtractor}, which reads the dictionary eagerly with DTDs and external entities
 * disabled.
 */
public final class LookupFeatureExtractorFactory implements LeafFeatureExtractorFactory {
    /** Creates the factory, for {@link java.util.ServiceLoader}. */
    public LookupFeatureExtractorFactory() {}

    private static FeatureExtractor buildExtractor(FeatureExtractorParameters parameters) {
        String name = parameters.getString("name");
        Feature presentFeature = parameters.findString("value").map(value -> createFeatureWithValue(name, value))
                .orElseGet(() -> createFeature(name));
        Path dictionary = parameters.getPath("dictionary");
        try (InputStream dictionaryStream = Files.newInputStream(dictionary)) {
            XPathFeatureExtractor.Builder builder = XPathFeatureExtractor
                    .builder(dictionaryStream, parameters.getString("xpath"))
                    .caseSensitive(parameters.getBoolean("caseSensitive")).presentFeature(presentFeature);
            parameters.findString("absentName")
                    .ifPresent(absentName -> builder.notPresentFeature(createFeature(absentName)));
            return builder.build();
        } catch (IOException exception) {
            throw new UncheckedIOException("could not read lookup dictionary: " + dictionary, exception);
        }
    }

    @Override
    public FeatureExtractor create(FeatureExtractorParameters parameters) {
        return buildExtractor(parameters);
    }

    @Override
    public Set<ParameterDescriptor> parameters() {
        return Set.of(
                ParameterDescriptor.builder("absentName", ParameterKind.STRING)
                        .description("feature name emitted when the token is absent from the dictionary").build(),
                ParameterDescriptor.builder("caseSensitive", ParameterKind.BOOLEAN).defaultValue("true")
                        .description("whether membership testing is case-sensitive").build(),
                ParameterDescriptor.builder("dictionary", ParameterKind.PATH).required(true)
                        .description("path to the XML file holding the value set").build(),
                ParameterDescriptor.builder("name", ParameterKind.STRING).required(true)
                        .description("feature name emitted when the token is present").build(),
                ParameterDescriptor.builder("value", ParameterKind.STRING)
                        .description("feature value emitted with the name when the token is present").build(),
                ParameterDescriptor.builder("xpath", ParameterKind.STRING).required(true)
                        .description("XPath selecting the value elements from the dictionary").build()
        );
    }

    @Override
    public String type() {
        return "lookup";
    }

    @Override
    public void validate(FeatureExtractorParameters parameters, AssemblyContext context) {
        String xpath = parameters.getString("xpath");
        try {
            XPathFactory.newInstance().newXPath().compile(xpath);
        } catch (XPathExpressionException exception) {
            throw new IllegalArgumentException(
                    "parameter 'xpath' is not a valid XPath expression: '" + xpath + "'",
                    exception
            );
        }
        try {
            buildExtractor(parameters);
        } catch (UncheckedCrfException exception) {
            throw new IllegalArgumentException(
                    "parameter 'dictionary' is not well-formed XML: " + parameters.getPath("dictionary"),
                    exception
            );
        }
    }
}
