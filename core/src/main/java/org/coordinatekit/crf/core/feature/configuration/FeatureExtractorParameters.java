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

import java.net.URL;
import java.util.Optional;

/**
 * Validated, typed access to one node's parameters, handed to a factory at construction time.
 *
 * <p>
 * Every value has already been coerced to its declared {@link ParameterKind kind} and every default
 * applied, so a factory reads without re-parsing. The two accessors per kind draw the line at
 * optionality:
 *
 * <ul>
 * <li>{@code getX} is total — use it for a required parameter or one with a default, where a value
 * is always present;
 * <li>{@code findX} returns an {@link Optional} — use it for an optional parameter with no default,
 * which may be absent.
 * </ul>
 *
 * <p>
 * Reaching for a name that was never declared, or for a kind other than the one it was declared
 * with, is a factory bug, not a configuration mistake, and throws {@link IllegalStateException}.
 * Calling {@code getX} on an optional-no-default parameter that happens to be absent is the same
 * kind of bug.
 */
public interface FeatureExtractorParameters {
    /**
     * Returns the value of an optional {@link ParameterKind#BOOLEAN} parameter, empty if absent.
     *
     * @param name the parameter name
     * @return the value, or empty if the parameter is absent
     * @throws IllegalStateException if {@code name} is not a declared boolean parameter
     */
    Optional<Boolean> findBoolean(String name);

    /**
     * Returns the value of an optional {@link ParameterKind#ENUMERATION} parameter, empty if absent.
     *
     * @param name the parameter name
     * @return the value, or empty if the parameter is absent
     * @throws IllegalStateException if {@code name} is not a declared enumeration parameter
     */
    Optional<String> findEnumeration(String name);

    /**
     * Returns the value of an optional {@link ParameterKind#INTEGER} parameter, empty if absent.
     *
     * @param name the parameter name
     * @return the value, or empty if the parameter is absent
     * @throws IllegalStateException if {@code name} is not a declared integer parameter
     */
    Optional<Integer> findInteger(String name);

    /**
     * Returns the value of an optional {@link ParameterKind#RESOURCE} parameter, empty if absent.
     *
     * @param name the parameter name
     * @return the resolved value, or empty if the parameter is absent
     * @throws IllegalStateException if {@code name} is not a declared resource parameter
     */
    Optional<URL> findResource(String name);

    /**
     * Returns the value of an optional {@link ParameterKind#STRING} parameter, empty if absent.
     *
     * @param name the parameter name
     * @return the value, or empty if the parameter is absent
     * @throws IllegalStateException if {@code name} is not a declared string parameter
     */
    Optional<String> findString(String name);

    /**
     * Returns the value of a required or default-bearing {@link ParameterKind#BOOLEAN} parameter.
     *
     * @param name the parameter name
     * @return the value
     * @throws IllegalStateException if {@code name} is not a declared boolean parameter, or is an
     *         optional-no-default parameter that is absent
     */
    boolean getBoolean(String name);

    /**
     * Returns the value of a required or default-bearing {@link ParameterKind#ENUMERATION} parameter.
     *
     * @param name the parameter name
     * @return the value
     * @throws IllegalStateException if {@code name} is not a declared enumeration parameter, or is an
     *         optional-no-default parameter that is absent
     */
    String getEnumeration(String name);

    /**
     * Returns the value of a required or default-bearing {@link ParameterKind#INTEGER} parameter.
     *
     * @param name the parameter name
     * @return the value
     * @throws IllegalStateException if {@code name} is not a declared integer parameter, or is an
     *         optional-no-default parameter that is absent
     */
    int getInteger(String name);

    /**
     * Returns the value of a required or default-bearing {@link ParameterKind#RESOURCE} parameter.
     *
     * @param name the parameter name
     * @return the resolved value
     * @throws IllegalStateException if {@code name} is not a declared resource parameter, or is an
     *         optional-no-default parameter that is absent
     */
    URL getResource(String name);

    /**
     * Returns the value of a required or default-bearing {@link ParameterKind#STRING} parameter.
     *
     * @param name the parameter name
     * @return the value
     * @throws IllegalStateException if {@code name} is not a declared string parameter, or is an
     *         optional-no-default parameter that is absent
     */
    String getString(String name);
}
