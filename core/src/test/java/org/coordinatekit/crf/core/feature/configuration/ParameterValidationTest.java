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
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/**
 * Tests {@link ParameterValidation}: each kind is coerced from its raw string, defaults are applied
 * for absent optionals, resources resolve against the base location, and every content mistake
 * surfaces as a located {@link FeatureConfigurationException} — including the {@code did you mean}
 * hint for a mistyped name.
 */
class ParameterValidationTest {
    private static final URL BASE = ConfigurationTestSupport
            .resourceUrl("/org/coordinatekit/crf/core/feature/configuration/states.xml");

    private static final SourceLocation LOCATION = SourceLocation.of(URI.create("features.xml"), 12, 1);

    private static final Set<ParameterDescriptor> PARAMETERS = Set.of(
            ParameterDescriptor.builder("after", ParameterKind.INTEGER).defaultValue("0").build(),
            ParameterDescriptor.builder("before", ParameterKind.INTEGER).defaultValue("0").minimumValue(0)
                    .maximumValue(10).build(),
            ParameterDescriptor.builder("dictionary", ParameterKind.RESOURCE).required(true).build(),
            ParameterDescriptor.builder("flag", ParameterKind.BOOLEAN).defaultValue("false").build(),
            ParameterDescriptor.builder("mode", ParameterKind.ENUMERATION).allowedValues(Set.of("fast", "slow"))
                    .defaultValue("fast").build()
    );

    private static FeatureExtractorParameters validate(Map<String, String> raw) {
        return ParameterValidation.validate("length", raw, PARAMETERS, BASE, LOCATION);
    }

    record RangeDescriptionParameters(String name, int minimum, int maximum, String expected) {}

    static Stream<RangeDescriptionParameters> rangeDescription() {
        return Stream.of(
                new RangeDescriptionParameters("both_bounds", 1, 5, "between 1 and 5"),
                new RangeDescriptionParameters("minimum_only", 0, Integer.MAX_VALUE, ">= 0"),
                new RangeDescriptionParameters("maximum_only", Integer.MIN_VALUE, 10, "<= 10"),
                new RangeDescriptionParameters("neither_bound", Integer.MIN_VALUE, Integer.MAX_VALUE, "any value")
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
    void validate__coercesEachKind() throws MalformedURLException, URISyntaxException {
        // ACT //
        FeatureExtractorParameters parameters = validate(
                Map.of("before", "3", "flag", "TRUE", "mode", "slow", "dictionary", "states.xml")
        );

        // ASSERT //
        assertEquals(3, parameters.getInteger("before"));
        assertTrue(parameters.getBoolean("flag"));
        assertEquals("slow", parameters.getEnumeration("mode"));
        assertEquals(
                BASE.toURI().resolve("states.xml").toURL().toString(),
                parameters.getResource("dictionary").toString()
        );
    }

    record ValidateExceptionParameters(
            String name,
            Executable action,
            Class<? extends Exception> expectedClass,
            String expectedMessage
    ) {}

    static Stream<ValidateExceptionParameters> validate__exception() throws MalformedURLException, URISyntaxException {
        return Stream.of(
                new ValidateExceptionParameters(
                        "unknown_parameter_with_suggestion",
                        () -> validate(Map.of("beofre", "1", "dictionary", "states.xml")),
                        FeatureConfigurationException.class,
                        "extractor 'length' at features.xml:12:1 — unknown parameter 'beofre'"
                                + " (did you mean 'before'?)"
                ),
                new ValidateExceptionParameters(
                        "unknown_parameter_without_suggestion",
                        () -> validate(Map.of("zzzzzz", "1", "dictionary", "states.xml")),
                        FeatureConfigurationException.class,
                        "extractor 'length' at features.xml:12:1 — unknown parameter 'zzzzzz'"
                ),
                new ValidateExceptionParameters(
                        "missing_required",
                        () -> validate(Map.of("before", "1")),
                        FeatureConfigurationException.class,
                        "extractor 'length' at features.xml:12:1 — missing required parameter 'dictionary'"
                ),
                new ValidateExceptionParameters(
                        "bad_integer",
                        () -> validate(Map.of("before", "two", "dictionary", "states.xml")),
                        FeatureConfigurationException.class,
                        "extractor 'length' at features.xml:12:1 — parameter 'before' expects an integer"
                                + " but got 'two'"
                ),
                new ValidateExceptionParameters(
                        "bad_boolean",
                        () -> validate(Map.of("flag", "yes", "dictionary", "states.xml")),
                        FeatureConfigurationException.class,
                        "extractor 'length' at features.xml:12:1 — parameter 'flag' expects a boolean"
                                + " (true or false) but got 'yes'"
                ),
                new ValidateExceptionParameters(
                        "enumeration_outside_allowed",
                        () -> validate(Map.of("mode", "medium", "dictionary", "states.xml")),
                        FeatureConfigurationException.class,
                        "extractor 'length' at features.xml:12:1 — parameter 'mode' expects one of"
                                + " [fast, slow] but got 'medium'"
                ),
                new ValidateExceptionParameters(
                        "integer_below_minimum",
                        () -> validate(Map.of("before", "-1", "dictionary", "states.xml")),
                        FeatureConfigurationException.class,
                        "extractor 'length' at features.xml:12:1 — parameter 'before' expects an integer"
                                + " between 0 and 10 but got '-1'"
                ),
                new ValidateExceptionParameters(
                        "integer_above_maximum",
                        () -> validate(Map.of("before", "11", "dictionary", "states.xml")),
                        FeatureConfigurationException.class,
                        "extractor 'length' at features.xml:12:1 — parameter 'before' expects an integer"
                                + " between 0 and 10 but got '11'"
                ),
                new ValidateExceptionParameters(
                        "path_missing",
                        () -> validate(Map.of("dictionary", "missing.xml")),
                        FeatureConfigurationException.class,
                        "extractor 'length' at features.xml:12:1 — parameter 'dictionary' points at a"
                                + " resource that cannot be opened: " + BASE.toURI().resolve("missing.xml").toURL()
                ),
                new ValidateExceptionParameters(
                        "absolute_url_with_illegal_syntax",
                        () -> validate(Map.of("dictionary", "http://exa mple.com")),
                        FeatureConfigurationException.class,
                        "extractor 'length' at features.xml:12:1 — parameter 'dictionary' is not a valid"
                                + " resource: 'http://exa mple.com'"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void validate__exception(ValidateExceptionParameters parameters) {
        // ACT //
        Exception exception = assertThrows(parameters.expectedClass(), parameters.action());

        // ASSERT //
        assertEquals(parameters.expectedMessage(), exception.getMessage());
        assertEquals("length", ((FeatureConfigurationException) exception).extractorType());
        assertEquals(LOCATION, ((FeatureConfigurationException) exception).sourceLocation().orElseThrow());
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
                () -> ParameterValidation.validate("length", Map.of(), parameters, BASE, LOCATION)
        );

        // ASSERT //
        assertEquals(
                "extractor 'length' at features.xml:12:1 — missing required parameter 'alpha'",
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
            URL documentUrl = tempDirectory.resolve("features.xml").toUri().toURL();

            // ACT //
            FeatureConfigurationException exception = assertThrows(
                    FeatureConfigurationException.class,
                    () -> ParameterValidation
                            .validate("length", Map.of("dictionary", "secret.xml"), PARAMETERS, documentUrl, LOCATION)
            );

            // ASSERT //
            assertEquals(
                    "extractor 'length' at features.xml:12:1 — parameter 'dictionary' points at a resource"
                            + " that cannot be opened: " + unreadable.toUri().toURL(),
                    exception.getMessage()
            );
        } finally {
            if (!unreadable.toFile().setReadable(true, false)) {
                System.err.println("failed to restore read permission on " + unreadable);
            }
        }
    }

    @Test
    void validate__resolvesAbsoluteUrlDictionary() {
        // ACT //
        FeatureExtractorParameters parameters = validate(Map.of("dictionary", BASE.toString()));

        // ASSERT //
        assertEquals(BASE.toString(), parameters.getResource("dictionary").toString());
    }

    @Test
    void validate__resolvesRelativeDictionaryAgainstJarBase(@TempDir Path tempDirectory) throws IOException {
        // ARRANGE //
        Path jarFile = tempDirectory.resolve("archive.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarFile))) {
            jar.putNextEntry(new JarEntry("config/features.xml"));
            jar.closeEntry();
            jar.putNextEntry(new JarEntry("config/dictionary.xml"));
            jar.closeEntry();
        }
        URL jarBase = URI.create("jar:" + jarFile.toUri() + "!/config/features.xml").toURL();

        // ACT //
        FeatureExtractorParameters parameters = ParameterValidation
                .validate("length", Map.of("dictionary", "dictionary.xml"), PARAMETERS, jarBase, LOCATION);

        // ASSERT //
        assertEquals(
                "jar:" + jarFile.toUri() + "!/config/dictionary.xml",
                parameters.getResource("dictionary").toString()
        );
    }

    record RelativeResourceParameters(String name, String fileName) {}

    static Stream<RelativeResourceParameters> validate__resolvesRelativeDictionaryWithUriSignificantCharacters() {
        return Stream.of(
                new RelativeResourceParameters("space", "dict with space.xml"),
                new RelativeResourceParameters("hash", "dict#1.xml"),
                new RelativeResourceParameters("percent", "dict%1.xml")
        );
    }

    @MethodSource
    @ParameterizedTest
    void validate__resolvesRelativeDictionaryWithUriSignificantCharacters(
            RelativeResourceParameters parameters,
            @TempDir Path tempDirectory
    ) throws IOException {
        // ARRANGE //
        Path dictionaryFile = tempDirectory.resolve(parameters.fileName());
        Files.writeString(dictionaryFile, "<a/>");
        URL documentUrl = tempDirectory.resolve("features.xml").toUri().toURL();

        // ACT //
        FeatureExtractorParameters result = ParameterValidation
                .validate("length", Map.of("dictionary", parameters.fileName()), PARAMETERS, documentUrl, LOCATION);

        // ASSERT //
        assertEquals(dictionaryFile.toUri().toURL().toString(), result.getResource("dictionary").toString());
    }

    @Test
    void validate__skipsProbeForNonLocalResource() throws IOException {
        // ARRANGE //
        int refusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            refusedPort = socket.getLocalPort();
        }
        String dictionaryUrl = "http://127.0.0.1:" + refusedPort + "/dictionary.xml";

        // ACT //
        // network resources defer their open to build time, so a connection-refused address still
        // passes validation here
        FeatureExtractorParameters parameters = validate(Map.of("dictionary", dictionaryUrl));

        // ASSERT //
        assertEquals(dictionaryUrl, parameters.getResource("dictionary").toString());
    }
}
