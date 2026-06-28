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
 * Thrown when more than one provider of a service type is registered and none was supplied
 * explicitly.
 *
 * <p>
 * The structured accessors let a caller rebuild its own user-facing guidance without this class
 * knowing about classpaths or any particular entry point.
 */
@NullMarked
public final class AmbiguousServiceException extends UncheckedCrfException {
    /**
     * The fully qualified class names of the registered implementations, sorted.
     */
    private final List<String> implementationNames;

    /**
     * The type of the ambiguous service; the authoritative identity callers branch on, and the source
     * of the human-readable {@link #serviceName()}.
     */
    private final Class<?> serviceType;

    /**
     * Constructs an exception naming the ambiguous service and its registered implementations.
     *
     * @param serviceType the ambiguous service type
     * @param implementations the discovered provider instances
     */
    public AmbiguousServiceException(Class<?> serviceType, List<?> implementations) {
        this(
                implementations.stream().map(implementation -> implementation.getClass().getName()).sorted().toList(),
                serviceType
        );
    }

    private AmbiguousServiceException(List<String> implementationNames, Class<?> serviceType) {
        super(
                "multiple " + serviceType.getSimpleName() + " service implementations are registered: "
                        + String.join(", ", implementationNames)
        );
        this.implementationNames = implementationNames;
        this.serviceType = serviceType;
    }

    /**
     * Returns the fully qualified class names of the registered implementations, sorted.
     *
     * @return the implementation class names
     */
    public List<String> implementationNames() {
        return implementationNames;
    }

    /**
     * Returns the human-readable name of the ambiguous service, derived from {@link #serviceType()}.
     *
     * @return the service name
     */
    public String serviceName() {
        return serviceType.getSimpleName();
    }

    /**
     * Returns the type of the ambiguous service, the authoritative identity for callers that tailor
     * guidance per slot.
     *
     * @return the service type
     */
    public Class<?> serviceType() {
        return serviceType;
    }
}
