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

import java.util.Objects;

/**
 * Factory for {@link TrainingSegment} instances.
 *
 * <p>
 * {@link TrainingSegment} is a public interface backed by a private record here, with static
 * factory methods that construct and validate instances. Callers should statically import the
 * factory methods.
 *
 * @see TrainingSegment
 */
public final class TrainingSegments {
    private TrainingSegments() {}

    /**
     * Creates an {@link SegmentKind#EXCLUDED EXCLUDED} segment carrying a dropped run of characters.
     *
     * @param text the excluded characters
     * @param <T> the tag type of the enclosing sequence
     * @return a new excluded segment with a {@code null} tag
     * @throws NullPointerException if {@code text} is null
     */
    public static <T> TrainingSegment<T> excluded(String text) {
        return new DefaultTrainingSegment<>(SegmentKind.EXCLUDED, null, text);
    }

    /**
     * Creates a {@link SegmentKind#TOKEN TOKEN} segment carrying a tagged token.
     *
     * @param tag the tag for the token; must not be null
     * @param text the token text
     * @param <T> the tag type
     * @return a new token segment
     * @throws NullPointerException if {@code tag} or {@code text} is null
     */
    public static <T> TrainingSegment<T> token(T tag, String text) {
        return new DefaultTrainingSegment<>(
                SegmentKind.TOKEN,
                Objects.requireNonNull(tag, "tag must not be null"),
                text
        );
    }

    private record DefaultTrainingSegment<T> (SegmentKind kind, @Nullable T tag, String text)
            implements TrainingSegment<T> {
        private DefaultTrainingSegment {
            Objects.requireNonNull(kind, "kind must not be null");
            Objects.requireNonNull(text, "text must not be null");
            if (kind == SegmentKind.TOKEN && tag == null) {
                throw new IllegalArgumentException("A token segment requires a non-null tag.");
            }
            if (kind == SegmentKind.EXCLUDED && tag != null) {
                throw new IllegalArgumentException("An excluded segment must not carry a tag.");
            }
        }
    }
}
