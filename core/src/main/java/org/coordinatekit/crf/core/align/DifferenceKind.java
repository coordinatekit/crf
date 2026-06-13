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
package org.coordinatekit.crf.core.align;

/**
 * The kind of a single {@link TokenDifference} region between a stored token list and a
 * re-tokenized one.
 *
 * @see TokenDifference#kind()
 */
public enum DifferenceKind {
    /** A run of stored tokens the re-tokenization dropped; its re-tokenized span is empty. */
    DELETION,

    /** A run of re-tokenized tokens absent from the stored list; its stored span is empty. */
    INSERTION,

    /**
     * A run of stored tokens replaced by a differing run of re-tokenized tokens; both spans are
     * non-empty.
     */
    REPLACEMENT
}
