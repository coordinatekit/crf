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
 * rather than writing Java, either by hand-building
 * {@link org.coordinatekit.crf.core.feature.configuration.FeatureExtractorNode}s or by writing a
 * file a {@link org.coordinatekit.crf.core.feature.configuration.FeatureConfigurationParser} reads.
 * Each node names a {@link org.coordinatekit.crf.core.feature.configuration.FeatureExtractorFactory
 * factory} by its
 * {@link org.coordinatekit.crf.core.feature.configuration.FeatureExtractorFactory#type() type} and
 * supplies string-valued parameters; the assembler validates the parameters against each factory's
 * declared
 * {@link org.coordinatekit.crf.core.feature.configuration.FeatureExtractorFactory#parameters()
 * parameters}, resolves nesting, and produces a single
 * {@link org.coordinatekit.crf.core.feature.FeatureExtractor}.
 * {@link org.coordinatekit.crf.core.feature.configuration.FeatureConfiguration} is the façade tying
 * a file straight to the assembled extractor.
 *
 * <p>
 * The pieces:
 *
 * <ul>
 * <li>{@link org.coordinatekit.crf.core.feature.configuration.FeatureExtractorNode} - the tree the
 * assembler consumes, built by hand through
 * {@link org.coordinatekit.crf.core.feature.configuration.FeatureExtractorNodes} or by a parser
 * <li>{@link org.coordinatekit.crf.core.feature.configuration.SourceLocation} - the format-neutral
 * file/line/column a parser stamps onto a node, surfaced in located error messages
 * <li>{@link org.coordinatekit.crf.core.feature.configuration.FeatureConfigurationParser} - the
 * ServiceLoader SPI turning a configuration file's bytes into a node tree, and the seam a
 * downstream module implements to teach the assembly a new format. The built-in
 * {@link org.coordinatekit.crf.core.feature.configuration.XmlFeatureConfigurationParser} covers XML
 * on the standard library alone; a format that pulls in its own dependencies, such as JSON or YAML,
 * ships as a separate module rather than joining this one
 * <li>{@link org.coordinatekit.crf.core.feature.configuration.FeatureConfiguration} - the façade
 * from a file straight to an assembled {@link org.coordinatekit.crf.core.feature.FeatureExtractor}
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
 * Two error taxonomies apply at different stages. Before any node reaches the assembler, a
 * <em>syntactic</em> problem in a file's shape (malformed markup, a disallowed {@code DOCTYPE}, a
 * missing required attribute, the wrong number of top-level extractors) surfaces as a located
 * {@link org.coordinatekit.crf.core.feature.configuration.FeatureConfigurationParseException}. Once
 * assembly runs, a <em>content</em> mistake (an unknown parameter, a missing required value, an
 * uncoercible or out-of-bounds value, an arity violation, a rejected cross-parameter rule) surfaces
 * as a located
 * {@link org.coordinatekit.crf.core.feature.configuration.FeatureConfigurationException}. A
 * classpath mistake (two factories claiming one type) surfaces as a
 * {@link org.coordinatekit.crf.core.feature.configuration.DuplicateFactoryTypeException}.
 *
 * @see org.coordinatekit.crf.core.feature.configuration.FeatureConfiguration
 * @see org.coordinatekit.crf.core.feature.configuration.FeatureExtractorFactory
 */
@NullMarked
package org.coordinatekit.crf.core.feature.configuration;

import org.jspecify.annotations.NullMarked;
