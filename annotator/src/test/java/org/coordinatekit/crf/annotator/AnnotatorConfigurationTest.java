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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

class AnnotatorConfigurationTest {
    record BuilderExceptionParameters(
            String name,
            Executable action,
            Class<? extends Exception> expectedClass,
            String expectedMessage
    ) {}

    record ThresholdAcceptedParameters(String name, double threshold) {}

    @Test
    void build__defaultsAndRoundTrips() {
        // ARRANGE //
        Path input = Path.of("in.txt");
        Path model = Path.of("model.bin");
        Path output = Path.of("out.xml");

        // ACT //
        AnnotatorConfiguration configuration = AnnotatorConfiguration.builder().input(input).model(model).output(output)
                .threshold(0.5).build();
        AnnotatorConfiguration defaulted = AnnotatorConfiguration.builder().input(input).output(output).build();

        // ASSERT //
        assertEquals(input, configuration.input());
        assertEquals(model, configuration.model());
        assertEquals(output, configuration.output());
        assertEquals(0.5, configuration.threshold());
        assertNull(defaulted.model());
        assertEquals(AnnotatorConfiguration.DEFAULT_THRESHOLD, defaulted.threshold());
    }

    static Stream<BuilderExceptionParameters> build__exception() {
        Path input = Path.of("in.txt");
        Path output = Path.of("out.xml");
        return Stream.of(
                new BuilderExceptionParameters(
                        "missing_input",
                        () -> AnnotatorConfiguration.builder().output(output).build(),
                        IllegalStateException.class,
                        "input must be set"
                ),
                new BuilderExceptionParameters(
                        "missing_output",
                        () -> AnnotatorConfiguration.builder().input(input).build(),
                        IllegalStateException.class,
                        "output must be set"
                ),
                new BuilderExceptionParameters(
                        "threshold_nan",
                        () -> AnnotatorConfiguration.builder().threshold(Double.NaN),
                        IllegalArgumentException.class,
                        "threshold must be in [0.0, 1.0], got: NaN"
                ),
                new BuilderExceptionParameters(
                        "threshold_negative",
                        () -> AnnotatorConfiguration.builder().threshold(-0.1),
                        IllegalArgumentException.class,
                        "threshold must be in [0.0, 1.0], got: -0.1"
                ),
                new BuilderExceptionParameters(
                        "threshold_above_one",
                        () -> AnnotatorConfiguration.builder().threshold(1.1),
                        IllegalArgumentException.class,
                        "threshold must be in [0.0, 1.0], got: 1.1"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void build__exception(BuilderExceptionParameters parameters) {
        // ACT //
        Exception exception = assertThrows(parameters.expectedClass(), parameters.action());

        // ASSERT //
        assertEquals(parameters.expectedMessage(), exception.getMessage());
    }

    static Stream<ThresholdAcceptedParameters> build__thresholdBoundariesAccepted() {
        return Stream.of(
                new ThresholdAcceptedParameters("lower_bound", 0.0),
                new ThresholdAcceptedParameters("upper_bound", 1.0)
        );
    }

    @MethodSource
    @ParameterizedTest
    void build__thresholdBoundariesAccepted(ThresholdAcceptedParameters parameters) {
        // ACT //
        AnnotatorConfiguration configuration = AnnotatorConfiguration.builder().input(Path.of("in.txt"))
                .output(Path.of("out.xml")).threshold(parameters.threshold()).build();

        // ASSERT //
        assertEquals(
                parameters.threshold(),
                configuration.threshold(),
                "inclusive boundary must be accepted and round-trip"
        );
    }
}
