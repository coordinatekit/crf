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

import java.nio.file.Path;

/**
 * A read-only view of the ambient state threaded through assembly. Where a
 * {@link FeatureExtractorNode} carries what to build, the context carries where the assembler is
 * and how its paths resolve: the {@link #baseDirectory()} that {@link ParameterKind#PATH path}
 * parameters resolve against, and the {@link #location()} used in located error messages. The base
 * directory comes from the caller, not from the configuration.
 *
 * <p>
 * Construction and advancement are internal to the assembler; this interface exists only to hand
 * the current position to the factory SPI.
 */
public interface AssemblyContext {
    /**
     * Returns the directory that {@link ParameterKind#PATH} parameters resolve against.
     *
     * @return the base directory
     */
    Path baseDirectory();

    /**
     * Returns the positional path to the current node, for example {@code /window/composite/length}.
     *
     * @return the location
     */
    String location();
}
