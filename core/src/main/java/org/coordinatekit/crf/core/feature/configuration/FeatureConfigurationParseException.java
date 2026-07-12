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

/**
 * Thrown by a {@link FeatureConfigurationParser} when a configuration source is not well-formed in
 * its format: malformed markup, a disallowed {@code DOCTYPE}, a missing required attribute, the
 * wrong number of top-level extractors, or an element the format does not expect.
 *
 * <p>
 * This is a <em>syntactic</em> problem in the file's shape, found before any node reaches the
 * assembler — distinct from the <em>content</em> problems (an unknown factory type, an invalid
 * parameter) that surface as {@link FeatureConfigurationException} once assembly runs. Every
 * message is located, formatted {@code /path/to/features.xml:12:5 — <detail>} through the
 * {@link #location()}.
 *
 * @see FeatureConfigurationException
 */
public final class FeatureConfigurationParseException extends UncheckedCrfException {
    /**
     * Where in the source the problem was found.
     */
    private final SourceLocation location;

    /**
     * Constructs a located parse exception.
     *
     * @param location where in the source the problem was found
     * @param detail the human-readable description of the problem
     */
    public FeatureConfigurationParseException(SourceLocation location, String detail) {
        super(location + " — " + detail);
        this.location = location;
    }

    /**
     * Constructs a located parse exception, chaining a cause.
     *
     * @param location where in the source the problem was found
     * @param detail the human-readable description of the problem
     * @param cause the underlying exception
     */
    public FeatureConfigurationParseException(SourceLocation location, String detail, Throwable cause) {
        super(location + " — " + detail, cause);
        this.location = location;
    }

    /**
     * Returns where in the source the problem was found.
     *
     * @return the source location
     */
    public SourceLocation location() {
        return location;
    }
}
