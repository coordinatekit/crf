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

import java.io.Flushable;
import java.io.IOException;

/**
 * Writes training sequences to an output destination.
 *
 * <p>
 * Implementations of this interface serialize {@link TrainingSequence} objects to various formats
 * (such as XML). Writers are stateful and tied to a single output destination; callers obtain a
 * writer from a factory method on an implementation such as {@link XmlTrainingData}, write
 * sequences via {@link #write(TrainingSequence)}, optionally call {@link #flush()} to make
 * previously written sequences durable, and must invoke {@link #close()} to finalize the output.
 *
 * <p>
 * Specific implementations document their durability guarantees with respect to {@link #flush()}.
 *
 * @param <T> the type of tag used in training sequences
 * @see XmlTrainingData
 * @see TrainingDataSequencer
 */
public interface TrainingSequenceWriter<T extends Comparable<T>> extends AutoCloseable, Flushable {
    /**
     * Finalizes the output and releases any underlying resources.
     *
     * <p>
     * After {@code close} returns, further calls to {@link #write(TrainingSequence)} or
     * {@link #flush()} will throw {@link IOException}. Calling {@code close} more than once is
     * permitted and is a no-op after the first call.
     *
     * @throws IOException if an error occurs while finalizing the output
     */
    @Override
    void close() throws IOException;

    /**
     * Flushes any buffered output so that previously written sequences are visible to other readers of
     * the same destination.
     *
     * <p>
     * The exact durability guarantee provided by {@code flush} is implementation-defined; see the
     * factory method that produced this writer for details.
     *
     * @throws IOException if an error occurs while flushing
     */
    @Override
    void flush() throws IOException;

    /**
     * Writes a single training sequence to the output.
     *
     * <p>
     * The sequence is serialized in the format defined by the writer's implementation. The output
     * position advances after the call returns.
     *
     * @param sequence the training sequence to write
     * @throws IOException if an error occurs while writing
     */
    void write(TrainingSequence<T> sequence) throws IOException;
}
