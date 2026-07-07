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

import java.nio.file.Path;
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
    private static final Path BASE_DIRECTORY = ConfigurationTestSupport
            .resourceDirectory("/org/coordinatekit/crf/core/feature/configuration/states.xml");

    private static final Set<ParameterDescriptor> PARAMETERS = Set.of(
            ParameterDescriptor.builder("count", ParameterKind.INTEGER).required(true).build(),
            ParameterDescriptor.builder("file", ParameterKind.PATH).required(true).build(),
            ParameterDescriptor.builder("flag", ParameterKind.BOOLEAN).defaultValue("true").build(),
            ParameterDescriptor.builder("label", ParameterKind.STRING).defaultValue("X").build(),
            ParameterDescriptor.builder("mode", ParameterKind.ENUMERATION).allowedValues(Set.of("fast", "slow"))
                    .defaultValue("fast").build(),
            ParameterDescriptor.builder("note", ParameterKind.STRING).build()
    );

    private static FeatureExtractorParameters parameters() {
        AssemblyContext context = DefaultAssemblyContext.root(BASE_DIRECTORY);
        return ParameterValidation.validate("test", Map.of("count", "3", "file", "states.xml"), PARAMETERS, context);
    }

    @Test
    void find__absentOptionalIsEmpty() {
        // ACT & ASSERT //
        assertEquals(Optional.empty(), parameters().findString("note"));
    }

    @Test
    void find__presentValuesAreOptionalsOf() {
        // ARRANGE //
        AssemblyContext context = DefaultAssemblyContext.root(BASE_DIRECTORY);
        FeatureExtractorParameters parameters = ParameterValidation.validate(
                "test",
                Map.of("count", "3", "file", "states.xml", "flag", "false", "mode", "slow", "note", "N"),
                PARAMETERS,
                context
        );

        // ACT & ASSERT //
        assertEquals(Optional.of("N"), parameters.findString("note"));
        assertEquals(Optional.of(false), parameters.findBoolean("flag"));
        assertEquals(Optional.of(3), parameters.findInteger("count"));
        assertEquals(Optional.of("slow"), parameters.findEnumeration("mode"));
        assertEquals(Optional.of(BASE_DIRECTORY.resolve("states.xml")), parameters.findPath("file"));
    }

    @Test
    void get__readsRequiredAndDefaults() {
        // ARRANGE //
        FeatureExtractorParameters parameters = parameters();

        // ACT & ASSERT //
        assertEquals(3, parameters.getInteger("count"));
        assertTrue(parameters.getBoolean("flag"));
        assertEquals("X", parameters.getString("label"));
        assertEquals("fast", parameters.getEnumeration("mode"));
        assertEquals(BASE_DIRECTORY.resolve("states.xml"), parameters.getPath("file"));
    }

    record IllegalUseParameters(String name, Executable action, String expectedMessage) {}

    static Stream<IllegalUseParameters> illegalUse() {
        return Stream.of(
                new IllegalUseParameters(
                        "undeclaredName",
                        () -> parameters().getString("missing"),
                        "no parameter named 'missing' is declared"
                ),
                new IllegalUseParameters(
                        "undeclaredNameOnFind",
                        () -> parameters().findInteger("missing"),
                        "no parameter named 'missing' is declared"
                ),
                new IllegalUseParameters(
                        "wrongKind",
                        () -> parameters().getInteger("label"),
                        "parameter 'label' is declared as STRING, not INTEGER"
                ),
                new IllegalUseParameters(
                        "wrongKindOnFind",
                        () -> parameters().findInteger("label"),
                        "parameter 'label' is declared as STRING, not INTEGER"
                ),
                new IllegalUseParameters(
                        "getOnAbsentOptional",
                        () -> parameters().getString("note"),
                        "parameter 'note' has no value; it is optional with no default, so use the find accessor"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void illegalUse(IllegalUseParameters parameters) {
        // ACT //
        IllegalStateException exception = assertThrows(IllegalStateException.class, parameters.action());

        // ASSERT //
        assertEquals(parameters.expectedMessage(), exception.getMessage());
    }
}
