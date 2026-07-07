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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Tests {@link ParameterValidation}: each kind is coerced from its raw string, defaults are applied
 * for absent optionals, paths resolve against the base directory, and every content mistake
 * surfaces as a located {@link FeatureConfigurationException} — including the {@code did you mean}
 * hint for a mistyped name.
 */
class ParameterValidationTest {
    private static final Path BASE_DIRECTORY = ConfigurationTestSupport
            .resourceDirectory("/org/coordinatekit/crf/core/feature/configuration/states.xml");

    private static final Set<ParameterDescriptor> PARAMETERS = Set.of(
            ParameterDescriptor.builder("after", ParameterKind.INTEGER).defaultValue("0").build(),
            ParameterDescriptor.builder("before", ParameterKind.INTEGER).defaultValue("0").minimumValue(0)
                    .maximumValue(10).build(),
            ParameterDescriptor.builder("dictionary", ParameterKind.PATH).required(true).build(),
            ParameterDescriptor.builder("flag", ParameterKind.BOOLEAN).defaultValue("false").build(),
            ParameterDescriptor.builder("mode", ParameterKind.ENUMERATION).allowedValues(Set.of("fast", "slow"))
                    .defaultValue("fast").build()
    );

    private static AssemblyContext context() {
        return DefaultAssemblyContext.root(BASE_DIRECTORY).descend("window").descend("composite").descend("length");
    }

    private static FeatureExtractorParameters validate(Map<String, String> raw) {
        return ParameterValidation.validate("length", raw, PARAMETERS, context());
    }

    record RangeDescriptionParameters(String name, int minimum, int maximum, String expected) {}

    static Stream<RangeDescriptionParameters> rangeDescription() {
        return Stream.of(
                new RangeDescriptionParameters("bothBounds", 1, 5, "between 1 and 5"),
                new RangeDescriptionParameters("minimumOnly", 0, Integer.MAX_VALUE, ">= 0"),
                new RangeDescriptionParameters("maximumOnly", Integer.MIN_VALUE, 10, "<= 10"),
                new RangeDescriptionParameters("neitherBound", Integer.MIN_VALUE, Integer.MAX_VALUE, "any value")
        );
    }

    @MethodSource
    @ParameterizedTest
    void rangeDescription(RangeDescriptionParameters parameters) {
        // ACT //
        String actual = ParameterValidation.rangeDescription(parameters.minimum(), parameters.maximum());

        // ASSERT //
        assertEquals(parameters.expected(), actual);
    }

    @Test
    void validate__acceptsInclusiveIntegerBounds() {
        // ACT & ASSERT //
        assertEquals(0, validate(Map.of("before", "0", "dictionary", "states.xml")).getInteger("before"));
        assertEquals(10, validate(Map.of("before", "10", "dictionary", "states.xml")).getInteger("before"));
    }

    @Test
    void validate__appliesDefaultsForAbsentOptionals() {
        // ACT //
        FeatureExtractorParameters parameters = validate(Map.of("dictionary", "states.xml"));

        // ASSERT //
        assertEquals(0, parameters.getInteger("before"));
        assertEquals(0, parameters.getInteger("after"));
        assertFalse(parameters.getBoolean("flag"));
        assertEquals("fast", parameters.getEnumeration("mode"));
    }

    @Test
    void validate__coercesEachKind() {
        // ACT //
        FeatureExtractorParameters parameters = validate(
                Map.of("before", "3", "flag", "TRUE", "mode", "slow", "dictionary", "states.xml")
        );

        // ASSERT //
        assertEquals(3, parameters.getInteger("before"));
        assertTrue(parameters.getBoolean("flag"));
        assertEquals("slow", parameters.getEnumeration("mode"));
        assertEquals(BASE_DIRECTORY.resolve("states.xml"), parameters.getPath("dictionary"));
    }

    record ExceptionParameters(String name, Map<String, String> raw, String expectedMessage) {}

    static Stream<ExceptionParameters> validate__exception() {
        return Stream.of(
                new ExceptionParameters(
                        "unknownParameterWithSuggestion",
                        Map.of("beofre", "1", "dictionary", "states.xml"),
                        "extractor 'length' at /window/composite/length — unknown parameter 'beofre'"
                                + " (did you mean 'before'?)"
                ),
                new ExceptionParameters(
                        "unknownParameterWithoutSuggestion",
                        Map.of("zzzzzz", "1", "dictionary", "states.xml"),
                        "extractor 'length' at /window/composite/length — unknown parameter 'zzzzzz'"
                ),
                new ExceptionParameters(
                        "missingRequired",
                        Map.of("before", "1"),
                        "extractor 'length' at /window/composite/length — missing required parameter 'dictionary'"
                ),
                new ExceptionParameters(
                        "badInteger",
                        Map.of("before", "two", "dictionary", "states.xml"),
                        "extractor 'length' at /window/composite/length — parameter 'before' expects an integer"
                                + " but got 'two'"
                ),
                new ExceptionParameters(
                        "badBoolean",
                        Map.of("flag", "yes", "dictionary", "states.xml"),
                        "extractor 'length' at /window/composite/length — parameter 'flag' expects a boolean"
                                + " (true or false) but got 'yes'"
                ),
                new ExceptionParameters(
                        "enumerationOutsideAllowed",
                        Map.of("mode", "medium", "dictionary", "states.xml"),
                        "extractor 'length' at /window/composite/length — parameter 'mode' expects one of"
                                + " [fast, slow] but got 'medium'"
                ),
                new ExceptionParameters(
                        "integerBelowMinimum",
                        Map.of("before", "-1", "dictionary", "states.xml"),
                        "extractor 'length' at /window/composite/length — parameter 'before' expects an integer"
                                + " between 0 and 10 but got '-1'"
                ),
                new ExceptionParameters(
                        "integerAboveMaximum",
                        Map.of("before", "11", "dictionary", "states.xml"),
                        "extractor 'length' at /window/composite/length — parameter 'before' expects an integer"
                                + " between 0 and 10 but got '11'"
                ),
                new ExceptionParameters(
                        "pathMissing",
                        Map.of("dictionary", "missing.xml"),
                        "extractor 'length' at /window/composite/length — parameter 'dictionary' points at a file"
                                + " that does not exist: " + BASE_DIRECTORY.resolve("missing.xml")
                ),
                new ExceptionParameters(
                        "pathNotRegularFile",
                        Map.of("dictionary", "."),
                        "extractor 'length' at /window/composite/length — parameter 'dictionary' points at a path"
                                + " that is not a regular file: " + BASE_DIRECTORY.resolve(".")
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void validate__exception(ExceptionParameters parameters) {
        // ACT //
        FeatureConfigurationException exception = assertThrows(
                FeatureConfigurationException.class,
                () -> validate(parameters.raw())
        );

        // ASSERT //
        assertEquals(parameters.expectedMessage(), exception.getMessage());
        assertEquals("length", exception.extractorType());
        assertEquals("/window/composite/length", exception.location());
    }

    @Test
    void validate__missingRequiredReportsAlphabeticallyFirstName() {
        // ARRANGE //
        Set<ParameterDescriptor> parameters = Set.of(
                ParameterDescriptor.builder("zebra", ParameterKind.STRING).required(true).build(),
                ParameterDescriptor.builder("alpha", ParameterKind.STRING).required(true).build()
        );

        // ACT //
        FeatureConfigurationException exception = assertThrows(
                FeatureConfigurationException.class,
                () -> ParameterValidation.validate("length", Map.of(), parameters, context())
        );

        // ASSERT //
        assertEquals(
                "extractor 'length' at /window/composite/length — missing required parameter 'alpha'",
                exception.getMessage()
        );
    }

    @Test
    void validate__pathNotReadable(@TempDir Path tempDirectory) throws IOException {
        // ARRANGE //
        Path unreadable = tempDirectory.resolve("secret.xml");
        Files.writeString(unreadable, "<a/>");
        boolean revoked = unreadable.toFile().setReadable(false, false);
        Assumptions.assumeTrue(
                revoked && !Files.isReadable(unreadable),
                "cannot revoke read access on this platform/user"
        );

        try {
            AssemblyContext unreadableContext = DefaultAssemblyContext.root(tempDirectory).descend("window")
                    .descend("composite").descend("length");

            // ACT //
            FeatureConfigurationException exception = assertThrows(
                    FeatureConfigurationException.class,
                    () -> ParameterValidation
                            .validate("length", Map.of("dictionary", "secret.xml"), PARAMETERS, unreadableContext)
            );

            // ASSERT //
            assertEquals(
                    "extractor 'length' at /window/composite/length — parameter 'dictionary' points at a file that"
                            + " is not readable: " + unreadable,
                    exception.getMessage()
            );
        } finally {
            if (!unreadable.toFile().setReadable(true, false)) {
                System.err.println("failed to restore read permission on " + unreadable);
            }
        }
    }
}
