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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * The factories available to the {@link FeatureExtractorAssembler}, indexed by
 * {@link FeatureExtractorFactory#type() type}.
 *
 * <p>
 * {@link #load()} loads every registered {@link FeatureExtractorFactory} through
 * {@link ServiceLoader}; {@link #find(String)} looks one up by type. Two factories claiming the
 * same type is a classpath mistake and fails fast with a {@link DuplicateFactoryTypeException}; a
 * factory whose kind and declared child bounds are inconsistent, or that declares two parameters
 * with the same name, fails fast with an {@link InvalidFactoryDeclarationException}.
 */
public final class FeatureExtractorFactoryRegistry {
    private final Map<String, FeatureExtractorFactory> factoriesByType;

    private FeatureExtractorFactoryRegistry(Map<String, FeatureExtractorFactory> factoriesByType) {
        this.factoriesByType = factoriesByType;
    }

    /**
     * Loads every registered {@link FeatureExtractorFactory} through {@link ServiceLoader} and indexes
     * them by type.
     *
     * @return a registry of the loaded factories
     * @throws DuplicateFactoryTypeException if two loaded factories declare the same type
     * @throws InvalidFactoryDeclarationException if a loaded factory's kind and declared child bounds
     *         are inconsistent, or it declares two parameters with the same name
     */
    public static FeatureExtractorFactoryRegistry load() {
        List<FeatureExtractorFactory> factories = new ArrayList<>();
        ServiceLoader.load(FeatureExtractorFactory.class).forEach(factories::add);
        return of(factories);
    }

    /**
     * Indexes the given factories by type, the discovery-free core kept separate for unit testing with
     * synthetic providers.
     *
     * @param factories the factories to index
     * @return a registry of the given factories
     * @throws DuplicateFactoryTypeException if two factories declare the same type
     * @throws InvalidFactoryDeclarationException if a factory's kind and declared child bounds are
     *         inconsistent, or it declares two parameters with the same name
     */
    static FeatureExtractorFactoryRegistry of(List<FeatureExtractorFactory> factories) {
        Map<String, FeatureExtractorFactory> byType = new LinkedHashMap<>();
        for (FeatureExtractorFactory factory : factories) {
            validateDeclaration(factory);
            FeatureExtractorFactory existing = byType.putIfAbsent(factory.type(), factory);
            if (existing != null) {
                throw new DuplicateFactoryTypeException(factory.type(), List.of(existing, factory));
            }
        }
        return new FeatureExtractorFactoryRegistry(Map.copyOf(byType));
    }

    /**
     * Validates that a factory implements exactly one of {@link LeafFeatureExtractorFactory} /
     * {@link NestingFeatureExtractorFactory}, that, for a nesting factory, its declared child bounds
     * are sane, and that its declared parameters carry no duplicate name.
     *
     * @param factory the factory to validate
     * @throws InvalidFactoryDeclarationException if the factory's kind and declared child bounds are
     *         inconsistent, or it declares two parameters with the same name
     */
    private static void validateDeclaration(FeatureExtractorFactory factory) {
        boolean leaf = factory instanceof LeafFeatureExtractorFactory;
        boolean nesting = factory instanceof NestingFeatureExtractorFactory;
        if (leaf && nesting) {
            throw new InvalidFactoryDeclarationException(
                    factory,
                    "must implement only one of LeafFeatureExtractorFactory or NestingFeatureExtractorFactory"
            );
        }
        if (!leaf && !nesting) {
            throw new InvalidFactoryDeclarationException(
                    factory,
                    "must implement LeafFeatureExtractorFactory or NestingFeatureExtractorFactory"
            );
        }
        if (nesting) {
            NestingFeatureExtractorFactory nestingFactory = (NestingFeatureExtractorFactory) factory;
            int minimumChildren = nestingFactory.minimumChildren();
            int maximumChildren = nestingFactory.maximumChildren();
            if (minimumChildren < 0) {
                throw new InvalidFactoryDeclarationException(
                        factory,
                        "declares minimum children that must be non-negative, got: " + minimumChildren
                );
            }
            if (maximumChildren < minimumChildren) {
                throw new InvalidFactoryDeclarationException(
                        factory,
                        "declares maximum children (" + maximumChildren
                                + ") that must not be less than minimum children (" + minimumChildren + ")"
                );
            }
            if (minimumChildren < 1) {
                throw new InvalidFactoryDeclarationException(
                        factory,
                        "is a nesting factory but declares a minimum children of " + minimumChildren
                                + "; a nesting factory must declare at least one child"
                );
            }
        }
        Set<String> seenNames = new HashSet<>();
        for (ParameterDescriptor parameter : factory.parameters()) {
            if (!seenNames.add(parameter.name())) {
                throw new InvalidFactoryDeclarationException(
                        factory,
                        "declares duplicate parameter '" + parameter.name() + "'"
                );
            }
        }
    }

    /**
     * Returns the factory registered for {@code type}, or empty if none is.
     *
     * @param type the factory type
     * @return the factory, or empty if none is registered for the type
     */
    public Optional<FeatureExtractorFactory> find(String type) {
        return Optional.ofNullable(factoriesByType.get(type));
    }
}
