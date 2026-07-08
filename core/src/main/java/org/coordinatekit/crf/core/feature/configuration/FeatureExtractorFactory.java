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

import java.util.Set;

/**
 * The ServiceLoader SPI a configuration node names to build a feature extractor.
 *
 * <p>
 * A factory publishes a stable {@link #type()} — the token a node carries to select it — and the
 * {@link #parameters()} it accepts, and may override {@link #validate(FeatureExtractorParameters)}
 * to reject a configuration the parameters alone cannot. Every factory is one of two kinds: a
 * {@link LeafFeatureExtractorFactory} that takes no children, or a
 * {@link NestingFeatureExtractorFactory} that composes children and declares its own child arity.
 * This root interface is <strong>not sealed</strong>: a downstream registers its own factories
 * through {@code META-INF/services}, and the assembler dispatches on which sub-interface a factory
 * implements. {@link FeatureExtractorFactoryRegistry} enforces at construction that a factory
 * implements exactly one of the two sub-interfaces, rejecting a mismatch with an
 * {@link InvalidFactoryDeclarationException} rather than letting it surface later, mid-assembly.
 *
 * @see LeafFeatureExtractorFactory
 * @see NestingFeatureExtractorFactory
 * @see FeatureExtractorFactoryRegistry
 */
public interface FeatureExtractorFactory {
    /**
     * Returns the parameters this factory accepts. Names must be unique within a factory.
     *
     * @return the declared parameters
     */
    default Set<ParameterDescriptor> parameters() {
        return Set.of();
    }

    /**
     * Returns the stable type token that selects this factory from a configuration node.
     *
     * @return the factory type
     */
    String type();

    /**
     * Validates a cross-parameter or richer value rule the per-parameter {@link ParameterDescriptor}
     * cannot express.
     *
     * <p>
     * The default implementation accepts every configuration. Override to reject one, throwing an
     * {@link IllegalArgumentException} describing the problem; the assembler calls this after
     * per-parameter validation and the arity check, before recursing into children, and wraps a thrown
     * {@link IllegalArgumentException} into a located {@link FeatureConfigurationException} naming this
     * node. Use this for a cheap cross-parameter or value check that per-parameter
     * {@link ParameterDescriptor}s cannot express. A check that is only affordable while actually
     * building the extractor — parsing a dictionary to prove it is well-formed XML, for example — need
     * not be duplicated here: {@code create()} may itself throw {@link IllegalArgumentException} to
     * report a content problem, and the assembler locates it exactly as it does one thrown from here.
     *
     * @param parameters the validated, coerced parameters
     * @throws IllegalArgumentException if the configuration violates a cross-parameter rule
     */
    default void validate(FeatureExtractorParameters parameters) {}
}
