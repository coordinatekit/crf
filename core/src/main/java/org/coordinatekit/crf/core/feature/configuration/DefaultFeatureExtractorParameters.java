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

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The package-private record implementing {@link FeatureExtractorParameters}, produced by
 * {@link ParameterValidation}.
 *
 * <p>
 * {@code values} holds only the parameters that carry a value — required parameters,
 * default-bearing parameters (with the default already coerced), and optional-no-default parameters
 * that a configuration supplied. Each value is pre-coerced to the Java type its
 * {@link ParameterKind} maps to: {@link Boolean}, {@link Integer}, {@link Path}, or {@link String}.
 * The {@code parameters} are kept so the accessors can tell a factory bug (an undeclared name or
 * wrong kind) apart from an absent optional value.
 *
 * @param parameters the declared parameters these values belong to
 * @param values the pre-coerced values, keyed by parameter name
 */
record DefaultFeatureExtractorParameters(Set<ParameterDescriptor> parameters, Map<String, Object> values)
        implements FeatureExtractorParameters {
    @Override
    public Optional<Boolean> findBoolean(String name) {
        return find(name, ParameterKind.BOOLEAN, Boolean.class);
    }

    @Override
    public Optional<String> findEnumeration(String name) {
        return find(name, ParameterKind.ENUMERATION, String.class);
    }

    @Override
    public Optional<Integer> findInteger(String name) {
        return find(name, ParameterKind.INTEGER, Integer.class);
    }

    @Override
    public Optional<Path> findPath(String name) {
        return find(name, ParameterKind.PATH, Path.class);
    }

    @Override
    public Optional<String> findString(String name) {
        return find(name, ParameterKind.STRING, String.class);
    }

    @Override
    public boolean getBoolean(String name) {
        return get(name, ParameterKind.BOOLEAN, Boolean.class);
    }

    @Override
    public String getEnumeration(String name) {
        return get(name, ParameterKind.ENUMERATION, String.class);
    }

    @Override
    public int getInteger(String name) {
        return get(name, ParameterKind.INTEGER, Integer.class);
    }

    @Override
    public Path getPath(String name) {
        return get(name, ParameterKind.PATH, Path.class);
    }

    @Override
    public String getString(String name) {
        return get(name, ParameterKind.STRING, String.class);
    }

    /**
     * Returns the coerced value of {@code name}, present when the configuration or a default supplied
     * one.
     *
     * @param name the parameter name
     * @param expectedKind the kind the accessor expects
     * @param type the Java type the kind coerces to
     * @param <X> the coerced value type
     * @return the value, or empty when absent
     * @throws IllegalStateException if {@code name} is not declared or was declared with a different
     *         kind
     */
    private <X> Optional<X> find(String name, ParameterKind expectedKind, Class<X> type) {
        checkDeclared(name, expectedKind);
        return Optional.ofNullable(values.get(name)).map(type::cast);
    }

    /**
     * Returns the coerced value of {@code name}, which must be present.
     *
     * @param name the parameter name
     * @param expectedKind the kind the accessor expects
     * @param type the Java type the kind coerces to
     * @param <X> the coerced value type
     * @return the value
     * @throws IllegalStateException if {@code name} is not declared, was declared with a different
     *         kind, or is an optional-no-default parameter that is absent
     */
    private <X> X get(String name, ParameterKind expectedKind, Class<X> type) {
        checkDeclared(name, expectedKind);
        Object value = values.get(name);
        if (value == null) {
            throw new IllegalStateException(
                    "parameter '" + name + "' has no value; it is optional with no default, so use the find accessor"
            );
        }
        return type.cast(value);
    }

    /**
     * Verifies that {@code name} names a parameter declared with {@code expectedKind}.
     *
     * @param name the parameter name
     * @param expectedKind the kind the accessor expects
     * @throws IllegalStateException if {@code name} is not declared or was declared with a different
     *         kind
     */
    private void checkDeclared(String name, ParameterKind expectedKind) {
        ParameterDescriptor parameter = lookup(name);
        if (parameter == null) {
            throw new IllegalStateException("no parameter named '" + name + "' is declared");
        }
        if (parameter.kind() != expectedKind) {
            throw new IllegalStateException(
                    "parameter '" + name + "' is declared as " + parameter.kind() + ", not " + expectedKind
            );
        }
    }

    /**
     * Returns the descriptor of the parameter named {@code name}, or {@code null} if none is declared.
     *
     * @param name the parameter name
     * @return the parameter descriptor, or {@code null}
     */
    private @Nullable ParameterDescriptor lookup(String name) {
        for (ParameterDescriptor parameter : parameters) {
            if (parameter.name().equals(name)) {
                return parameter;
            }
        }
        return null;
    }
}
