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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManagedSequenceStreamsTest {

    @Test
    void readManaged__closesInputWhenDelegateReturns() throws IOException {
        // ARRANGE //
        var input = new TrackingInputStream();
        TrainingDataSequencer<String> sequencer = stream -> Stream.empty();

        // ACT // the input is closed only when the returned stream is closed
        try (var sequences = ManagedSequenceStreams.readManaged(input, sequencer)) {
            assertEquals(0, input.closeCount);
        }

        // ASSERT //
        assertEquals(1, input.closeCount);
    }

    record PropagateParameters(String name, Throwable failure, Class<? extends Throwable> expectedClass) {}

    static Stream<PropagateParameters> readManaged__propagatesDelegateFailureAndCloses() {
        return Stream.of(
                new PropagateParameters(
                        "unchecked",
                        new UncheckedCrfException("read failed"),
                        UncheckedCrfException.class
                ),
                new PropagateParameters("checked_io_exception", new IOException("read failed"), IOException.class)
        );
    }

    @MethodSource
    @ParameterizedTest
    void readManaged__propagatesDelegateFailureAndCloses(PropagateParameters parameters) {
        // ARRANGE //
        var input = new TrackingInputStream();
        TrainingDataSequencer<String> sequencer = stream -> {
            if (parameters.failure()instanceof IOException ioException) {
                throw ioException;
            }
            throw (RuntimeException) parameters.failure();
        };

        // ACT //
        Throwable thrown = assertThrows(
                parameters.expectedClass(),
                () -> ManagedSequenceStreams.readManaged(input, sequencer)
        );

        // ASSERT // the original failure propagates and the input is closed without suppression
        assertSame(parameters.failure(), thrown);
        assertEquals(1, input.closeCount);
        assertEquals(0, thrown.getSuppressed().length);
    }

    @Test
    void readManaged__suppressesCloseFailureWhenDelegateThrows() {
        // ARRANGE //
        var closeFailure = new IOException("close failed");
        var input = new TrackingInputStream(closeFailure);
        var delegateFailure = new UncheckedCrfException("read failed");
        TrainingDataSequencer<String> sequencer = stream -> {
            throw delegateFailure;
        };

        // ACT //
        UncheckedCrfException thrown = assertThrows(
                UncheckedCrfException.class,
                () -> ManagedSequenceStreams.readManaged(input, sequencer)
        );

        // ASSERT // the delegate failure propagates with the close failure attached as suppressed
        assertSame(delegateFailure, thrown);
        assertEquals(1, input.closeCount);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(closeFailure, thrown.getSuppressed()[0]);
    }

    @Test
    void readManaged__wrapsCloseFailureOnStreamClose() throws IOException {
        // ARRANGE //
        var closeFailure = new IOException("close failed");
        var input = new TrackingInputStream(closeFailure);
        TrainingDataSequencer<String> sequencer = stream -> Stream.empty();
        var sequences = ManagedSequenceStreams.readManaged(input, sequencer);

        // ACT // a close failure on the managed input surfaces as an unchecked exception
        UncheckedIOException thrown = assertThrows(UncheckedIOException.class, sequences::close);

        // ASSERT //
        assertSame(closeFailure, thrown.getCause());
        assertEquals(1, input.closeCount);
    }

    /** An input stream that counts {@code close()} calls and optionally fails them. */
    static final class TrackingInputStream extends InputStream {
        int closeCount;
        private final @Nullable IOException closeFailure;

        TrackingInputStream() {
            this(null);
        }

        TrackingInputStream(@Nullable IOException closeFailure) {
            this.closeFailure = closeFailure;
        }

        @Override
        public void close() throws IOException {
            closeCount++;
            if (closeFailure != null) {
                throw closeFailure;
            }
        }

        @Override
        public int read() {
            return -1;
        }
    }
}
