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
package org.coordinatekit.crf.annotator;

import org.coordinatekit.crf.core.Sequence;
import org.coordinatekit.crf.core.TagProvider;

import java.util.List;

/**
 * A view-model describing a single sequence to present to a user for tagging.
 *
 * <p>
 * The sequence carries a non-empty, immutable list of {@link AnnotatorToken} entries — one per
 * token, each bundling the data the user-interface needs to render and edit that token.
 *
 * <p>
 * Instances are constructed via one of the two factory methods on {@link AnnotatorModels}, chosen
 * by whether a tagger is available: {@link AnnotatorModels#annotatorSequence(int, int, Sequence)
 * annotatorSequence(..., taggedSequence)} for the with-tagger path, or
 * {@link AnnotatorModels#annotatorSequence(int, int, List, TagProvider) annotatorSequence(...,
 * tokens, tagProvider)} for the no-tagger path.
 *
 * @param <F> the feature type
 * @param <T> the tag type
 * @see AnnotatorModels#annotatorSequence(int, int, Sequence)
 * @see AnnotatorModels#annotatorSequence(int, int, List, TagProvider)
 */
public interface AnnotatorSequence<F, T extends Comparable<T>> {
    /**
     * Returns whether key display features are available for this sequence — that is, whether a display
     * feature extractor was configured. When {@code false}, every token's
     * {@link AnnotatorToken#features() features} set is empty and the user-interface offers no
     * key-feature view.
     *
     * @return {@code true} when key display features are available
     */
    boolean featuresAvailable();

    /**
     * Returns the 1-based position of this sequence within the overall annotation batch.
     *
     * @return the sequence number
     */
    int sequenceNumber();

    /**
     * Returns the per-token entries for the sequence.
     *
     * @return the tokens
     */
    List<AnnotatorToken<F, T>> tokens();

    /**
     * Returns the total number of sequences in the overall annotation batch.
     *
     * @return the total number of sequences
     */
    int totalSequences();

    /**
     * Returns whether verbose display features are available for this sequence. Independent of
     * {@link #featuresAvailable() featuresAvailable}. When {@code false}, every token's
     * {@link AnnotatorToken#verboseFeatures() verboseFeatures} set is empty and the user-interface
     * offers no all-features view.
     *
     * @return {@code true} when verbose display features are available
     */
    boolean verboseFeaturesAvailable();
}
