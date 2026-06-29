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
 * Factory for {@link Feature} instances.
 *
 * <p>
 * Every feature is created here at offset {@code 0} (the current token); the window component is
 * stamped later with {@link Feature#withOffset(int)}. The {@link Enum} overloads are a convenience
 * for callers whose names and values are enum constants, resolving each via {@link Enum#name()}.
 * {@link #naturalOrder()} exposes the framework-owned comparator that puts a feature set into a
 * deterministic sequence. Callers should statically import these factory methods.
 *
 * @see Feature
 */
public final class Features {
    private static final Comparator<Feature> NATURAL_ORDER = Comparator.comparingInt(Feature::offset)
            .thenComparing(Feature::name)
            .thenComparing(Feature::value, Comparator.nullsFirst(Comparator.naturalOrder()));

    private Features() {
        throw new UnsupportedOperationException("Features is a utility class");
    }

    /**
     * Returns the natural order over features: by {@link Feature#offset()}, then
     * {@link Feature#name()}, then {@link Feature#value()} with {@code null} ordered first.
     *
     * <p>
     * This is the framework's order for putting a feature set into a deterministic sequence; it is a
     * {@link Comparator} rather than a {@link Comparable} contract on {@link Feature} so the order is
     * consistent across every implementation and implementers need not supply it.
     *
     * @return the natural feature comparator
     */
    public static Comparator<Feature> naturalOrder() {
        return NATURAL_ORDER;
    }

    /**
     * Creates a feature with the given name and no value, at offset {@code 0}.
     *
     * @param name the feature name
     * @return a new feature with a null value
     * @throws NullPointerException if {@code name} is null
     */
    public static Feature of(String name) {
        return of(name, null);
    }

    /**
     * Creates a feature with the given name and value, at offset {@code 0}.
     *
     * @param name the feature name
     * @param value the feature value, or {@code null} for a bare name
     * @return a new feature
     * @throws NullPointerException if {@code name} is null
     */
    public static Feature of(String name, @Nullable String value) {
        Objects.requireNonNull(name, "name must not be null");
        return new DefaultFeature(0, name, value);
    }

    /**
     * Creates a feature whose name is {@code name.name()} and no value, at offset {@code 0}.
     *
     * @param name the feature name as an enum constant
     * @return a new feature with a null value
     * @throws NullPointerException if {@code name} is null
     */
    public static Feature of(Enum<?> name) {
        Objects.requireNonNull(name, "name must not be null");
        return of(name.name());
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
    public static Feature of(Enum<?> name, Enum<?> value) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(value, "value must not be null");
        return of(name.name(), value.name());
    }

    private record DefaultFeature(int offset, String name, @Nullable String value) implements Feature {
        @Override
        public Feature withOffset(int offset) {
            return new DefaultFeature(offset, name, value);
        }
    }
}
