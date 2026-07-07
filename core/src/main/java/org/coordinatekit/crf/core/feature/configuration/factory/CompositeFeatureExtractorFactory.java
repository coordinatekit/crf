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
import org.coordinatekit.crf.core.feature.CompositeFeatureExtractor;
import org.coordinatekit.crf.core.feature.FeatureExtractor;

import java.util.List;

/**
 * The {@code composite} factory: unions the features of its children.
 *
 * <p>
 * It takes one or more children and no parameters, backing them with
 * {@link CompositeFeatureExtractor#of(FeatureExtractor...)}.
 */
public final class CompositeFeatureExtractorFactory implements NestingFeatureExtractorFactory {
    /** Creates the factory, for {@link java.util.ServiceLoader}. */
    public CompositeFeatureExtractorFactory() {}

    @Override
    public FeatureExtractor create(FeatureExtractorParameters parameters, List<FeatureExtractor> children) {
        return CompositeFeatureExtractor.of(children.toArray(new FeatureExtractor[0]));
    }

    @Override
    public int maximumChildren() {
        return Integer.MAX_VALUE;
    }

    @Override
    public String type() {
        return "composite";
    }
}
