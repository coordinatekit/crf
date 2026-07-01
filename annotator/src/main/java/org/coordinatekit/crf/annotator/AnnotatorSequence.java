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
import org.jspecify.annotations.Nullable;

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
 * @param <T> the tag type
 * @see AnnotatorModels#annotatorSequence(int, int, Sequence)
 * @see AnnotatorModels#annotatorSequence(int, int, List, TagProvider)
 */
public interface AnnotatorSequence<T extends Comparable<T>> {
    /**
     * Returns which display-feature sources this sequence carries, derived from which feature
     * extractors were configured. {@link FeatureAvailability#keyAvailable() Key} availability backs the
     * key-feature view and {@link FeatureAvailability#verboseAvailable() verbose} availability backs
     * the all-features view; when a source is unavailable, every token's corresponding feature set is
     * empty and the user-interface offers no view for it.
     *
     * @return the feature availability
     */
    FeatureAvailability featureAvailability();

    /**
     * Returns the 1-based position of this sequence within the overall annotation batch.
     *
     * @return the sequence number
     */
    int sequenceNumber();

    /**
     * Returns the conditional probability the model assigns to the given tagging of this sequence, or
     * {@code null} when no model backs this sequence (the no-tagger path).
     *
     * <p>
     * When non-null, it is bound to this sequence's input and expects one tag per {@link #tokens()
     * token}; the terminal interface uses it to show a total likelihood that updates as the user
     * revises tags.
     *
     * <p>
     * This method does not guarantee that a wrong-sized {@code tags} list is rejected; how such a
     * mismatch is handled is left to the backing scorer.
     *
     * @param tags the candidate tag for each token, in token order; must have one entry per
     *        {@link #tokens() token}
     * @return the conditional probability of {@code tags} given the input in {@code [0, 1]}, or
     *         {@code null} when no model backs this sequence
     */
    @Nullable
    Double probabilityOf(List<T> tags);

    /**
     * Returns the per-token entries for the sequence.
     *
     * @return the tokens
     */
    List<AnnotatorToken<T>> tokens();

    /**
     * Returns the total number of sequences in the overall annotation batch.
     *
     * @return the total number of sequences
     */
    int totalSequences();
}
