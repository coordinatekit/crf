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
/**
 * Service-provider discovery for the CRF domain SPIs.
 *
 * <p>
 * This package locates the providers an entry point needs through {@link java.util.ServiceLoader}
 * and applies each slot's canonical default:
 *
 * <ul>
 * <li>{@link org.coordinatekit.crf.core.spi.CrfServices} - discovers the CRF domain SPIs
 * (tokenizer, full and key feature extractors, tag provider, tagger loader), binding each to its
 * built-in default
 * <li>{@link org.coordinatekit.crf.core.spi.AmbiguousServiceException} - thrown when more than one
 * provider of a service type is registered and none was supplied explicitly
 * </ul>
 *
 * <p>
 * The generic discovery kernel resolves a slot by the precedence
 * {@code explicit > single discovered provider > fallback}; it stays package-private and is reached
 * through {@link org.coordinatekit.crf.core.spi.CrfServices}.
 *
 * @see org.coordinatekit.crf.core.spi.CrfServices
 */
@NullMarked
package org.coordinatekit.crf.core.spi;

import org.jspecify.annotations.NullMarked;
