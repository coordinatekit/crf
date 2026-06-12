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
 * The kind of a segment: either a tagless {@link Segment} within a {@link Tokenization} or a tagged
 * {@link TrainingSegment} within a {@link TrainingSequence}.
 *
 * <p>
 * Both a tokenization and a training sequence are ordered lists of segments, each of which is
 * either a dropped run of excluded characters or a token. It is the shared kind of both.
 *
 * @see Segment
 * @see TrainingSegment
 * @see Tokenization
 * @see TrainingSequence
 */
public enum SegmentKind {
    /** A run of characters a tokenizer dropped; it carries text but no tag. */
    EXCLUDED,

    /** A token; it carries both text and a tag. */
    TOKEN
}
