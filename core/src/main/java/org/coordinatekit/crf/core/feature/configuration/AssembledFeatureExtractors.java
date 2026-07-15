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

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * The pair of extractors {@link FeatureExtractorAssembler} produces from one configuration tree:
 * the full extractor assembled from the whole tree, and the key extractor assembled from the
 * subtree rooted at the node marked {@link FeatureExtractorNode#key()}.
 *
 * <p>
 * When no node is marked, {@link #keyFeatureExtractor()} is empty; when a node is marked, it holds
 * the shared instance the full tree wraps, not a separate copy.
 */
public final class AssembledFeatureExtractors {
    private final FeatureExtractor fullFeatureExtractor;
    private final @Nullable FeatureExtractor keyFeatureExtractor;

    private AssembledFeatureExtractors(
            FeatureExtractor fullFeatureExtractor,
            @Nullable FeatureExtractor keyFeatureExtractor
    ) {
        this.fullFeatureExtractor = fullFeatureExtractor;
        this.keyFeatureExtractor = keyFeatureExtractor;
    }

    /**
     * Returns the full feature extractor, assembled from the whole configuration tree.
     *
     * @return the full feature extractor
     */
    public FeatureExtractor fullFeatureExtractor() {
        return fullFeatureExtractor;
    }

    /**
     * Returns the key feature extractor, assembled from the subtree rooted at the marked node, or empty
     * when no node was marked.
     *
     * @return the key feature extractor, or empty when no node was marked key
     */
    public Optional<FeatureExtractor> keyFeatureExtractor() {
        return Optional.ofNullable(keyFeatureExtractor);
    }

    /**
     * Creates a pair with no key extractor, for a tree in which no node was marked key.
     *
     * @param fullFeatureExtractor the full feature extractor
     * @return the assembled pair, with an empty key extractor
     */
    public static AssembledFeatureExtractors of(FeatureExtractor fullFeatureExtractor) {
        return new AssembledFeatureExtractors(
                Objects.requireNonNull(fullFeatureExtractor, "fullFeatureExtractor must not be null"),
                null
        );
    }

    /**
     * Creates a pair from an already-assembled full and key extractor.
     *
     * @param fullFeatureExtractor the full feature extractor
     * @param keyFeatureExtractor the key feature extractor, or null when no node was marked key
     * @return the assembled pair, with an empty key extractor when {@code keyFeatureExtractor} is null
     */
    public static AssembledFeatureExtractors of(
            FeatureExtractor fullFeatureExtractor,
            @Nullable FeatureExtractor keyFeatureExtractor
    ) {
        return new AssembledFeatureExtractors(
                Objects.requireNonNull(fullFeatureExtractor, "fullFeatureExtractor must not be null"),
                keyFeatureExtractor
        );
    }
}
