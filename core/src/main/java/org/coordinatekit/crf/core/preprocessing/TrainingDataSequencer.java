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
package org.coordinatekit.crf.core.preprocessing;

import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@NullMarked
public interface TrainingDataSequencer<T> {
    /**
     * Reads training data from an input stream and returns a stream of training sequences.
     *
     * @param input the input stream to read training data from
     * @return a stream of training sequences
     * @throws IOException if an error occurs while reading the input stream
     */
    Stream<TrainingSequence<T>> read(InputStream input) throws IOException;

    /**
     * Reads training data from a file path and returns a stream of training sequences.
     *
     * @param path the path to the training data file
     * @return a stream of training sequences
     * @throws IOException if an error occurs while reading the file
     */
    default Stream<TrainingSequence<T>> read(Path path) throws IOException {
        return read(Files.newInputStream(path));
    }
}
