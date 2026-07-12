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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.util.stream.Stream;

/**
 * Tests {@link SourceLocation}: {@link SourceLocation#equals(Object)} and
 * {@link SourceLocation#hashCode()} compare all three fields, and {@link SourceLocation#toString()}
 * renders the column when it is a real one-based column, and falls back to {@code source:line} for
 * the unknown-location sentinel or a source format that cannot report a column.
 */
class SourceLocationTest {
    record EqualsParameters(String name, SourceLocation first, SourceLocation second, boolean expectedEqual) {}

    static Stream<EqualsParameters> equals__comparesAllFields() {
        SourceLocation base = SourceLocation.of(URI.create("features.xml"), 3, 5);
        return Stream.of(
                new EqualsParameters(
                        "identical_fields",
                        base,
                        SourceLocation.of(URI.create("features.xml"), 3, 5),
                        true
                ),
                new EqualsParameters(
                        "differs_by_source",
                        base,
                        SourceLocation.of(URI.create("other.xml"), 3, 5),
                        false
                ),
                new EqualsParameters(
                        "differs_by_line",
                        base,
                        SourceLocation.of(URI.create("features.xml"), 4, 5),
                        false
                ),
                new EqualsParameters(
                        "differs_by_column",
                        base,
                        SourceLocation.of(URI.create("features.xml"), 3, 6),
                        false
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void equals__comparesAllFields(EqualsParameters parameters) {
        // ACT & ASSERT //
        assertEquals(parameters.expectedEqual(), parameters.first().equals(parameters.second()));
    }

    @Test
    void equals__hashCodeConsistentForEqualInstances() {
        // ARRANGE //
        SourceLocation first = SourceLocation.of(URI.create("features.xml"), 3, 5);
        SourceLocation second = SourceLocation.of(URI.create("features.xml"), 3, 5);

        // ACT & ASSERT //
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @SuppressWarnings({"AssertBetweenInconvertibleTypes", "MisorderedAssertEqualsArguments"})
    @Test
    void equals__rejectsForeignType() {
        // ARRANGE //
        SourceLocation location = SourceLocation.of(URI.create("features.xml"), 3, 5);

        // ACT & ASSERT //
        assertNotEquals(location, "features.xml:3:5");
    }

    @Test
    void equals__rejectsNull() {
        // ARRANGE //
        SourceLocation location = SourceLocation.of(URI.create("features.xml"), 3, 5);

        // ACT & ASSERT //
        assertNotEquals(null, location);
    }

    record ToStringParameters(String name, URI source, int line, int column, String expected) {}

    static Stream<ToStringParameters> toString__rendersColumnWhenKnown() {
        return Stream.of(
                new ToStringParameters(
                        "file_uri_with_column",
                        URI.create("file:/path/to/features.xml"),
                        12,
                        5,
                        "/path/to/features.xml:12:5"
                ),
                new ToStringParameters(
                        "relative_uri_with_column",
                        URI.create("features.xml"),
                        12,
                        5,
                        "features.xml:12:5"
                ),
                new ToStringParameters(
                        "non_file_absolute_uri_with_column",
                        URI.create("https://host/features.xml"),
                        12,
                        5,
                        "https://host/features.xml:12:5"
                ),
                new ToStringParameters(
                        "unknown_column_renders_without_column",
                        URI.create("features.xml"),
                        12,
                        -1,
                        "features.xml:12"
                ),
                new ToStringParameters(
                        "unknown_line_renders_literal_negative_one",
                        URI.create("features.xml"),
                        -1,
                        -1,
                        "features.xml:-1"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void toString__rendersColumnWhenKnown(ToStringParameters parameters) {
        // ARRANGE //
        SourceLocation location = SourceLocation.of(parameters.source(), parameters.line(), parameters.column());

        // ACT //
        String actual = location.toString();

        // ASSERT //
        assertEquals(parameters.expected(), actual);
    }
}
