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

import java.io.IOException;
import java.nio.file.Path;

/**
 * Opens training sequence writers that append to existing training data files.
 *
 * <p>
 * Implementations of this interface support adding new training sequences to a file on disk without
 * rewriting the existing content. Fresh files are initialized with the appropriate envelope (such
 * as an XML prolog and root element); existing files are validated, their close marker is removed
 * if present, and subsequent writes are appended in place. The close marker is restored when the
 * returned writer is closed.
 *
 * @param <T> the type of tag used in training sequences
 * @see TrainingSequenceWriter
 * @see XmlTrainingData
 */
public interface TrainingDataAppender<T extends Comparable<T>> {
    /**
     * Opens a training sequence writer that appends to the given file.
     *
     * <p>
     * If the file is missing or empty, the implementation initializes it with the format's envelope. If
     * the file already exists, it is validated against the expected format; an {@link IOException} is
     * thrown without modifying the file if validation fails.
     *
     * <p>
     * The returned writer must be closed to finalize the file. The file is a valid document only after
     * {@link TrainingSequenceWriter#close()} returns.
     *
     * @param output the path of the training data file to append to
     * @return a writer positioned to append new sequences
     * @throws IOException if the file cannot be opened, validated, or prepared for append
     */
    TrainingSequenceWriter<T> appendingWriter(Path output) throws IOException;
}
