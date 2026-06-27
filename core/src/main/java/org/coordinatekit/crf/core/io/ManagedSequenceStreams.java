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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.stream.Stream;

/**
 * Binds the lifecycle of an opened {@link InputStream} to the {@link Stream} of training sequences
 * produced from it, so the input is closed exactly once whether sequencing succeeds or fails.
 */
final class ManagedSequenceStreams {
    private ManagedSequenceStreams() {}

    /**
     * Reads training sequences from an already-opened input and ties the input's lifecycle to the
     * returned stream.
     *
     * <p>
     * On success the input is closed when the returned stream is closed; a failure during that close
     * surfaces as an {@link UncheckedIOException}. If the sequencer throws while producing the stream,
     * the input is closed immediately and the original failure propagates, with any close failure
     * attached as a suppressed exception.
     *
     * @param input the opened input stream whose lifecycle the returned stream owns
     * @param sequencer the sequencer used to read training sequences from the input
     * @param <T> the type of tag used in training sequences
     * @return a stream of training sequences that closes the input when closed
     * @throws IOException if the sequencer fails to read the input
     */
    static <T extends Comparable<T>> Stream<TrainingSequence<T>> readManaged(
            InputStream input,
            TrainingDataSequencer<T> sequencer
    ) throws IOException {
        try {
            return sequencer.read(input).onClose(() -> {
                try {
                    input.close();
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            });
        } catch (Throwable failure) {
            try {
                input.close();
            } catch (IOException suppressed) {
                failure.addSuppressed(suppressed);
            }
            throw failure;
        }
    }
}
