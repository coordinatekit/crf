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
 * Thrown when the content of a feature configuration is invalid: an unknown or missing parameter, a
 * value that cannot be coerced to its declared kind or that falls outside its declared bounds, a
 * cross-parameter rule a factory's {@code validate} rejects, an arity violation, an unknown factory
 * type, or a tree deeper than the assembler allows.
 *
 * <p>
 * Every message is <em>located</em> — it names the extractor type and the positional path to the
 * offending node so a downstream can point a user at the exact spot, for example
 * {@code extractor 'length' at /window/composite/length — unknown parameter 'beofre' (did you mean
 * 'before'?)}. The {@link #extractorType()} and {@link #location()} accessors expose the same
 * structured parts for callers that build their own guidance.
 *
 * @see DuplicateFactoryTypeException
 */
public final class FeatureConfigurationException extends UncheckedCrfException {
    /**
     * The positional path to the offending node, for example {@code /window/composite/length}.
     */
    private final String location;

    /**
     * The type of the extractor whose configuration was invalid.
     */
    private final String extractorType;

    /**
     * Constructs a located exception naming the extractor type, the positional path, and the problem.
     *
     * @param extractorType the type of the extractor whose configuration was invalid
     * @param location the positional path to the offending node
     * @param detail the human-readable description of the problem
     */
    public FeatureConfigurationException(String extractorType, String location, String detail) {
        super("extractor '" + extractorType + "' at " + location + " — " + detail);
        this.extractorType = extractorType;
        this.location = location;
    }

    /**
     * Constructs a located exception naming the extractor type, the positional path, and the problem,
     * chaining a cause.
     *
     * @param extractorType the type of the extractor whose configuration was invalid
     * @param location the positional path to the offending node
     * @param detail the human-readable description of the problem
     * @param cause the underlying exception
     */
    public FeatureConfigurationException(String extractorType, String location, String detail, Throwable cause) {
        super("extractor '" + extractorType + "' at " + location + " — " + detail, cause);
        this.extractorType = extractorType;
        this.location = location;
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
     * Returns the positional path to the offending node, for example {@code /window/composite/length}.
     *
     * @return the location
     */
    public String location() {
        return location;
    }
}
