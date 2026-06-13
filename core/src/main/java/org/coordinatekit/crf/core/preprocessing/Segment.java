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
 * One element of a surface decomposition: either a token or a dropped run of excluded characters.
 *
 * <p>
 * A segment preserves a slice of the original text in document order. Concatenating the
 * {@link #text() text} of every segment in order reconstructs the surface string exactly. A
 * {@link SegmentKind#TOKEN TOKEN} segment carries token text; an {@link SegmentKind#EXCLUDED
 * EXCLUDED} segment carries the characters a tokenizer dropped between, before, or after tokens.
 *
 * <p>
 * This is the tag-less supertype shared by the untagged segments of a {@link Tokenization} and the
 * tagged {@link TrainingSegment segments} of a {@link TrainingSequence}.
 *
 * @see SegmentKind
 * @see Segments
 * @see TrainingSegment
 */
public interface Segment {
    /**
     * Returns the kind of this segment.
     *
     * @return {@link SegmentKind#TOKEN} for a token, {@link SegmentKind#EXCLUDED} for a dropped run of
     *         characters
     */
    SegmentKind kind();

    /**
     * Returns the surface text of this segment.
     *
     * @return the token text for a token segment, or the excluded characters for an excluded segment
     */
    String text();
}
