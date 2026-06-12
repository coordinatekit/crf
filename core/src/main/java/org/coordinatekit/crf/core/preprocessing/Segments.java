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

import java.util.List;
import java.util.Objects;

/**
 * Factory for tag-less {@link Segment} instances and the shared surface-reconstruction helper.
 *
 * <p>
 * {@link Segment} is a public interface backed by a private record here, with static factory
 * methods that construct and validate instances. Callers should statically import the factory
 * methods. The tag-carrying counterpart is {@link TrainingSegments}.
 *
 * @see Segment
 */
public final class Segments {
    private Segments() {}

    /**
     * Creates an {@link SegmentKind#EXCLUDED EXCLUDED} segment carrying a dropped run of characters.
     *
     * @param text the excluded characters
     * @return a new excluded segment
     * @throws NullPointerException if {@code text} is null
     */
    public static Segment excluded(String text) {
        return new DefaultSegment(SegmentKind.EXCLUDED, text);
    }

    /**
     * Reconstructs the surface text of a list of segments by concatenating each segment's text in
     * document order.
     *
     * @param segments the segments in document order
     * @return the reconstructed surface string
     * @throws NullPointerException if {@code segments} or any segment is null
     */
    static String surface(List<? extends Segment> segments) {
        StringBuilder builder = new StringBuilder();
        for (Segment segment : segments) {
            builder.append(segment.text());
        }
        return builder.toString();
    }

    /**
     * Creates a {@link SegmentKind#TOKEN TOKEN} segment carrying token text.
     *
     * @param text the token text
     * @return a new token segment
     * @throws NullPointerException if {@code text} is null
     */
    public static Segment token(String text) {
        return new DefaultSegment(SegmentKind.TOKEN, text);
    }

    private record DefaultSegment(SegmentKind kind, String text) implements Segment {
        private DefaultSegment {
            Objects.requireNonNull(kind, "kind must not be null");
            Objects.requireNonNull(text, "text must not be null");
        }
    }
}
