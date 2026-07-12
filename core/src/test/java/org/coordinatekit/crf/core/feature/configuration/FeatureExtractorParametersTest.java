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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Tests {@link FeatureExtractorParameters}: the {@code get} accessors read required and
 * default-bearing values, the {@code find} accessors return optionals for optional-no-default
 * parameters, and reaching for an undeclared name, a wrong kind, or an absent optional value with
 * {@code get} is a factory bug that throws {@link IllegalStateException}.
 */
class FeatureExtractorParametersTest {
    private static final URL BASE = ConfigurationTestSupport
            .resourceUrl("/org/coordinatekit/crf/core/feature/configuration/states.xml");

    private static final Set<ParameterDescriptor> PARAMETERS = Set.of(
            ParameterDescriptor.builder("count", ParameterKind.INTEGER).required(true).build(),
            ParameterDescriptor.builder("file", ParameterKind.RESOURCE).required(true).build(),
            ParameterDescriptor.builder("flag", ParameterKind.BOOLEAN).defaultValue("true").build(),
            ParameterDescriptor.builder("label", ParameterKind.STRING).defaultValue("X").build(),
            ParameterDescriptor.builder("mode", ParameterKind.ENUMERATION).allowedValues(Set.of("fast", "slow"))
                    .defaultValue("fast").build(),
            ParameterDescriptor.builder("note", ParameterKind.STRING).build()
    );

    private static FeatureExtractorParameters parameters() {
        return ParameterValidation.validate("test", Map.of("count", "3", "file", "states.xml"), PARAMETERS, BASE, null);
    }

    @Test
    void find__absentOptionalIsEmpty() {
        // ACT & ASSERT //
        assertEquals(Optional.empty(), parameters().findString("note"));
    }

    @Test
    void find__presentValuesAreOptionalsOf() throws MalformedURLException, URISyntaxException {
        // ARRANGE //
        FeatureExtractorParameters parameters = ParameterValidation.validate(
                "test",
                Map.of("count", "3", "file", "states.xml", "flag", "false", "mode", "slow", "note", "N"),
                PARAMETERS,
                BASE,
                null
        );

        // ACT & ASSERT //
        assertEquals(Optional.of("N"), parameters.findString("note"));
        assertEquals(Optional.of(false), parameters.findBoolean("flag"));
        assertEquals(Optional.of(3), parameters.findInteger("count"));
        assertEquals(Optional.of("slow"), parameters.findEnumeration("mode"));
        assertEquals(
                Optional.of(BASE.toURI().resolve("states.xml").toURL().toString()),
                parameters.findResource("file").map(URL::toString)
        );
    }

    @Test
    void get__readsRequiredAndDefaults() throws MalformedURLException, URISyntaxException {
        // ARRANGE //
        FeatureExtractorParameters parameters = parameters();

        // ACT & ASSERT //
        assertEquals(3, parameters.getInteger("count"));
        assertTrue(parameters.getBoolean("flag"));
        assertEquals("X", parameters.getString("label"));
        assertEquals("fast", parameters.getEnumeration("mode"));
        assertEquals(BASE.toURI().resolve("states.xml").toURL().toString(), parameters.getResource("file").toString());
    }

    record IllegalUseParameters(
            String name,
            Executable action,
            Class<? extends Exception> expectedClass,
            String expectedMessage
    ) {}

    static Stream<IllegalUseParameters> illegalUse() {
        return Stream.of(
                new IllegalUseParameters(
                        "undeclared_name",
                        () -> parameters().getString("missing"),
                        IllegalStateException.class,
                        "no parameter named 'missing' is declared"
                ),
                new IllegalUseParameters(
                        "undeclared_name_on_find",
                        () -> parameters().findInteger("missing"),
                        IllegalStateException.class,
                        "no parameter named 'missing' is declared"
                ),
                new IllegalUseParameters(
                        "wrong_kind",
                        () -> parameters().getInteger("label"),
                        IllegalStateException.class,
                        "parameter 'label' is declared as STRING, not INTEGER"
                ),
                new IllegalUseParameters(
                        "wrong_kind_on_find",
                        () -> parameters().findInteger("label"),
                        IllegalStateException.class,
                        "parameter 'label' is declared as STRING, not INTEGER"
                ),
                new IllegalUseParameters(
                        "get_on_absent_optional",
                        () -> parameters().getString("note"),
                        IllegalStateException.class,
                        "parameter 'note' has no value; it is optional with no default, so use the find accessor"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void illegalUse(IllegalUseParameters parameters) {
        // ACT //
        Exception exception = assertThrows(parameters.expectedClass(), parameters.action());

        // ASSERT //
        assertEquals(parameters.expectedMessage(), exception.getMessage());
    }
}
