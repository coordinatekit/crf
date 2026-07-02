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
package org.coordinatekit.crf.core.feature;

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
 * <p>
 * Every feature is created by {@link #createFeature(String)} or
 * {@link #createFeatureWithValue(String, String)} at offset {@code 0} (the current token); the
 * window component is stamped later with {@link #withOffset(int)}. The {@link Enum} overloads are a
 * convenience for callers whose names and values are enum constants, resolving each via
 * {@link Enum#name()}. Callers should statically import these factory methods.
 *
 * @see FeatureFormat
 */
public final class Feature implements Comparable<Feature> {
    private static final Comparator<Feature> NATURAL_ORDER = Comparator.comparingInt(Feature::offset)
            .thenComparing(Feature::name)
            .thenComparing(Feature::value, Comparator.nullsFirst(Comparator.naturalOrder()));

    private final String name;
    private final int offset;
    private final @Nullable String value;

    private Feature(int offset, String name, @Nullable String value) {
        this.offset = offset;
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.value = value;
    }

    @Override
    public int compareTo(Feature other) {
        return NATURAL_ORDER.compare(this, other);
    }

    /**
     * Creates a feature with the given name and no value, at offset {@code 0}.
     *
     * @param name the feature name
     * @return a new feature with a null value
     * @throws NullPointerException if {@code name} is null
     */
    public static Feature createFeature(String name) {
        return createFeatureWithValue(name, (String) null);
    }

    /**
     * Creates a feature whose name is {@code name.name()} and no value, at offset {@code 0}.
     *
     * @param name the feature name as an enum constant
     * @return a new feature with a null value
     * @throws NullPointerException if {@code name} is null
     */
    public static Feature createFeature(Enum<?> name) {
        Objects.requireNonNull(name, "name must not be null");
        return createFeature(name.name());
    }

    /**
     * Creates a feature with the given name and value, at offset {@code 0}.
     *
     * @param name the feature name
     * @param value the feature value, or {@code null} for a bare name
     * @return a new feature
     * @throws NullPointerException if {@code name} is null
     */
    public static Feature createFeatureWithValue(String name, @Nullable String value) {
        Objects.requireNonNull(name, "name must not be null");
        return new Feature(0, name, value);
    }

    /**
     * Creates a feature with the given name and whose value is {@code value.name()}, at offset
     * {@code 0}.
     *
     * @param name the feature name
     * @param value the feature value as an enum constant
     * @return a new feature
     * @throws NullPointerException if {@code name} or {@code value} is null
     */
    public static Feature createFeatureWithValue(String name, Enum<?> value) {
        Objects.requireNonNull(value, "value must not be null");
        return createFeatureWithValue(name, value.name());
    }

    /**
     * Creates a feature whose name is {@code name.name()} and the given value, at offset {@code 0}.
     *
     * @param name the feature name as an enum constant
     * @param value the feature value, or {@code null} for a bare name
     * @return a new feature
     * @throws NullPointerException if {@code name} is null
     */
    public static Feature createFeatureWithValue(Enum<?> name, @Nullable String value) {
        Objects.requireNonNull(name, "name must not be null");
        return createFeatureWithValue(name.name(), value);
    }

    /**
     * Creates a feature whose name is {@code name.name()} and value is {@code value.name()}, at offset
     * {@code 0}.
     *
     * @param name the feature name as an enum constant
     * @param value the feature value as an enum constant
     * @return a new feature
     * @throws NullPointerException if {@code name} or {@code value} is null
     */
    public static Feature createFeatureWithValue(Enum<?> name, Enum<?> value) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(value, "value must not be null");
        return createFeatureWithValue(name.name(), value.name());
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
