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
package org.coordinatekit.crf.annotator;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * JUnit 5 extension that redirects {@link System#out} and {@link System#err} to in-memory buffers
 * for the duration of each test and restores the originals afterwards. Register it as an instance
 * field so tests can read what was captured:
 *
 * <pre>
 * &#64;RegisterExtension
 * final CapturedStandardStreams streams = new CapturedStandardStreams();
 * </pre>
 *
 * Then assert on {@link #out()} / {@link #err()}. This replaces the per-class redirect-and-restore
 * lifecycle the tests would otherwise each carry.
 *
 * <p>
 * This fixture is duplicated in the {@code annotator} and {@code cli} test source sets; the copies
 * are kept identical until a shared test-fixtures source set exists, so change both together.
 */
final class CapturedStandardStreams implements BeforeEachCallback, AfterEachCallback {
    private final ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
    private final ByteArrayOutputStream capturedOut = new ByteArrayOutputStream();
    private @Nullable PrintStream originalErr;
    private @Nullable PrintStream originalOut;

    @Override
    public void afterEach(ExtensionContext context) {
        if (originalOut != null) {
            System.setOut(originalOut);
        }
        if (originalErr != null) {
            System.setErr(originalErr);
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        originalOut = System.out;
        originalErr = System.err;
        capturedOut.reset();
        capturedErr.reset();
        System.setOut(new PrintStream(capturedOut, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(capturedErr, true, StandardCharsets.UTF_8));
    }

    /** Returns everything written to {@link System#err} during the current test, decoded as UTF-8. */
    String err() {
        return capturedErr.toString(StandardCharsets.UTF_8);
    }

    /** Returns everything written to {@link System#out} during the current test, decoded as UTF-8. */
    String out() {
        return capturedOut.toString(StandardCharsets.UTF_8);
    }
}
