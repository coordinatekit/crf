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

import org.coordinatekit.crf.core.UncheckedCrfException;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Thrown when a service was requested by name but no registered provider of the service type
 * carries that name.
 *
 * <p>
 * The structured accessors let a caller rebuild its own user-facing guidance — listing the names a
 * user could have picked instead — without this class knowing about classpaths or any particular
 * entry point. It mirrors {@link AmbiguousServiceException}, which covers the unnamed case where
 * more than one provider is registered.
 */
@NullMarked
public final class UnknownServiceException extends UncheckedCrfException {
    /**
     * The names of the registered providers of the service type, sorted.
     */
    private final List<String> availableNames;

    /**
     * The name that was requested but matched no provider.
     */
    private final String requestedName;

    /**
     * The human-readable name of the service type.
     */
    private final String serviceName;

    /**
     * Constructs an exception naming the requested service, the name that matched nothing, and the
     * names that were available.
     *
     * @param serviceName the human-readable service name
     * @param requestedName the requested name that matched no provider
     * @param availableNames the names of the registered providers
     */
    public UnknownServiceException(String serviceName, String requestedName, List<String> availableNames) {
        this(availableNames.stream().sorted().toList(), requestedName, serviceName);
    }

    private UnknownServiceException(List<String> availableNames, String requestedName, String serviceName) {
        super(
                "no " + serviceName + " service named \"" + requestedName + "\" is registered; available names: "
                        + (availableNames.isEmpty() ? "(none)" : String.join(", ", availableNames))
        );
        this.availableNames = availableNames;
        this.requestedName = requestedName;
        this.serviceName = serviceName;
    }

    /**
     * Returns the names of the registered providers of the service type, sorted.
     *
     * @return the available names
     */
    public List<String> availableNames() {
        return availableNames;
    }

    /**
     * Returns the requested name that matched no provider.
     *
     * @return the requested name
     */
    public String requestedName() {
        return requestedName;
    }

    /**
     * Returns the human-readable name of the service type.
     *
     * @return the service name
     */
    public String serviceName() {
        return serviceName;
    }
}
