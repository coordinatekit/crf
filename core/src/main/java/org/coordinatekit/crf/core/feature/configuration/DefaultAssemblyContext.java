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
import java.util.Objects;

/**
 * The package-private record implementing {@link AssemblyContext}, created through
 * {@link #root(Path)} and extended through {@link #descend(String)}.
 *
 * <p>
 * {@code location} holds the accumulated path without its leading slash — the empty string at the
 * root — so {@link #location()} presents {@code /} for the root and {@code /window/composite} once
 * descended. This keeps {@link #descend(String)} a plain append.
 *
 * @param baseDirectory the base directory
 * @param location the accumulated path, without its leading slash
 */
record DefaultAssemblyContext(Path baseDirectory, String location) implements AssemblyContext {
    /**
     * Returns a child context for a nested node of the given type, extending this context's location by
     * that type.
     *
     * @param type the type of the nested node
     * @return the child context
     */
    DefaultAssemblyContext descend(String type) {
        Objects.requireNonNull(type, "type must not be null");
        return new DefaultAssemblyContext(baseDirectory, location + "/" + type);
    }

    @Override
    public String location() {
        return location.isEmpty() ? "/" : location;
    }

    /**
     * Creates a root context resolving paths against the given base directory, at location {@code /}.
     *
     * @param baseDirectory the directory that path parameters resolve against
     * @return a new root context
     */
    static DefaultAssemblyContext root(Path baseDirectory) {
        return new DefaultAssemblyContext(Objects.requireNonNull(baseDirectory, "baseDirectory must not be null"), "");
    }
}
