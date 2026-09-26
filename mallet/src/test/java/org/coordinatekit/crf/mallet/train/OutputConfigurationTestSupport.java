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
package org.coordinatekit.crf.mallet.train;

import org.junit.jupiter.api.function.Executable;

import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Shared builder-validation cases for {@link ConllOutputConfiguration} and
 * {@link ModelOutputConfiguration}. The two configurations are unrelated types with their own
 * {@code Builder}, but reject the same invalid inputs with byte-for-byte identical messages, so the
 * exception cases live here once and each test supplies its own builder and setters via
 * {@link #outputBuilderExceptionCases}.
 */
final class OutputConfigurationTestSupport {
    private OutputConfigurationTestSupport() {}

    record BuilderExceptionParameters(
            String name,
            Executable action,
            Class<? extends Exception> expectedClass,
            String expectedMessage
    ) {}

    @SuppressWarnings({"DataFlowIssue", "NullAway"}) // null literals passed to non-null setters
    static <B> Stream<BuilderExceptionParameters> outputBuilderExceptionCases(
            Supplier<B> builder,
            BiConsumer<B, String> filePrefix,
            BiConsumer<B, String> fileSuffix,
            ObjIntConsumer<B> iterationInterval,
            BiConsumer<B, Path> outputDirectory
    ) {
        return Stream.of(
                new BuilderExceptionParameters(
                        "filePrefix_null",
                        () -> filePrefix.accept(builder.get(), null),
                        NullPointerException.class,
                        "filePrefix may not be null"
                ),
                new BuilderExceptionParameters(
                        "fileSuffix_null",
                        () -> fileSuffix.accept(builder.get(), null),
                        NullPointerException.class,
                        "fileSuffix may not be null"
                ),
                new BuilderExceptionParameters(
                        "iterationInterval_zero",
                        () -> iterationInterval.accept(builder.get(), 0),
                        IllegalArgumentException.class,
                        "iterationInterval must be positive, got: 0"
                ),
                new BuilderExceptionParameters(
                        "iterationInterval_negative",
                        () -> iterationInterval.accept(builder.get(), -5),
                        IllegalArgumentException.class,
                        "iterationInterval must be positive, got: -5"
                ),
                new BuilderExceptionParameters(
                        "outputDirectory_null",
                        () -> outputDirectory.accept(builder.get(), null),
                        NullPointerException.class,
                        "outputDirectory may not be null"
                )
        );
    }
}
