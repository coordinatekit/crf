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

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One node of the tree the assembler consumes: a factory {@link #type()}, the raw string
 * {@link #parameters()} for that factory, and the {@link #children()} to nest under it.
 */
public interface FeatureExtractorNode {
    /**
     * The deepest a configuration tree may nest before it is rejected, a guard against a pathologically
     * or maliciously deep configuration.
     *
     * <p>
     * The assembler enforces this as a hard backstop: it rejects any tree — however built — that nests
     * deeper than this, so the invariant holds regardless of how the tree was produced. A
     * {@link FeatureConfigurationParser} implementation should mirror the same limit in its own
     * recursion so that a too-deep source fails fast with a located
     * {@link FeatureConfigurationParseException} at parse time, rather than overflowing the stack
     * before the backstop can run.
     */
    int MAXIMUM_NESTING_DEPTH = 64;

    /**
     * Returns the children nested under this node, in order.
     *
     * @return the child nodes
     */
    List<FeatureExtractorNode> children();

    /**
     * Returns whether this node is marked as the key boundary. Carried but not consumed by the current
     * assembler.
     *
     * @return {@code true} if this node is the key boundary
     */
    boolean key();

    /**
     * Returns the raw, unvalidated parameters for the factory, keyed by parameter name.
     *
     * @return the raw parameters
     */
    Map<String, String> parameters();

    /**
     * Returns where this node came from in its source configuration, empty for a hand-built node.
     *
     * @return the source location, or empty
     */
    Optional<SourceLocation> sourceLocation();

    /**
     * Returns the factory type that selects which factory builds this node.
     *
     * @return the factory type
     */
    String type();
}
