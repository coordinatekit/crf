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
package org.coordinatekit.crf.annotator.ui;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * A view-model entry for a single token within an {@link AnnotatorSequence}.
 *
 * <p>
 * Carries the per-token data the user-interface needs: the raw token string, its extracted feature
 * set, the starting tag and confidence to show, and the full set of alternative tags with their
 * scores. The {@link #initialConfidence() initialConfidence} and the values of
 * {@link #alternativeTagScores() alternativeTagScores} may be {@code null} when no model produced a
 * prediction for that token (or tag).
 *
 * @param <F> the feature type
 * @param <T> the tag type
 */
public interface AnnotatorToken<F, T extends Comparable<T>> {
    /**
     * Returns the map from tag to score for the edit screen, in canonical iteration order: tags with a
     * non-null score first (sorted by score descending), then tags with a {@code null} score, with the
     * natural tag order breaking ties.
     *
     * @return the alternative tag scores
     */
    Map<T, @Nullable Double> alternativeTagScores();

    /**
     * Returns the feature set extracted for this token.
     *
     * @return the feature set
     */
    Set<F> features();

    /**
     * Returns the starting confidence value, or {@code null} when no model produced a confidence.
     *
     * @return the starting confidence, or {@code null}
     */
    @Nullable
    Double initialConfidence();

    /**
     * Returns the starting tag shown to the user.
     *
     * @return the starting tag
     */
    T initialTag();

    /**
     * Returns the raw token string.
     *
     * @return the token
     */
    String token();
}
