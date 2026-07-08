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

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A position in a configuration source, format-neutral so any {@link FeatureConfigurationParser}
 * can populate it.
 *
 * <p>
 * A {@link FeatureExtractorNode} produced by a parser carries its {@code SourceLocation} so a
 * configuration-content error can point at the exact file and line. A hand-built node (through
 * {@link FeatureExtractorNodes}) carries none, and an error for it names no location.
 */
public final class SourceLocation {
    private final int column;
    private final int line;
    private final URI source;

    private SourceLocation(URI source, int line, int column) {
        this.column = column;
        this.line = line;
        this.source = source;
    }

    /**
     * Returns the column within the line, one-based.
     *
     * @return the column
     */
    public int column() {
        return column;
    }

    /**
     * Renders {@code source}: a filesystem path for a {@code file:} URI, else the URI in full.
     *
     * @param source the source to render
     * @return the rendered source
     */
    private static String display(URI source) {
        if ("file".equalsIgnoreCase(source.getScheme())) {
            try {
                return Path.of(source).toString();
            } catch (RuntimeException exception) {
                // Non-hierarchical/opaque file: URI — fall back to the raw URI text.
                return source.toString();
            }
        }
        return source.toString();
    }

    @Override
    public boolean equals(@Nullable Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof SourceLocation other)) {
            return false;
        }
        return column == other.column && line == other.line && Objects.equals(source, other.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(column, line, source);
    }

    /**
     * Returns the one-based line within the source.
     *
     * @return the line
     */
    public int line() {
        return line;
    }

    /**
     * Returns the URI of the source, for example {@code file:/path/to/features.xml}.
     *
     * @return the source URI
     */
    public URI uri() {
        return source;
    }

    /**
     * Creates a source location.
     *
     * @param source the URI of the source
     * @param line the one-based line within the source
     * @param column the column within the line, one-based
     * @return a new source location
     */
    public static SourceLocation of(URI source, int line, int column) {
        Objects.requireNonNull(source, "source must not be null");
        return new SourceLocation(source, line, column);
    }

    /**
     * Renders the source followed by the line and, when known, the column. A {@code file:} source
     * renders as its absolute filesystem path, for example {@code /path/to/features.xml:12:5}; a
     * relative URI renders as written, for example {@code features.xml:12:5}; any other absolute URI
     * renders in full, for example {@code https://host/features.xml:12:5}. A location with no column
     * (the unknown-location sentinel, or a source format that cannot report one) renders as
     * {@code source:line}.
     *
     * @return the rendered location
     */
    @Override
    public String toString() {
        String rendered = display(source) + ":" + line;
        return column > 0 ? rendered + ":" + column : rendered;
    }
}
