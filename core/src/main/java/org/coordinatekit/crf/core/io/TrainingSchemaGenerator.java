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
package org.coordinatekit.crf.core.io;

import org.jspecify.annotations.NullMarked;

import java.io.OutputStream;

/**
 * Generates schema definitions for validating CRF training data.
 *
 * <p>
 * Implementations of this interface produce schemas (such as XSD) that define the structure and
 * valid elements for training data files. These schemas can be used to validate training data
 * before processing.
 *
 * @see XmlTrainingData
 */
@NullMarked
public interface TrainingSchemaGenerator {

    /**
     * Generates a schema and writes it to the specified output stream.
     *
     * <p>
     * The format of the generated schema depends on the implementation. For example,
     * {@link XmlTrainingData} generates an XSD schema.
     *
     * @param output the output stream to write the schema to
     * @throws org.coordinatekit.crf.core.UncheckedCrfException if an error occurs during generation
     * @throws IllegalStateException if the generator is not properly configured (e.g., missing target
     *         namespace or empty tag set)
     */
    void generateSchema(OutputStream output);
}
