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
package org.coordinatekit.crf.core.io;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Configuration settings for {@link XmlTrainingData}.
 *
 * <p>
 * This immutable class encapsulates the configurable parameters that control how
 * {@link XmlTrainingData} reads, writes, and generates schemas for training data XML documents. Use
 * the {@link Builder} to construct instances.
 *
 * <p>
 * Example usage:
 *
 * <pre>
 * <code>
 * XmlTrainingDataConfiguration configuration = XmlTrainingDataConfiguration.builder()
 *         .rootElementName("Collection")
 *         .targetNamespace("https://example.org/tags")
 *         .build();
 * </code>
 * </pre>
 *
 * @see XmlTrainingData
 * @see Builder
 */
public final class XmlTrainingDataConfiguration {
    /**
     * The default local name used for the root element of training data documents written by
     * {@link XmlTrainingData}.
     */
    public static final String DEFAULT_ROOT_ELEMENT_NAME = "Collection";

    private static final XmlTrainingDataConfiguration DEFAULTS = builder().build();

    private final String rootElementName;
    private final @Nullable String targetNamespace;

    private XmlTrainingDataConfiguration(Builder builder) {
        this.rootElementName = builder.rootElementName;
        this.targetNamespace = builder.targetNamespace;
    }

    /**
     * Returns a new {@link Builder} instance for constructing a configuration.
     *
     * @return a new builder with default values
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a configuration with all default values.
     *
     * <p>
     * This is equivalent to calling {@code XmlTrainingDataConfiguration.builder().build()}.
     *
     * @return a configuration with default settings
     */
    public static XmlTrainingDataConfiguration defaults() {
        return DEFAULTS;
    }

    /**
     * Returns the local name to use for the root element of training data documents.
     *
     * <p>
     * The root element is emitted in the CRF schema namespace by {@link XmlTrainingData} writers and
     * matched against the same namespace when validating files for append. Defaults to
     * {@link #DEFAULT_ROOT_ELEMENT_NAME}.
     *
     * @return the root element local name
     */
    public String rootElementName() {
        return rootElementName;
    }

    /**
     * Returns the target namespace used for XSD schema generation, or {@code null} if no schema
     * generation is required.
     *
     * <p>
     * When schema generation is invoked via
     * {@link XmlTrainingData#generateSchema(java.io.OutputStream)} this value must be non-null and
     * non-blank.
     *
     * @return the target namespace URI, or {@code null} if unset
     */
    public @Nullable String targetNamespace() {
        return targetNamespace;
    }

    /**
     * Builder for constructing {@link XmlTrainingDataConfiguration} instances.
     *
     * <p>
     * All parameters have sensible defaults.
     */
    @NullMarked
    public static final class Builder {
        private String rootElementName = DEFAULT_ROOT_ELEMENT_NAME;
        private @Nullable String targetNamespace;

        private Builder() {}

        /**
         * Builds the configuration with the current settings.
         *
         * @return an immutable configuration instance
         */
        public XmlTrainingDataConfiguration build() {
            return new XmlTrainingDataConfiguration(this);
        }

        /**
         * Sets the local name to use for the root element of training data documents.
         *
         * @param rootElementName the root element local name, must not be null or blank
         * @return this builder
         * @throws NullPointerException if rootElementName is null
         * @throws IllegalArgumentException if rootElementName is blank
         */
        public Builder rootElementName(String rootElementName) {
            Objects.requireNonNull(rootElementName, "rootElementName may not be null");
            if (rootElementName.isBlank()) {
                throw new IllegalArgumentException("rootElementName may not be blank");
            }

            this.rootElementName = rootElementName;
            return this;
        }

        /**
         * Sets the target namespace used for XSD schema generation.
         *
         * <p>
         * A {@code null} value indicates that schema generation is not configured. Blank values are
         * permitted by the builder but will cause schema generation to fail at use time.
         *
         * @param targetNamespace the target namespace URI, or {@code null} to leave unset
         * @return this builder
         */
        public Builder targetNamespace(@Nullable String targetNamespace) {
            this.targetNamespace = targetNamespace;
            return this;
        }
    }
}
