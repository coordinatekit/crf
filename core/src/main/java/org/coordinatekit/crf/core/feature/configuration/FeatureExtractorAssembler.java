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

import org.coordinatekit.crf.core.feature.FeatureExtractor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Assembles a tree of {@link FeatureExtractorNode}s into a single {@link FeatureExtractor}.
 *
 * <p>
 * Assembly is depth-first: each node's children are built before the node itself, so a nesting
 * factory receives already-assembled children. For every node the assembler looks up the factory by
 * {@link FeatureExtractorNode#type() type}, validates the node's parameters against the factory's
 * {@link FeatureExtractorFactory#parameters() parameters}, checks that the child count is within
 * the declared arity, calls the factory's
 * {@link FeatureExtractorFactory#validate(FeatureExtractorParameters, AssemblyContext) validate}
 * for any cross-parameter rule the parameters alone cannot express, then dispatches on whether the
 * factory is a {@link LeafFeatureExtractorFactory} or a {@link NestingFeatureExtractorFactory}. A
 * configuration-content problem — an unknown type, an invalid parameter, an arity violation, a
 * rejected {@code validate}, or nesting past {@link #MAXIMUM_ASSEMBLY_DEPTH} — surfaces as a
 * located {@link FeatureConfigurationException}.
 *
 * <p>
 * The {@link FeatureExtractorNode#key() key} marker is not consulted; this assembler returns only
 * the full extractor. Splitting the tree into a key/full pair arrives in a later phase.
 */
public final class FeatureExtractorAssembler {
    /**
     * The deepest nesting the assembler will follow before rejecting a tree, a guard against a
     * pathologically or maliciously deep configuration.
     */
    static final int MAXIMUM_ASSEMBLY_DEPTH = 64;

    private final FeatureExtractorFactoryRegistry registry;

    /**
     * Creates an assembler that draws factories from the given registry.
     *
     * @param registry the factory registry
     */
    public FeatureExtractorAssembler(FeatureExtractorFactoryRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    /**
     * Assembles the tree rooted at {@code root} into a single feature extractor.
     *
     * @param root the root node of the tree
     * @param baseDirectory the directory that path parameters resolve against
     * @return the assembled feature extractor
     * @throws FeatureConfigurationException if any node names an unknown type, carries an invalid
     *         parameter, violates its declared arity, fails its factory's {@code validate}, or the tree
     *         is deeper than {@link #MAXIMUM_ASSEMBLY_DEPTH}
     */
    public FeatureExtractor assemble(FeatureExtractorNode root, Path baseDirectory) {
        Objects.requireNonNull(root, "root must not be null");
        Objects.requireNonNull(baseDirectory, "baseDirectory must not be null");
        return assemble(root, DefaultAssemblyContext.root(baseDirectory), 1);
    }

    /**
     * Assembles one node at the given depth, recursing into its children first.
     *
     * @param node the node to assemble
     * @param parentContext the context of the parent node
     * @param depth the one-based depth of this node
     * @return the assembled feature extractor for this node
     * @throws FeatureConfigurationException on any located configuration-content problem
     */
    private FeatureExtractor assemble(FeatureExtractorNode node, DefaultAssemblyContext parentContext, int depth) {
        DefaultAssemblyContext context = parentContext.descend(node.type());
        if (depth > MAXIMUM_ASSEMBLY_DEPTH) {
            throw new FeatureConfigurationException(
                    node.type(),
                    context.location(),
                    "nesting is deeper than the maximum of " + MAXIMUM_ASSEMBLY_DEPTH
            );
        }

        FeatureExtractorFactory factory = registry.find(node.type()).orElseThrow(
                () -> new FeatureConfigurationException(
                        node.type(),
                        context.location(),
                        "unknown extractor type '" + node.type() + "'"
                )
        );
        FeatureExtractorParameters parameters = ParameterValidation
                .validate(node.type(), node.parameters(), factory.parameters(), context);

        int childCount = node.children().size();
        if (factory instanceof LeafFeatureExtractorFactory && childCount != 0) {
            throw new FeatureConfigurationException(node.type(), context.location(), arityMessage(0, 0, childCount));
        }
        if (factory instanceof NestingFeatureExtractorFactory nesting) {
            int minimumChildren = nesting.minimumChildren();
            int maximumChildren = nesting.maximumChildren();
            if (childCount < minimumChildren || childCount > maximumChildren) {
                throw new FeatureConfigurationException(
                        node.type(),
                        context.location(),
                        arityMessage(minimumChildren, maximumChildren, childCount)
                );
            }
        }

        try {
            factory.validate(parameters, context);
        } catch (IllegalArgumentException exception) {
            throw new FeatureConfigurationException(
                    node.type(),
                    context.location(),
                    Objects.requireNonNullElse(exception.getMessage(), exception.toString()),
                    exception
            );
        }

        List<FeatureExtractor> children = new ArrayList<>();
        for (FeatureExtractorNode child : node.children()) {
            children.add(assemble(child, context, depth + 1));
        }

        if (factory instanceof LeafFeatureExtractorFactory leaf) {
            return leaf.create(parameters);
        }
        if (factory instanceof NestingFeatureExtractorFactory nesting) {
            return nesting.create(parameters, List.copyOf(children));
        }

        // Unreachable due to validation in FeatureExtractorFactoryRegistry
        throw new IllegalStateException(
                "factory for type '" + node.type() + "' (" + factory.getClass().getName()
                        + ") is neither a LeafFeatureExtractorFactory nor a NestingFeatureExtractorFactory"
        );
    }

    /**
     * Describes an arity violation in terms a downstream can show a user.
     *
     * @param minimum the minimum number of children the factory requires
     * @param maximum the maximum number of children the factory accepts
     * @param childCount the number of children the node actually had
     * @return the human-readable description
     */
    private static String arityMessage(int minimum, int maximum, int childCount) {
        String expected;
        if (minimum == 0 && maximum == 0) {
            expected = "no children";
        } else if (minimum == maximum) {
            expected = "exactly " + minimum + (minimum == 1 ? " child" : " children");
        } else if (maximum == Integer.MAX_VALUE) {
            expected = "at least " + minimum + (minimum == 1 ? " child" : " children");
        } else {
            expected = "between " + minimum + " and " + maximum + " children";
        }
        return "expected " + expected + " but got " + childCount;
    }
}
