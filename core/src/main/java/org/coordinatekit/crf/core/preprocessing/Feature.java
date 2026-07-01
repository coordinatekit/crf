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

import java.util.Comparator;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A single extracted feature as structured data.
 *
 * <p>
 * A feature is a {@link #name()}, an optional {@link #value()}, and an {@link #offset()} describing
 * the window position it was extracted from relative to the current token: {@code 0} for the
 * current token, a negative offset for a preceding token, a positive offset for a following token.
 * The grammar that turns a feature into the model's flat feature string (positional prefixes,
 * {@code name=value} pairs) lives in {@link FeatureFormat}, not here; this type carries the parts
 * as data so the pipeline never touches strings.
 *
 * <p>
 * Features are compared by {@link #offset()} first, then {@link #name()}, then {@link #value()}
 * with {@code null} ordered first. This is the deterministic order the pipeline uses when
 * serializing a feature set.
 *
 * @see Features
 * @see FeatureFormat
 */
public final class Feature implements Comparable<Feature> {
    private static final Comparator<Feature> NATURAL_ORDER = Comparator.comparingInt(Feature::offset)
            .thenComparing(Feature::name)
            .thenComparing(Feature::value, Comparator.nullsFirst(Comparator.naturalOrder()));

    private final String name;
    private final int offset;
    private final @Nullable String value;

    Feature(int offset, String name, @Nullable String value) {
        this.offset = offset;
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.value = value;
    }

    @Override
    public int compareTo(Feature other) {
        return NATURAL_ORDER.compare(this, other);
    }

    @Override
    public boolean equals(@Nullable Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Feature other)) {
            return false;
        }
        return offset == other.offset && name.equals(other.name) && Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(offset, name, value);
    }

    /**
     * Returns the feature name.
     *
     * @return the feature name, never {@code null}
     */
    public String name() {
        return name;
    }

    /**
     * Returns the window offset this feature was extracted from, relative to the current token.
     *
     * <p>
     * {@code 0} is the current token, a negative offset a preceding token, a positive offset a
     * following token.
     *
     * @return the window offset
     */
    public int offset() {
        return offset;
    }

    @Override
    public String toString() {
        return "Feature[offset=" + offset + ", name=" + name + ", value=" + value + "]";
    }

    /**
     * Returns the feature value, or {@code null} when the feature is a bare name with no value.
     *
     * @return the feature value, or {@code null}
     */
    public @Nullable String value() {
        return value;
    }

    /**
     * Returns a copy of this feature with its offset replaced by {@code offset}.
     *
     * <p>
     * The offset is replaced, not accumulated: applying this to a feature that already carries an
     * offset discards the old offset.
     *
     * @param offset the new window offset
     * @return a feature equal to this one but with the given offset
     */
    public Feature withOffset(int offset) {
        return new Feature(offset, name, value);
    }
}
