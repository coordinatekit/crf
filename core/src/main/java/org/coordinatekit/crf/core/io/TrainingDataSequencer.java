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

import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Reads training data and converts it into a stream of training sequences.
 *
 * <p>
 * Implementations of this interface parse training data from various formats (such as XML) and
 * produce a stream of {@link TrainingSequence} objects that can be used to train CRF models.
 *
 * <p>
 * The returned streams should be closed after use to release any underlying resources. When using
 * try-with-resources, the stream and any associated resources will be automatically closed.
 *
 * @param <T> the type of tag used in training sequences
 * @see XmlTrainingData
 */
@NullMarked
public interface TrainingDataSequencer<T extends Comparable<T>> {

    /**
     * Reads training data from an input stream and returns a stream of training sequences.
     *
     * <p>
     * The caller is responsible for closing both the returned stream and the input stream.
     *
     * @param input the input stream to read training data from
     * @return a stream of training sequences that should be closed after use
     * @throws IOException if an error occurs while reading the input stream
     */
    Stream<TrainingSequence<T>> read(InputStream input) throws IOException;

    /**
     * Reads training data from a file path and returns a stream of training sequences.
     *
     * <p>
     * The returned stream must be closed after use to release the underlying file handle. Using
     * try-with-resources is recommended.
     *
     * @param path the path to the training data file
     * @return a stream of training sequences that should be closed after use
     * @throws IOException if an error occurs while reading the file
     */
    default Stream<TrainingSequence<T>> read(Path path) throws IOException {
        InputStream input = Files.newInputStream(path);
        return read(input).onClose(() -> {
            try {
                input.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
