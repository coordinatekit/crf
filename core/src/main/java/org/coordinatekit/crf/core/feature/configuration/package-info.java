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
 * Configuration-driven assembly of feature extractors from named, parameterized factories.
 *
 * <p>
 * A downstream project changes which features a model sees by describing a tree of extractors
 * rather than writing Java. Each node names a
 * {@link org.coordinatekit.crf.core.feature.configuration.FeatureExtractorFactory factory} by its
 * {@link org.coordinatekit.crf.core.feature.configuration.FeatureExtractorFactory#type() type} and
 * supplies string-valued parameters; the
 * {@link org.coordinatekit.crf.core.feature.configuration.FeatureExtractorAssembler assembler}
 * validates the parameters against each factory's declared
 * {@link org.coordinatekit.crf.core.feature.configuration.FeatureExtractorFactory#parameters()
 * parameters}, resolves nesting, and produces a single
 * {@link org.coordinatekit.crf.core.feature.FeatureExtractor}.
 *
 * <p>
 * The pieces:
 *
 * <ul>
 * <li>{@link org.coordinatekit.crf.core.feature.configuration.FeatureExtractorNode} - the
 * hand-built tree the assembler consumes (a file parser arrives in a later phase)
 * <li>{@link org.coordinatekit.crf.core.feature.configuration.AssemblyContext} - the ambient state
 * threaded through assembly; where a node says what to build, the context says where the assembler
 * is and how its path parameters resolve
 * <li>{@link org.coordinatekit.crf.core.feature.configuration.FeatureExtractorFactory} - the
 * ServiceLoader SPI, split into
 * {@link org.coordinatekit.crf.core.feature.configuration.LeafFeatureExtractorFactory leaf} and
 * {@link org.coordinatekit.crf.core.feature.configuration.NestingFeatureExtractorFactory nesting}
 * kinds, with an optional {@code validate} seam for a cross-parameter rule its parameters cannot
 * express
 * <li>{@link org.coordinatekit.crf.core.feature.configuration.ParameterDescriptor} - the parameter
 * contract a factory publishes
 * <li>{@link org.coordinatekit.crf.core.feature.configuration.FeatureExtractorParameters} -
 * validated, typed access to a node's parameters, handed to a factory at construction time
 * <li>{@link org.coordinatekit.crf.core.feature.configuration.FeatureExtractorFactoryRegistry} -
 * discovers the factories and indexes them by type
 * </ul>
 *
 * <p>
 * Configuration-content mistakes (an unknown parameter, a missing required value, an uncoercible or
 * out-of-bounds value, an arity violation, a rejected cross-parameter rule) surface as a located
 * {@link org.coordinatekit.crf.core.feature.configuration.FeatureConfigurationException}; a
 * classpath mistake (two factories claiming one type) surfaces as a
 * {@link org.coordinatekit.crf.core.feature.configuration.DuplicateFactoryTypeException}.
 *
 * @see org.coordinatekit.crf.core.feature.configuration.FeatureExtractorAssembler
 * @see org.coordinatekit.crf.core.feature.configuration.FeatureExtractorFactory
 */
@NullMarked
package org.coordinatekit.crf.core.feature.configuration;

import org.jspecify.annotations.NullMarked;
