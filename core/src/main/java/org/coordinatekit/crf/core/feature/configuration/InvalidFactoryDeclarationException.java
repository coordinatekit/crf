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
 * Thrown when a {@link FeatureExtractorFactory} declares an inconsistent kind or arity contract: it
 * implements neither or both of {@link LeafFeatureExtractorFactory} and
 * {@link NestingFeatureExtractorFactory}, or, for a {@link NestingFeatureExtractorFactory}, its
 * declared child bounds are not sane, or it declares two parameters with the same name.
 *
 * <p>
 * This is a classpath/registry error rather than a mistake in a configuration's content: a
 * factory's contract is inconsistent before any node is assembled. The structured accessors let a
 * caller rebuild its own guidance — naming the offending class and its declared type — without this
 * class knowing about classpaths or any particular entry point, mirroring
 * {@link DuplicateFactoryTypeException}.
 */
public final class InvalidFactoryDeclarationException extends UncheckedCrfException {
    /**
     * The fully qualified class name of the offending factory.
     */
    private final String factoryClassName;

    /**
     * The type declared by the offending factory.
     */
    private final String type;

    /**
     * Constructs an exception naming the offending factory and describing how its declaration is
     * inconsistent.
     *
     * @param factory the factory whose kind/arity declaration is inconsistent
     * @param detail the description of the inconsistency
     */
    public InvalidFactoryDeclarationException(FeatureExtractorFactory factory, String detail) {
        this(factory.type(), factory.getClass().getName(), detail);
    }

    private InvalidFactoryDeclarationException(String type, String factoryClassName, String detail) {
        super("factory '" + type + "' (" + factoryClassName + ") " + detail);
        this.type = type;
        this.factoryClassName = factoryClassName;
    }

    /**
     * Returns the fully qualified class name of the offending factory.
     *
     * @return the factory class name
     */
    public String factoryClassName() {
        return factoryClassName;
    }

    /**
     * Returns the type declared by the offending factory.
     *
     * @return the factory type
     */
    public String type() {
        return type;
    }
}
