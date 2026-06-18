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

import org.coordinatekit.crf.core.UncheckedCrfException;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Validates CRF training data documents against the structural grammar and the tag vocabulary.
 *
 * <p>
 * A document is checked against two schemas compiled together: the fixed structural grammar (the
 * {@code crf:Collection} / {@code crf:Sequence} / {@code crf:Excluded} shape, in the CRF schema
 * namespace) and the tag vocabulary derived from the configured
 * {@link org.coordinatekit.crf.core.TagProvider}. Validation therefore catches both malformed
 * structure and unknown or mistyped tag names.
 *
 * <p>
 * The tag vocabulary is declared in the configured target namespace, or in no namespace when none
 * is configured; tag names are checked either way, so a target namespace is optional.
 *
 * @see XmlTrainingData
 * @see TrainingSchemaGenerator
 */
@NullMarked
public interface TrainingDataValidator {
    /**
     * Validates the training data read from the given input stream.
     *
     * <p>
     * The stream is read but not closed; the caller retains ownership.
     *
     * @param input the input stream to read the training data document from
     * @throws org.coordinatekit.crf.core.UncheckedCrfException if the document is invalid or cannot be
     *         read
     * @throws IllegalStateException if the validator is not properly configured (e.g., an empty tag
     *         set)
     */
    void validate(InputStream input);

    /**
     * Validates the training data stored in the given file.
     *
     * <p>
     * The default implementation opens the file as a stream and delegates to
     * {@link #validate(InputStream)}.
     *
     * @param file the path of the training data document to validate
     * @throws UncheckedCrfException if the document is invalid or cannot be read
     * @throws IllegalStateException if the validator is not properly configured (e.g., an empty tag
     *         set)
     */
    default void validate(Path file) {
        try (InputStream input = Files.newInputStream(file)) {
            validate(input);
        } catch (IOException e) {
            throw new UncheckedCrfException(e);
        }
    }
}
