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

import java.util.List;

/**
 * A {@link FeatureExtractorFactory} that composes already-assembled child extractors, such as a
 * windowing or union extractor.
 *
 * <p>
 * The assembler builds a node's children depth-first and enforces that the child count is within
 * {@link #minimumChildren()} and {@link #maximumChildren()} before calling
 * {@link #create(FeatureExtractorParameters, List)}, so the {@code children} list is always within
 * the declared bounds.
 *
 * @see LeafFeatureExtractorFactory
 */
public interface NestingFeatureExtractorFactory extends FeatureExtractorFactory {
    /**
     * Creates a feature extractor from the given validated parameters and assembled children.
     *
     * @param parameters the validated parameters, already coerced to their declared kinds
     * @param children the assembled child extractors, within the declared child arity
     * @return the assembled feature extractor
     */
    FeatureExtractor create(FeatureExtractorParameters parameters, List<FeatureExtractor> children);

    /**
     * Returns the maximum number of children this factory accepts.
     *
     * @return the maximum child count
     */
    int maximumChildren();

    /**
     * Returns the minimum number of children this factory requires.
     *
     * <p>
     * Defaults to {@code 1}: a nesting factory composes children, so requiring at least one keeps a
     * childless node — which would behave as a leaf — from selecting it. Override to require more.
     *
     * @return the minimum child count
     */
    default int minimumChildren() {
        return 1;
    }
}
