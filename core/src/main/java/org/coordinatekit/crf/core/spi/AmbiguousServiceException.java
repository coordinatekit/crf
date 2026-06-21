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
import java.util.stream.Collectors;

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
    private final List<String> implementationNames;
    private final String serviceName;

    /**
     * Constructs an exception naming the ambiguous service and its registered implementations.
     *
     * @param serviceName the human-readable service name
     * @param implementations the discovered provider instances
     */
    public AmbiguousServiceException(String serviceName, List<?> implementations) {
        super("multiple " + serviceName + " service implementations are registered: " + classNames(implementations));
        this.implementationNames = implementations.stream().map(implementation -> implementation.getClass().getName())
                .sorted().toList();
        this.serviceName = serviceName;
    }

    private static String classNames(List<?> implementations) {
        return implementations.stream().map(implementation -> implementation.getClass().getName()).sorted()
                .collect(Collectors.joining(", "));
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
     * Returns the human-readable name of the ambiguous service.
     *
     * @return the service name
     */
    public String serviceName() {
        return serviceName;
    }
}
