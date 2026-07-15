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

import org.jspecify.annotations.Nullable;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Assembles a tree of {@link FeatureExtractorNode}s into a full/key pair of
 * {@link FeatureExtractor}s, returned as an {@link AssembledFeatureExtractors}.
 *
 * <p>
 * Assembly is depth-first: each node's children are built before the node itself, so a nesting
 * factory receives already-assembled children. For every node the assembler looks up the factory by
 * {@link FeatureExtractorNode#type() type}, validates the node's parameters against the factory's
 * {@link FeatureExtractorFactory#parameters() parameters}, checks that the child count is within
 * the declared arity, calls the factory's
 * {@link FeatureExtractorFactory#validate(FeatureExtractorParameters) validate} for any
 * cross-parameter rule the parameters alone cannot express, then dispatches on whether the factory
 * is a {@link LeafFeatureExtractorFactory} or a {@link NestingFeatureExtractorFactory}. A
 * configuration-content problem — an unknown type, an invalid parameter, an arity violation, a
 * rejected {@code validate}, a rejected {@code create}, or nesting past
 * {@link FeatureExtractorNode#MAXIMUM_NESTING_DEPTH} — surfaces as a located
 * {@link FeatureConfigurationException}.
 *
 * <p>
 * At most one node in the tree may carry the {@link FeatureExtractorNode#key() key} marker. Because
 * assembly is post-order and a marked node's extractor is the same instance its parent wraps, the
 * captured key extractor is the shared subtree instance, not a rebuilt copy; when no node is
 * marked, no key extractor is produced. A second marked node surfaces as a located
 * {@link FeatureConfigurationException}.
 */
final class FeatureExtractorAssembler {
    private final FeatureExtractorFactoryRegistry registry;

    /**
     * Creates an assembler that draws factories from the given registry.
     *
     * @param registry the factory registry
     */
    FeatureExtractorAssembler(FeatureExtractorFactoryRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    /**
     * Assembles the tree rooted at {@code root} into a full/key pair of feature extractors.
     *
     * @param root the root node of the tree
     * @param baseLocation the document location that resource parameters resolve against
     * @return the assembled full and key feature extractors
     * @throws FeatureConfigurationException if any node names an unknown type, carries an invalid
     *         parameter, violates its declared arity, fails its factory's {@code validate} or
     *         {@code create}, the tree is deeper than
     *         {@link FeatureExtractorNode#MAXIMUM_NESTING_DEPTH}, or more than one node carries the
     *         {@link FeatureExtractorNode#key() key} marker
     */
    AssembledFeatureExtractors assemble(FeatureExtractorNode root, URL baseLocation) {
        Objects.requireNonNull(root, "root must not be null");
        Objects.requireNonNull(baseLocation, "baseLocation must not be null");
        // `keyHolder` is a mutable slot shared across the recursion's stack frames. The key-marked
        // node can sit anywhere in the tree, so its exact extractor instance has to climb back out to
        // this top-level call, and a shared array is how one value does that. A filled slot also means
        // a key node was already captured, so a second marker is a duplicate.
        FeatureExtractor[] keyHolder = new FeatureExtractor[1];
        FeatureExtractor full = assemble(root, baseLocation, 1, keyHolder);
        return AssembledFeatureExtractors.of(full, keyHolder[0]);
    }

    /**
     * Assembles one node at the given depth, recursing into its children first.
     *
     * @param node the node to assemble
     * @param baseLocation the document location that resource parameters resolve against
     * @param depth the one-based depth of this node
     * @param keyHolder a single-slot holder capturing the key-marked node's extractor, empty until a
     *        marked node is assembled
     * @return the assembled feature extractor for this node
     * @throws FeatureConfigurationException on any located configuration-content problem, including a
     *         second node carrying the {@link FeatureExtractorNode#key() key} marker
     */
    private FeatureExtractor assemble(
            FeatureExtractorNode node,
            URL baseLocation,
            int depth,
            @Nullable FeatureExtractor[] keyHolder
    ) {
        SourceLocation source = node.sourceLocation().orElse(null);
        if (depth > FeatureExtractorNode.MAXIMUM_NESTING_DEPTH) {
            throw new FeatureConfigurationException(
                    node.type(),
                    source,
                    "nesting is deeper than the maximum of " + FeatureExtractorNode.MAXIMUM_NESTING_DEPTH
            );
        }

        FeatureExtractorFactory factory = registry.find(node.type()).orElseThrow(
                () -> new FeatureConfigurationException(
                        node.type(),
                        source,
                        "unknown extractor type '" + node.type() + "'"
                )
        );
        FeatureExtractorParameters parameters = ParameterValidation
                .validate(node.type(), node.parameters(), factory.parameters(), baseLocation, source);

        int childCount = node.children().size();
        if (factory instanceof LeafFeatureExtractorFactory && childCount != 0) {
            throw new FeatureConfigurationException(node.type(), source, arityMessage(0, 0, childCount));
        }
        if (factory instanceof NestingFeatureExtractorFactory nesting) {
            int minimumChildren = nesting.minimumChildren();
            int maximumChildren = nesting.maximumChildren();
            if (childCount < minimumChildren || childCount > maximumChildren) {
                throw new FeatureConfigurationException(
                        node.type(),
                        source,
                        arityMessage(minimumChildren, maximumChildren, childCount)
                );
            }
        }

        try {
            factory.validate(parameters);
        } catch (IllegalArgumentException exception) {
            throw located(node.type(), source, exception);
        }

        List<FeatureExtractor> children = new ArrayList<>();
        for (FeatureExtractorNode child : node.children()) {
            children.add(assemble(child, baseLocation, depth + 1, keyHolder));
        }

        FeatureExtractor extractor;
        try {
            if (factory instanceof LeafFeatureExtractorFactory leaf) {
                extractor = leaf.create(parameters);
            } else if (factory instanceof NestingFeatureExtractorFactory nesting) {
                extractor = nesting.create(parameters, List.copyOf(children));
            } else {
                // Unreachable due to validation in FeatureExtractorFactoryRegistry
                throw new IllegalStateException(
                        "factory for type '" + node.type() + "' (" + factory.getClass().getName()
                                + ") is neither a LeafFeatureExtractorFactory nor a NestingFeatureExtractorFactory"
                );
            }
        } catch (IllegalArgumentException exception) {
            throw located(node.type(), source, exception);
        }

        if (node.key()) {
            if (keyHolder[0] != null) {
                throw new FeatureConfigurationException(node.type(), source, "at most one node may be marked key");
            }
            keyHolder[0] = extractor;
        }
        return extractor;
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

    /**
     * Wraps a factory's content rejection into a located {@link FeatureConfigurationException}.
     *
     * @param type the extractor type reported by the rejecting node
     * @param source the node's source location, or {@code null} if unknown
     * @param exception the rejection thrown by the factory's {@code validate} or {@code create}
     * @return the located exception
     */
    private static FeatureConfigurationException located(
            String type,
            @Nullable SourceLocation source,
            IllegalArgumentException exception
    ) {
        return new FeatureConfigurationException(
                type,
                source,
                Objects.requireNonNullElse(exception.getMessage(), exception.toString()),
                exception
        );
    }
}
