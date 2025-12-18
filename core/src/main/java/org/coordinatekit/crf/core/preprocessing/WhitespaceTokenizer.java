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

import java.util.Arrays;

/**
 * A tokenizer that splits input on whitespace characters.
 *
 * <p>
 * This implementation splits the input string on one or more consecutive whitespace characters
 * (spaces, tabs, newlines, etc.). Note that leading whitespace will result in an empty string as
 * the first token.
 *
 * @see Tokenizer
 */
public class WhitespaceTokenizer implements Tokenizer {
    /**
     * {@inheritDoc}
     *
     * <p>
     * Splits the input on whitespace using the regex pattern {@code \s+}.
     */
    @Override
    public InputSequence tokenize(String input) {
        return new InputSequence(Arrays.asList(input.split("\\s+")));
    }
}
