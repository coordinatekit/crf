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
import java.io.OutputStream;

/**
 * Opens training sequence writers that emit a fresh document to an output stream.
 *
 * <p>
 * Implementations of this interface initialize the destination with the format's envelope (such as
 * an XML prolog and opening root element), then return a writer that appends one sequence per call.
 * The envelope's closing marker is written when the returned writer is closed.
 *
 * <p>
 * Unlike {@link TrainingDataAppender}, this interface targets caller-owned streams and does not
 * support resuming a partially written document; each call produces a new document from the start.
 *
 * @param <T> the type of tag used in training sequences
 * @see TrainingSequenceWriter
 * @see XmlTrainingData
 */
public interface TrainingDataStreamer<T extends Comparable<T>> {
    /**
     * Opens a training sequence writer that emits a fresh document to the given output stream.
     *
     * <p>
     * The implementation writes the format's envelope (such as an XML prolog and opening root element)
     * to {@code output} before returning. Each call to {@link TrainingSequenceWriter#write} appends a
     * single sequence; {@link TrainingSequenceWriter#close()} writes the envelope's closing marker.
     *
     * <p>
     * The output is a valid document only after {@link TrainingSequenceWriter#close()} returns. The
     * returned writer takes ownership of the stream and closes it on {@code close()}.
     *
     * @param output the output stream to write training data to
     * @return a writer positioned to emit new sequences
     * @throws IOException if writing the envelope fails
     */
    TrainingSequenceWriter<T> writer(OutputStream output) throws IOException;
}
