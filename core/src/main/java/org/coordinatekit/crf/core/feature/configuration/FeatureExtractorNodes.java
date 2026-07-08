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

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds {@link FeatureExtractorNode} trees. A {@link FeatureConfigurationParser} builds nodes
 * through this, and it remains the ergonomic way to hand-build a tree in a test.
 */
public final class FeatureExtractorNodes {
    private FeatureExtractorNodes() {}

    /**
     * Creates a builder for a node of the given factory type.
     *
     * @param type the factory type
     * @return a new builder
     */
    public static Builder builder(String type) {
        return new Builder(type);
    }

    /**
     * Builder for {@link FeatureExtractorNode}, ergonomic for hand-built trees and the parser alike.
     */
    public static final class Builder {
        private final List<FeatureExtractorNode> children = new ArrayList<>();
        private boolean key = false;
        private final Map<String, String> parameters = new LinkedHashMap<>();
        private @Nullable SourceLocation sourceLocation;
        private final String type;

        private Builder(String type) {
            this.type = Objects.requireNonNull(type, "type must not be null");
        }

        /**
         * Builds the node.
         *
         * @return a new {@link FeatureExtractorNode}
         */
        public FeatureExtractorNode build() {
            return new DefaultFeatureExtractorNode(
                    List.copyOf(children),
                    key,
                    Map.copyOf(parameters),
                    sourceLocation,
                    type
            );
        }

        /**
         * Adds a child node.
         *
         * @param child the child node
         * @return this builder
         */
        public Builder child(FeatureExtractorNode child) {
            this.children.add(Objects.requireNonNull(child, "child must not be null"));
            return this;
        }

        /**
         * Marks whether this node is the key boundary. Defaults to {@code false}.
         *
         * @param key {@code true} to mark this node as the key boundary
         * @return this builder
         */
        public Builder key(boolean key) {
            this.key = key;
            return this;
        }

        /**
         * Sets a raw parameter value.
         *
         * @param name the parameter name
         * @param value the raw parameter value
         * @return this builder
         */
        public Builder parameter(String name, String value) {
            this.parameters.put(
                    Objects.requireNonNull(name, "name must not be null"),
                    Objects.requireNonNull(value, "value must not be null")
            );
            return this;
        }

        /**
         * Sets where this node came from in its source configuration.
         *
         * @param sourceLocation the source location
         * @return this builder
         */
        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = Objects.requireNonNull(sourceLocation, "sourceLocation must not be null");
            return this;
        }
    }

    /**
     * The private record implementing {@link FeatureExtractorNode}, built exclusively through
     * {@link Builder#build()}.
     *
     * @param children the child nodes
     * @param key whether this node is the key boundary
     * @param parameters the raw parameters
     * @param source where this node came from in its source configuration, empty for a hand-built node
     * @param type the factory type
     */
    private record DefaultFeatureExtractorNode(
            List<FeatureExtractorNode> children,
            boolean key,
            Map<String, String> parameters,
            @Nullable SourceLocation source,
            String type
    ) implements FeatureExtractorNode {
        @Override
        public Optional<SourceLocation> sourceLocation() {
            return Optional.ofNullable(source);
        }
    }
}
