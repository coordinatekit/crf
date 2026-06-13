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
 * Two separable concerns drive two schemas in two namespaces. A single XSD document has exactly one
 * target namespace, so they cannot share one file:
 *
 * <ul>
 * <li><b>Structural grammar</b> — fixed, library-owned, and identical for every tag provider. It
 * defines the {@code <crf:Collection>} / {@code <crf:Sequence>} / {@code <crf:Excluded>} shape in
 * the CRF schema namespace ({@code https://coordinatekit.org/crf/schema}) and lives in the static
 * {@code crf-structure.xsd} resource. Tags are referenced through a strict wildcard, so each tag
 * element needs a matching global declaration.
 * <li><b>Tag vocabulary</b> — dynamic and per-tag-provider. {@code generateSchema} emits one
 * element declaration per tag in the user's configured target namespace.
 * </ul>
 *
 * <p>
 * When a target namespace is configured, the writer declares it as the document's default
 * namespace. Tag elements stay syntactically bare ({@code <Adjective>Brown</Adjective>}) but
 * resolve into the user namespace, so the library's own output validates against the schemas it
 * produces.
 * {@link org.coordinatekit.crf.core.io.TrainingDataValidator#validate(java.nio.file.Path)} compiles
 * the structural and tag schemas together and checks a document against both.
 *
 * @see org.coordinatekit.crf.core.io.XmlTrainingData
 * @see org.coordinatekit.crf.core.io.TrainingDataSequencer
 */
@NullMarked
package org.coordinatekit.crf.core.io;

import org.jspecify.annotations.NullMarked;
