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

import org.jspecify.annotations.Nullable;

/**
 * One element of a {@link TrainingSequence}: either a tagged token or a dropped run of excluded
 * characters.
 *
 * <p>
 * Segments preserve the full surface of an annotated example in document order. A
 * {@link SegmentKind#TOKEN TOKEN} segment carries both {@link #text() text} and a non-null
 * {@link #tag() tag}; an {@link SegmentKind#EXCLUDED EXCLUDED} segment carries only text, with a
 * {@code null} tag. Instances are created through the {@link TrainingSegments} factory.
 *
 * <p>
 * This is the tag-carrying refinement of {@link Segment}: it adds {@link #tag()} to the shared
 * {@link #kind()} and {@link #text()} surface decomposition.
 *
 * @param <T> the type of tag (label) associated with token segments
 * @see Segment
 * @see SegmentKind
 * @see TrainingSegments
 * @see TrainingSequence
 */
public interface TrainingSegment<T> extends Segment {
    /**
     * Returns the tag for this segment, or {@code null} when this segment is not a token.
     *
     * @return the tag, non-null if and only if {@link #kind()} is {@link SegmentKind#TOKEN}
     */
    @Nullable
    T tag();
}
