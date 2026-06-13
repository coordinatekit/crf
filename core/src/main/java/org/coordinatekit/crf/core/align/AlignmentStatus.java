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
 * The outcome of comparing a stored training sequence against a tokenizer's re-tokenization of its
 * surface.
 *
 * <p>
 * {@link #UNTOKENIZABLE} is a first-class error state, kept distinct from {@link #MISALIGNED} so
 * that "the tokenizer rejected the surface" is never conflated with "the tokens disagree".
 *
 * @see SequenceAlignment#status()
 */
public enum AlignmentStatus {
    /** Re-tokenizing the surface reproduced the stored token list exactly. */
    ALIGNED,

    /** Re-tokenizing the surface produced a token list that differs from the stored one. */
    MISALIGNED,

    /** The tokenizer rejected the reconstructed surface, so no comparison was possible. */
    UNTOKENIZABLE
}
