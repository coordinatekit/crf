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

import org.coordinatekit.crf.core.UncheckedCrfException;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Thrown when the content of a feature configuration is invalid: an unknown or missing parameter, a
 * value that cannot be coerced to its declared kind or that falls outside its declared bounds, a
 * cross-parameter rule a factory's {@code validate} rejects, an arity violation, an unknown factory
 * type, or a tree deeper than the assembler allows.
 *
 * <p>
 * Every message names the extractor type and, when the offending node carried a
 * {@link SourceLocation}, the file and line it came from, so a downstream can point a user at the
 * exact spot, for example
 * {@code extractor 'length' at /path/to/features.xml:12:5 — unknown parameter
 * 'beofre' (did you mean 'before'?)}. A hand-built node carries no source location, so its message
 * names the extractor type alone, as in
 * {@code extractor 'length' — unknown parameter 'beofre' ...}. The {@link #extractorType()} and
 * {@link #sourceLocation()} accessors expose the same structured parts for callers that build their
 * own guidance.
 *
 * @see DuplicateFactoryTypeException
 */
public final class FeatureConfigurationException extends UncheckedCrfException {
    /**
     * The type of the extractor whose configuration was invalid.
     */
    private final String extractorType;

    /**
     * The source location of the offending node, or {@code null} for a hand-built node that carried
     * none.
     */
    private final @Nullable SourceLocation source;

    /**
     * Constructs a located exception naming the extractor type, the source location when the node
     * carried one, and the problem.
     *
     * @param extractorType the type of the extractor whose configuration was invalid
     * @param source the source location of the offending node, or {@code null} if it carried none
     * @param detail the human-readable description of the problem
     */
    public FeatureConfigurationException(String extractorType, @Nullable SourceLocation source, String detail) {
        super(message(extractorType, source, detail));
        this.extractorType = extractorType;
        this.source = source;
    }

    /**
     * Constructs a located exception naming the extractor type, the source location when the node
     * carried one, and the problem, chaining a cause.
     *
     * @param extractorType the type of the extractor whose configuration was invalid
     * @param source the source location of the offending node, or {@code null} if it carried none
     * @param detail the human-readable description of the problem
     * @param cause the underlying exception
     */
    public FeatureConfigurationException(
            String extractorType,
            @Nullable SourceLocation source,
            String detail,
            Throwable cause
    ) {
        super(message(extractorType, source, detail), cause);
        this.extractorType = extractorType;
        this.source = source;
    }

    /**
     * Returns the type of the extractor whose configuration was invalid.
     *
     * @return the extractor type
     */
    public String extractorType() {
        return extractorType;
    }

    /**
     * Composes the located message: the extractor type, an {@code at <source>} clause when the node
     * carried a source location, and the detail.
     *
     * @param extractorType the extractor type
     * @param source the source location, or {@code null} if the node carried none
     * @param detail the description of the problem
     * @return the composed message
     */
    private static String message(String extractorType, @Nullable SourceLocation source, String detail) {
        String at = source == null ? "" : " at " + source;
        return "extractor '" + extractorType + "'" + at + " — " + detail;
    }

    /**
     * Returns the source location of the offending node, or empty for a hand-built node that carried
     * none.
     *
     * @return the source location, or empty
     */
    public Optional<SourceLocation> sourceLocation() {
        return Optional.ofNullable(source);
    }
}
