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
package org.coordinatekit.crf.core.feature.configuration;

import org.coordinatekit.crf.core.feature.FeatureExtractor;

/**
 * A {@link FeatureExtractorFactory} that builds a feature extractor from its parameters alone,
 * taking no children.
 *
 * <p>
 * A leaf factory has no arity concept; the assembler rejects a node that gives one children.
 *
 * @see NestingFeatureExtractorFactory
 */
public interface LeafFeatureExtractorFactory extends FeatureExtractorFactory {
    /**
     * Creates a feature extractor from the given validated parameters.
     *
     * @param parameters the validated parameters, already coerced to their declared kinds
     * @return the assembled feature extractor
     */
    FeatureExtractor create(FeatureExtractorParameters parameters);
}
