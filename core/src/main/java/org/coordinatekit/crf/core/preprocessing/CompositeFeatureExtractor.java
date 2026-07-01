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
package org.coordinatekit.crf.core.preprocessing;

import org.coordinatekit.crf.core.PositionedToken;
import org.coordinatekit.crf.core.Sequence;
import org.jspecify.annotations.NullMarked;

import java.util.*;

/**
 * A feature extractor that combines multiple feature extractors into one.
 *
 * <p>
 * This extractor iterates through a collection of feature extractors in order, collecting all
 * features produced by each extractor into a single set.
 *
 * <p>
 * Example usage:
 *
 * <pre>
 * <code>
 * CompositeFeatureExtractor extractor = CompositeFeatureExtractor.of(
 *         lengthExtractor, caseExtractor, prefixExtractor
 * );
 * </code>
 * </pre>
 */
@NullMarked
public class CompositeFeatureExtractor implements FeatureExtractor {
    private final List<FeatureExtractor> extractors;

    /**
     * Creates a new composite feature extractor with the specified extractors.
     *
     * @param extractors the collection of feature extractors to combine
     */
    private CompositeFeatureExtractor(Collection<? extends FeatureExtractor> extractors) {
        this.extractors = List.copyOf(extractors);
    }

    /**
     * Creates a new composite feature extractor from the specified extractors.
     *
     * @param extractors the feature extractors to combine
     * @return a new composite feature extractor
     */
    public static CompositeFeatureExtractor of(FeatureExtractor... extractors) {
        return new CompositeFeatureExtractor(List.of(extractors));
    }

    @Override
    public Set<Feature> extractAt(Sequence<? extends PositionedToken> sequence, int position) {
        Set<Feature> features = new HashSet<>();
        for (FeatureExtractor extractor : extractors) {
            features.addAll(extractor.extractAt(sequence, position));
        }
        return Collections.unmodifiableSet(features);
    }
}
