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
package org.coordinatekit.crf.core.feature.configuration;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Describes one parameter a {@link FeatureExtractorFactory} accepts: its {@link #name()},
 * {@link #kind()}, whether it is {@link #required()}, its {@link #defaultValue() default}, and —
 * for an {@link ParameterKind#ENUMERATION enumeration} — the {@link #allowedValues() values} it
 * admits, or — for an {@link ParameterKind#INTEGER integer} — the {@link #minimumValue()} and
 * {@link #maximumValue()} it admits.
 *
 * <p>
 * A parameter is one of three shapes:
 *
 * <ul>
 * <li><em>required</em> — must be present; has no default
 * <li><em>optional with a default</em> — absent means the default applies
 * <li><em>optional with no default</em> — absent means unset, read through the {@code find*}
 * accessors
 * </ul>
 *
 * <p>
 * Build one with {@link #builder(String, ParameterKind)}.
 */
public final class ParameterDescriptor {
    private final Set<String> allowedValues;
    private final @Nullable String defaultValue;
    private final String description;
    private final ParameterKind kind;
    private final int maximumValue;
    private final int minimumValue;
    private final String name;
    private final boolean required;

    private ParameterDescriptor(Builder builder) {
        this.allowedValues = Set.copyOf(builder.allowedValues);
        this.defaultValue = builder.defaultValue;
        this.description = builder.description;
        this.kind = builder.kind;
        this.maximumValue = builder.maximumValue;
        this.minimumValue = builder.minimumValue;
        this.name = builder.name;
        this.required = builder.required;
    }

    /**
     * Returns the values this parameter admits, non-empty only for an {@link ParameterKind#ENUMERATION}
     * parameter and empty for every other kind.
     *
     * @return the allowed values, or an empty set
     */
    public Set<String> allowedValues() {
        return allowedValues;
    }

    /**
     * Creates a builder for a parameter with the given name and kind.
     *
     * @param name the parameter name
     * @param kind the parameter kind
     * @return a new builder
     */
    public static Builder builder(String name, ParameterKind kind) {
        return new Builder(name, kind);
    }

    /**
     * Returns the default value applied when the parameter is absent, or {@code null} when the
     * parameter is required or optional with no default.
     *
     * @return the default value, or {@code null}
     */
    public @Nullable String defaultValue() {
        return defaultValue;
    }

    /**
     * Returns a human-readable description of the parameter.
     *
     * @return the description
     */
    public String description() {
        return description;
    }

    @Override
    public boolean equals(@Nullable Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ParameterDescriptor other)) {
            return false;
        }
        return maximumValue == other.maximumValue && minimumValue == other.minimumValue && required == other.required
                && kind == other.kind && name.equals(other.name) && description.equals(other.description)
                && allowedValues.equals(other.allowedValues) && Objects.equals(defaultValue, other.defaultValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(allowedValues, defaultValue, description, kind, maximumValue, minimumValue, name, required);
    }

    /**
     * Returns the kind the raw value coerces to.
     *
     * @return the parameter kind
     */
    public ParameterKind kind() {
        return kind;
    }

    /**
     * Returns the largest value this parameter admits, meaningful only for an
     * {@link ParameterKind#INTEGER integer} parameter.
     *
     * @return the maximum value, or {@link Integer#MAX_VALUE} when unbounded above
     */
    public int maximumValue() {
        return maximumValue;
    }

    /**
     * Returns the smallest value this parameter admits, meaningful only for an
     * {@link ParameterKind#INTEGER integer} parameter.
     *
     * @return the minimum value, or {@link Integer#MIN_VALUE} when unbounded below
     */
    public int minimumValue() {
        return minimumValue;
    }

    /**
     * Returns the parameter name, unique within a factory's descriptor.
     *
     * @return the parameter name
     */
    public String name() {
        return name;
    }

    /**
     * Returns whether the parameter must be present.
     *
     * @return {@code true} if the parameter is required
     */
    public boolean required() {
        return required;
    }

    @Override
    public String toString() {
        return "ParameterDescriptor[name=" + name + ", kind=" + kind + ", allowedValues=" + allowedValues
                + ", defaultValue=" + defaultValue + ", description=" + description + ", maximumValue=" + maximumValue
                + ", minimumValue=" + minimumValue + ", required=" + required + "]";
    }

    /**
     * Builder for {@link ParameterDescriptor}.
     */
    public static final class Builder {
        private final ParameterKind kind;
        private final String name;
        private Set<String> allowedValues = Set.of();
        private @Nullable String defaultValue;
        private String description = "";
        private int maximumValue = Integer.MAX_VALUE;
        private int minimumValue = Integer.MIN_VALUE;
        private boolean required = false;

        private Builder(String name, ParameterKind kind) {
            this.name = Objects.requireNonNull(name, "name must not be null");
            this.kind = Objects.requireNonNull(kind, "kind must not be null");
        }

        /**
         * Sets the values an {@link ParameterKind#ENUMERATION} parameter admits.
         *
         * @param allowedValues the allowed values
         * @return this builder
         */
        public Builder allowedValues(Set<String> allowedValues) {
            this.allowedValues = Set.copyOf(allowedValues);
            return this;
        }

        /**
         * Sets a human-readable description of the parameter.
         *
         * @param description the description
         * @return this builder
         */
        public Builder description(String description) {
            this.description = Objects.requireNonNull(description, "description must not be null");
            return this;
        }

        /**
         * Sets the default value applied when the parameter is absent, which also makes the parameter
         * optional.
         *
         * @param defaultValue the default value
         * @return this builder
         */
        public Builder defaultValue(@Nullable String defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        /**
         * Sets the largest value an {@link ParameterKind#INTEGER} parameter admits. Defaults to
         * {@link Integer#MAX_VALUE}, meaning unbounded above.
         *
         * @param maximum the maximum value
         * @return this builder
         */
        public Builder maximumValue(int maximum) {
            this.maximumValue = maximum;
            return this;
        }

        /**
         * Sets the smallest value an {@link ParameterKind#INTEGER} parameter admits. Defaults to
         * {@link Integer#MIN_VALUE}, meaning unbounded below.
         *
         * @param minimum the minimum value
         * @return this builder
         */
        public Builder minimumValue(int minimum) {
            this.minimumValue = minimum;
            return this;
        }

        /**
         * Sets whether the parameter must be present. Defaults to {@code false}.
         *
         * @param required {@code true} to require the parameter
         * @return this builder
         */
        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        /**
         * Builds the descriptor.
         *
         * @return a new {@link ParameterDescriptor}
         * @throws IllegalStateException if the parameter is both required and default-bearing, if an
         *         enumeration declares no allowed values, if a non-enumeration declares allowed values, if
         *         an enumeration's default is not among its allowed values, if bounds are set on a
         *         non-integer parameter, if the minimum exceeds the maximum, or if an integer default falls
         *         outside the bounds
         */
        public ParameterDescriptor build() {
            if (required && defaultValue != null) {
                throw new IllegalStateException(
                        "parameter '" + name + "' cannot be both required and have a default value"
                );
            }
            if (kind == ParameterKind.ENUMERATION) {
                if (allowedValues.isEmpty()) {
                    throw new IllegalStateException(
                            "enumeration parameter '" + name + "' must declare at least one allowed value"
                    );
                }
                if (defaultValue != null && !allowedValues.contains(defaultValue)) {
                    throw new IllegalStateException(
                            "default value '" + defaultValue + "' of enumeration parameter '" + name
                                    + "' is not among the allowed values " + new TreeSet<>(allowedValues)
                    );
                }
            } else if (!allowedValues.isEmpty()) {
                throw new IllegalStateException(
                        "allowed values apply only to enumeration parameters, but '" + name + "' is " + kind
                );
            }
            if (kind == ParameterKind.INTEGER) {
                if (minimumValue > maximumValue) {
                    throw new IllegalStateException(
                            "minimum " + minimumValue + " of parameter '" + name + "' exceeds its maximum "
                                    + maximumValue
                    );
                }
                if (defaultValue != null) {
                    int parsedDefault;
                    try {
                        parsedDefault = Integer.parseInt(defaultValue);
                    } catch (NumberFormatException exception) {
                        throw new IllegalStateException(
                                "default value '" + defaultValue + "' of integer parameter '" + name
                                        + "' is not an integer",
                                exception
                        );
                    }
                    if (parsedDefault < minimumValue || parsedDefault > maximumValue) {
                        throw new IllegalStateException(
                                "default value '" + defaultValue + "' of parameter '" + name + "' must be "
                                        + ParameterValidation.rangeDescription(minimumValue, maximumValue)
                        );
                    }
                }
            } else if (minimumValue != Integer.MIN_VALUE || maximumValue != Integer.MAX_VALUE) {
                throw new IllegalStateException(
                        "bounds apply only to integer parameters, but '" + name + "' is " + kind
                );
            }
            return new ParameterDescriptor(this);
        }
    }
}
