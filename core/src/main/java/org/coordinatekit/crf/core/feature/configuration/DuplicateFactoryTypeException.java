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

import java.util.List;

/**
 * Thrown when more than one {@link FeatureExtractorFactory} on the classpath declares the same
 * {@link FeatureExtractorFactory#type() type}.
 *
 * <p>
 * This is a classpath/registry error rather than a mistake in a configuration's content: two
 * providers collide on a type before any node is assembled. The structured accessors let a caller
 * rebuild its own guidance — naming the conflicting classes and the duplicated type — without this
 * class knowing about classpaths or any particular entry point, mirroring
 * {@link org.coordinatekit.crf.core.spi.AmbiguousServiceException}.
 */
public final class DuplicateFactoryTypeException extends UncheckedCrfException {
    /**
     * The fully qualified class names of the conflicting factories, sorted.
     */
    private final List<String> factoryClassNames;

    /**
     * The type declared by more than one factory.
     */
    private final String type;

    /**
     * Constructs an exception naming the duplicated type and the conflicting factories.
     *
     * @param type the type declared by more than one factory
     * @param conflictingFactories the factories that declare {@code type}
     */
    public DuplicateFactoryTypeException(String type, List<?> conflictingFactories) {
        this(conflictingFactories.stream().map(factory -> factory.getClass().getName()).sorted().toList(), type);
    }

    private DuplicateFactoryTypeException(List<String> factoryClassNames, String type) {
        super(
                "multiple FeatureExtractorFactory implementations declare type \"" + type + "\": "
                        + String.join(", ", factoryClassNames)
        );
        this.factoryClassNames = factoryClassNames;
        this.type = type;
    }

    /**
     * Returns the fully qualified class names of the conflicting factories, sorted.
     *
     * @return the factory class names
     */
    public List<String> factoryClassNames() {
        return factoryClassNames;
    }

    /**
     * Returns the type declared by more than one factory.
     *
     * @return the duplicated type
     */
    public String type() {
        return type;
    }
}
