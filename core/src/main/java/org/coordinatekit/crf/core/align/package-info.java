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
/**
 * Read-only detection of training data that no longer matches a tokenizer.
 *
 * <p>
 * Training data annotated under one tokenizer can drift out of alignment when the tokenizer
 * changes: the new tokenizer may split or merge characters differently, so the tokens stored in the
 * XML no longer match what re-tokenizing the same surface would produce. The components here answer
 * the first operational question of a tokenizer migration — <em>which sequences need review?</em> —
 * without mutating anything.
 *
 * <p>
 * {@link org.coordinatekit.crf.core.align.AlignmentDetector} reconstructs each stored sequence's
 * surface, re-tokenizes it with a caller-supplied
 * {@link org.coordinatekit.crf.core.preprocessing.Tokenizer}, and compares the result against the
 * stored token list. A training sequence is <em>aligned</em> with a tokenizer iff re-tokenizing its
 * reconstructed surface reproduces its exact token list. The outcome is reported as an
 * {@link org.coordinatekit.crf.core.align.AlignmentReport} of per-sequence
 * {@link org.coordinatekit.crf.core.align.SequenceAlignment} results, each carrying an
 * {@link org.coordinatekit.crf.core.align.AlignmentStatus}.
 *
 * @see org.coordinatekit.crf.core.align.AlignmentDetector
 * @see org.coordinatekit.crf.core.align.AlignmentReport
 */
@NullMarked
package org.coordinatekit.crf.core.align;

import org.jspecify.annotations.NullMarked;
