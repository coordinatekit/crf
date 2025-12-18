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
package org.coordinatekit.crf.core;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * A tag provider for string tags with a defined tag set and starting tag.
 *
 * <p>
 * This implementation maintains a fixed set of valid tags and a designated starting tag. Unknown
 * tag strings are converted to the starting tag.
 *
 * @see TagProvider
 */
@NullMarked
public class StringTagProvider implements TagProvider<String> {
    private final Set<String> tags;
    private final String startingTag;

    /**
     * Constructs a new string tag provider with the starting tag.
     *
     * @param startingTag the starting/fallback tag, must be present in tags
     * @throws IllegalArgumentException if startingTag is not in tags when tags are non-empty
     */
    public StringTagProvider(String startingTag) {
        this(Set.of(), startingTag);
    }

    /**
     * Constructs a new string tag provider with the specified tags and starting tag.
     *
     * @param tags the collection of valid tags
     * @param startingTag the starting/fallback tag, must be present in tags
     * @throws IllegalArgumentException if startingTag is not in tags when tags are non-empty
     */
    public StringTagProvider(Collection<String> tags, String startingTag) {
        if (!tags.isEmpty() && !tags.contains(startingTag)) {
            throw new IllegalArgumentException("Starting tag '" + startingTag + "' must be present in tags");
        }

        this.startingTag = startingTag;
        this.tags = Set.copyOf(tags);
    }

    @Override
    public String decode(@Nullable String tag) {
        if (tag == null || (!tags().isEmpty() && !tags().contains(tag))) {
            return startingTag();
        }

        return tag;
    }

    @Override
    public @Nullable String encode(String rawTag) {
        return rawTag;
    }

    @Override
    public String startingTag() {
        return startingTag;
    }

    @Override
    public Set<String> tags() {
        return Set.copyOf(tags);
    }
}
