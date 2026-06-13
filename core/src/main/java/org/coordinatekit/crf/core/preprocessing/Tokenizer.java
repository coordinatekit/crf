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

/**
 * Converts raw text input into a sequence of tokens.
 *
 * <p>
 * Implementations of this interface define how input strings are split into individual tokens for
 * processing by the CRF model. A tokenizer is the authoritative source for tokenization: it reports
 * both the tokens it produced and the excluded character runs it dropped, so the original surface
 * string can be reconstructed exactly via {@link Tokenization#surface()}.
 *
 * @see WhitespaceTokenizer
 * @see Tokenization
 */
public interface Tokenizer {
    /**
     * Tokenizes the input string into tokens and the excluded character runs dropped around them.
     *
     * @param input the raw text to tokenize
     * @return the tokenization, carrying the tokens and excluded runs as ordered segments
     * @throws InvalidInputException if the input string is empty or blank
     * @throws NullPointerException if the input string is null
     */
    Tokenization tokenize(String input);
}
