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
package org.coordinatekit.crf.core.spi;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Generic {@link ServiceLoader} discovery with the precedence
 * {@code explicit > single discovered provider > fallback}.
 */
@NullMarked
final class ServiceResolution {
    private ServiceResolution() {
        throw new UnsupportedOperationException("ServiceResolution is a utility class");
    }

    /**
     * Loads every registered provider of {@code serviceType}.
     *
     * @param serviceType the service type to discover
     * @param <X> the service type
     * @return the discovered providers, in {@link ServiceLoader} order
     */
    static <X> List<X> discover(Class<X> serviceType) {
        List<X> providers = new ArrayList<>();
        ServiceLoader.load(serviceType).forEach(providers::add);
        return providers;
    }

    /**
     * Discovers {@code serviceType} then resolves, naming the slot from the type's simple name.
     *
     * @param serviceType the service type to discover and resolve
     * @param explicit the explicitly supplied provider, or {@code null} if none was set
     * @param fallback the built-in default, or {@code null} if the slot has none
     * @param <X> the service type
     * @return the resolved provider, or {@code null} if there was no explicit provider, no discovered
     *         provider, and no fallback
     * @throws AmbiguousServiceException if more than one provider is discovered and none is explicit
     */
    static <X> @Nullable X resolve(Class<X> serviceType, @Nullable X explicit, @Nullable X fallback) {
        return resolve(serviceType, explicit, discover(serviceType), fallback);
    }

    /**
     * List-based form, kept separate for unit testing with synthetic providers.
     *
     * @param serviceType the service type, used to identify the slot in the ambiguity error
     * @param explicit the explicitly supplied provider, or {@code null} if none was set
     * @param discovered the providers discovered through {@link ServiceLoader}
     * @param fallback the built-in default, or {@code null} if the slot has none
     * @param <X> the service type
     * @return the resolved provider, or {@code null} if there was no explicit provider, no discovered
     *         provider, and no fallback
     * @throws AmbiguousServiceException if more than one provider is discovered and none is explicit
     */
    static <X> @Nullable X resolve(
            Class<?> serviceType,
            @Nullable X explicit,
            List<X> discovered,
            @Nullable X fallback
    ) {
        if (explicit != null) {
            return explicit;
        }
        if (discovered.size() > 1) {
            throw new AmbiguousServiceException(serviceType, discovered);
        }
        if (!discovered.isEmpty()) {
            return discovered.getFirst();
        }
        return fallback;
    }
}
