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

import static org.coordinatekit.crf.core.preprocessing.Feature.createFeature;
import static org.coordinatekit.crf.core.preprocessing.Feature.createFeatureWithValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * Tests {@link DefaultFeatureFormat}: rendering and parsing across the offset prefixes, the
 * null-versus-empty value distinction, values containing {@code =}, the round-trip identity in both
 * directions, the documented null contract, the asymmetric/malformed parse surface, and the
 * illegal-name rejection in {@code render}.
 */
class DefaultFeatureFormatTest {
    private final DefaultFeatureFormat format = new DefaultFeatureFormat();

    record ExceptionParameters(
            String name,
            Executable action,
            Class<? extends Exception> expectedClass,
            String expectedMessageFragment
    ) {}

    record MappingParameters(String name, Feature feature, String rendered) {}

    record ParseAsymmetricParameters(String name, String rendered, Feature expected) {}

    static Stream<MappingParameters> mapping() {
        return Stream.of(
                new MappingParameters("bare_name", createFeature("IS_NUMERIC"), "IS_NUMERIC"),
                new MappingParameters("name_value", createFeatureWithValue("LENGTH", "3"), "LENGTH=3"),
                new MappingParameters("empty_value", createFeatureWithValue("X", ""), "X="),
                new MappingParameters("value_with_equals", createFeatureWithValue("A", "B=C"), "A=B=C"),
                new MappingParameters(
                        "previous_prefix",
                        createFeatureWithValue("TOKEN", "517").withOffset(-1),
                        "PREV_1__TOKEN=517"
                ),
                new MappingParameters(
                        "next_prefix",
                        createFeatureWithValue("TOKEN", "517").withOffset(2),
                        "NEXT_2__TOKEN=517"
                )
        );
    }

    @MethodSource("mapping")
    @ParameterizedTest
    void parse(MappingParameters parameters) {
        // ACT //
        Feature actual = format.parse(parameters.rendered());

        // ASSERT //
        assertEquals(parameters.feature(), actual, parameters.name());
    }

    // These inputs are intentionally not part of mapping(): they document parse's permissive behavior
    // and do not round-trip back to the same rendered string.
    static Stream<ParseAsymmetricParameters> parse__asymmetric() {
        return Stream.of(
                // PREV_0__ collapses to offset 0; renders back as bare "X", so it does not round-trip.
                new ParseAsymmetricParameters("zero_offset_prefix", "PREV_0__X", createFeature("X")),
                // A leading '=' yields an empty name.
                new ParseAsymmetricParameters("leading_equals", "=value", createFeatureWithValue("", "value")),
                // parse does not validate: it produces a prefix-shaped name that render() would reject.
                new ParseAsymmetricParameters(
                        "nested_prefix_name",
                        "PREV_1__NEXT_2__TOKEN",
                        createFeature("NEXT_2__TOKEN").withOffset(-1)
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void parse__asymmetric(ParseAsymmetricParameters parameters) {
        // ACT //
        Feature actual = format.parse(parameters.rendered());

        // ASSERT //
        assertEquals(parameters.expected(), actual, parameters.name());
    }

    @SuppressWarnings({"DataFlowIssue", "NullAway"})
    static Stream<ExceptionParameters> parse__exception() {
        DefaultFeatureFormat formatter = new DefaultFeatureFormat();
        return Stream.of(
                new ExceptionParameters(
                        "parse_null",
                        () -> formatter.parse(null),
                        NullPointerException.class,
                        "rendered must not be null"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void parse__exception(ExceptionParameters parameters) {
        // ACT //
        Exception exception = assertThrows(parameters.expectedClass(), parameters.action());

        // ASSERT //
        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(message.contains(parameters.expectedMessageFragment()), parameters.name() + ": " + message);
    }

    @MethodSource("mapping")
    @ParameterizedTest
    void render(MappingParameters parameters) {
        // ACT //
        String actual = format.render(parameters.feature());

        // ASSERT //
        assertEquals(parameters.rendered(), actual, parameters.name());
    }

    @SuppressWarnings({"DataFlowIssue", "NullAway"})
    static Stream<ExceptionParameters> render__exception() {
        DefaultFeatureFormat formatter = new DefaultFeatureFormat();
        return Stream.of(
                new ExceptionParameters(
                        "name_with_equals",
                        () -> formatter.render(createFeature("A=B")),
                        IllegalArgumentException.class,
                        "must not contain '='"
                ),
                new ExceptionParameters(
                        "name_looks_like_prefix",
                        () -> formatter.render(createFeature("PREV_1__X")),
                        IllegalArgumentException.class,
                        "positional prefix"
                ),
                new ExceptionParameters(
                        "render_null",
                        () -> formatter.render(null),
                        NullPointerException.class,
                        "feature must not be null"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void render__exception(ExceptionParameters parameters) {
        // ACT //
        Exception exception = assertThrows(parameters.expectedClass(), parameters.action());

        // ASSERT //
        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(message.contains(parameters.expectedMessageFragment()), parameters.name() + ": " + message);
    }

    @MethodSource("mapping")
    @ParameterizedTest
    void roundTrip(MappingParameters parameters) {
        // ACT //
        Feature actual = format.parse(format.render(parameters.feature()));

        // ASSERT //
        assertEquals(parameters.feature(), actual, parameters.name());
    }

    @MethodSource("mapping")
    @ParameterizedTest
    void roundTripRendered(MappingParameters parameters) {
        // ACT //
        String actual = format.render(format.parse(parameters.rendered()));

        // ASSERT //
        assertEquals(parameters.rendered(), actual, parameters.name());
    }
}
