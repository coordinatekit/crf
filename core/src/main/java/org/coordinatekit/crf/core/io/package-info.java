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
 * Input/output components for reading and writing CRF training data.
 *
 * <p>
 * This package provides utilities for working with training data in various formats:
 *
 * <ul>
 * <li>{@link org.coordinatekit.crf.core.io.TrainingDataSequencer} - Reads training data from files
 * into streams of training sequences
 * <li>{@link org.coordinatekit.crf.core.io.XmlTrainingData} - Handles XML-based training data with
 * the CRF namespace
 * <li>{@link org.coordinatekit.crf.core.io.TrainingSchemaGenerator} - Generates XSD schemas from
 * tag providers
 * <li>{@link org.coordinatekit.crf.core.io.TrainingDataValidator} - Validates documents against the
 * structural grammar and the tag vocabulary
 * </ul>
 *
 * <h2>Training Data Format</h2>
 *
 * <p>
 * Training data uses XML with a CRF-specific namespace
 * ({@code https://coordinatekit.org/crf/schema}):
 *
 * <ul>
 * <li>{@code <crf:Sequence>} elements wrap training examples
 * <li>Child elements represent tagged tokens (element name = tag, text content = token)
 * </ul>
 *
 * <h2>Schemas and Namespaces</h2>
 *
 * <p>
 * Two separable concerns drive two schemas. A single XSD document has exactly one target namespace,
 * so they cannot share one file:
 *
 * <ul>
 * <li><b>Structural grammar</b> is fixed, library-owned, and identical for every tag provider. It
 * defines the {@code <crf:Collection>} / {@code <crf:Sequence>} / {@code <crf:Excluded>} shape in
 * the CRF schema namespace ({@code https://coordinatekit.org/crf/schema}) and lives in the static
 * {@code crf-structure.xsd} resource. Tags are referenced through two disjoint wildcards.
 * Namespaced tags (any namespace other than crf) use a strict wildcard, so each needs a matching
 * global declaration and a mistyped tag is rejected. Configuring a target namespace therefore gives
 * a closed, validated vocabulary. No-namespace tags use a lax wildcard, validated against a
 * declaration when present and skipped when absent, so leaving the target namespace unset gives an
 * open vocabulary and lets the structural schema validate standalone.
 * <li><b>Tag vocabulary</b> is dynamic and per-tag-provider. {@code generateSchema} emits one
 * element declaration per tag, in the configured target namespace or in no namespace when none is
 * configured.
 * </ul>
 *
 * <p>
 * The target namespace is optional. When one is configured, the writer declares it as the
 * document's default namespace; when none is, tag elements are left in no namespace. Either way the
 * tag elements stay syntactically bare ({@code <Adjective>Brown</Adjective>}) and the library's own
 * output validates against the schemas it produces.
 * {@link org.coordinatekit.crf.core.io.TrainingDataValidator#validate(java.nio.file.Path)} compiles
 * the structural and tag schemas together and checks a document against both.
 *
 * @see org.coordinatekit.crf.core.io.XmlTrainingData
 * @see org.coordinatekit.crf.core.io.TrainingDataSequencer
 */
@NullMarked
package org.coordinatekit.crf.core.io;

import org.jspecify.annotations.NullMarked;
