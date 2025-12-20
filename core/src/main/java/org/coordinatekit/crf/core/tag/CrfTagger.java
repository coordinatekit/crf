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
package org.coordinatekit.crf.core.tag;

import org.coordinatekit.crf.core.Sequence;
import org.coordinatekit.crf.core.preprocessing.InvalidInputException;
import org.jspecify.annotations.NullMarked;

/**
 * A conditional random field (CRF) tagger that assigns tags to tokens in an input string.
 *
 * <p>
 * This interface defines the contract for CRF-based sequence labeling. Implementations process raw
 * text input by tokenizing it, extracting features, and applying a trained CRF model to produce a
 * sequence of tagged tokens with their associated scores.
 *
 * @param <F> the type of features extracted from tokens
 * @param <T> the type of tags (labels) assigned to tokens
 * @see TaggedPositionedToken
 * @see TaggedSequence
 */
@NullMarked
public interface CrfTagger<F, T extends Comparable<T>> {
    /**
     * Tags the tokens in the input string using the CRF model.
     *
     * @param input the raw text to tag
     * @return a sequence of tagged tokens with position and score information
     * @throws InvalidInputException if the input string is empty or blank
     * @throws NullPointerException if the input string is null
     */
    Sequence<TaggedPositionedToken<F, T>> tag(String input);
}
