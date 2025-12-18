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

import org.coordinatekit.crf.core.InputSequence;

/**
 * Converts raw text input into a sequence of tokens.
 *
 * <p>
 * Implementations of this interface define how input strings are split into individual tokens for
 * processing by the CRF model.
 *
 * @see WhitespaceTokenizer
 * @see InputSequence
 */
public interface Tokenizer {
    /**
     * Tokenizes the input string into a sequence of tokens.
     *
     * @param input the raw text to tokenize
     * @return a sequence containing the tokens
     */
    InputSequence tokenize(String input);
}
