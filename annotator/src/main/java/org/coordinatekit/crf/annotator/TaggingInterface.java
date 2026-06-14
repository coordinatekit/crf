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

/**
 * Presents a single sequence to a user for tagging and reports the outcome.
 *
 * <p>
 * Implementations are responsible for rendering the sequence, accepting user input, and translating
 * that input into a {@link TaggingResult}. The orchestrator (a higher layer) is responsible for
 * deciding which sequences to present and what to do with each result.
 *
 * @param <F> the feature type carried on the annotator sequence
 * @param <T> the tag type
 * @see org.coordinatekit.crf.annotator.terminal.TerminalTaggingInterface
 */
public interface TaggingInterface<F, T extends Comparable<T>> {
    /**
     * Presents the given sequence to the user and returns the chosen action.
     *
     * @param sequence the sequence to present
     * @return the outcome of the user's interaction
     */
    TaggingResult<T> present(AnnotatorSequence<F, T> sequence);
}
