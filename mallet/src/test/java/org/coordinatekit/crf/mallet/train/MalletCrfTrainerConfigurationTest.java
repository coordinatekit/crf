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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MalletCrfTrainerConfigurationTest {

    record BuilderExceptionParameters(
            String name,
            Executable action,
            Class<? extends Exception> expectedClass,
            String expectedMessage
    ) {}

    @SuppressWarnings({"DataFlowIssue", "NullAway"}) // null literals passed to non-null setters
    static Stream<BuilderExceptionParameters> builder__exception() {
        return Stream.of(
                new BuilderExceptionParameters(
                        "gaussianVariance_negative",
                        () -> MalletCrfTrainerConfiguration.builder().gaussianVariance(-1.0),
                        IllegalArgumentException.class,
                        "gaussianVariance must be positive, got: -1.0"
                ),
                new BuilderExceptionParameters(
                        "gaussianVariance_zero",
                        () -> MalletCrfTrainerConfiguration.builder().gaussianVariance(0),
                        IllegalArgumentException.class,
                        "gaussianVariance must be positive, got: 0.0"
                ),
                new BuilderExceptionParameters(
                        "iterations_negative",
                        () -> MalletCrfTrainerConfiguration.builder().iterations(-10),
                        IllegalArgumentException.class,
                        "iterations must be positive, got: -10"
                ),
                new BuilderExceptionParameters(
                        "iterations_zero",
                        () -> MalletCrfTrainerConfiguration.builder().iterations(0),
                        IllegalArgumentException.class,
                        "iterations must be positive, got: 0"
                ),
                new BuilderExceptionParameters(
                        "threads_negative",
                        () -> MalletCrfTrainerConfiguration.builder().threads(-4),
                        IllegalArgumentException.class,
                        "threads must be positive, got: -4"
                ),
                new BuilderExceptionParameters(
                        "threads_zero",
                        () -> MalletCrfTrainerConfiguration.builder().threads(0),
                        IllegalArgumentException.class,
                        "threads must be positive, got: 0"
                ),
                new BuilderExceptionParameters(
                        "trainingFraction_greaterThanOne",
                        () -> MalletCrfTrainerConfiguration.builder().trainingFraction(1.01),
                        IllegalArgumentException.class,
                        "trainingFraction must be in (0.0, 1.0], got: 1.01"
                ),
                new BuilderExceptionParameters(
                        "trainingFraction_negative",
                        () -> MalletCrfTrainerConfiguration.builder().trainingFraction(-0.5),
                        IllegalArgumentException.class,
                        "trainingFraction must be in (0.0, 1.0], got: -0.5"
                ),
                new BuilderExceptionParameters(
                        "trainingFraction_zero",
                        () -> MalletCrfTrainerConfiguration.builder().trainingFraction(0.0),
                        IllegalArgumentException.class,
                        "trainingFraction must be in (0.0, 1.0], got: 0.0"
                ),
                new BuilderExceptionParameters(
                        "weightsType_null",
                        () -> MalletCrfTrainerConfiguration.builder().weightsType(null),
                        NullPointerException.class,
                        "weightsType must not be null"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void builder__exception(BuilderExceptionParameters parameters) {
        // ACT //
        Exception exception = assertThrows(parameters.expectedClass(), parameters.action());

        // ASSERT //
        assertEquals(parameters.expectedMessage(), exception.getMessage());
    }

    @Test
    void builder_canBeReused() {
        var builder = MalletCrfTrainerConfiguration.builder().iterations(100).threads(4);

        MalletCrfTrainerConfiguration config1 = builder.build();
        MalletCrfTrainerConfiguration config2 = builder.gaussianVariance(20.0).build();

        assertEquals(100, config1.iterations());
        assertEquals(4, config1.threads());
        assertEquals(10.0, config1.gaussianVariance());

        assertEquals(100, config2.iterations());
        assertEquals(4, config2.threads());
        assertEquals(20.0, config2.gaussianVariance());
    }

    @Test
    void builder_gaussianVariance_acceptsPositiveValues() {
        MalletCrfTrainerConfiguration config = MalletCrfTrainerConfiguration.builder().gaussianVariance(0.001).build();

        assertEquals(0.001, config.gaussianVariance());
    }

    @Test
    void builder_iterations_acceptsPositive() {
        MalletCrfTrainerConfiguration config = MalletCrfTrainerConfiguration.builder().iterations(1).build();

        assertEquals(1, config.iterations());
    }

    @Test
    void builder_threads_acceptsPositive() {
        MalletCrfTrainerConfiguration config = MalletCrfTrainerConfiguration.builder().threads(1).build();

        assertEquals(1, config.threads());
    }

    @Test
    void builder_randomSeed_acceptsAnyInteger() {
        MalletCrfTrainerConfiguration configNegative = MalletCrfTrainerConfiguration.builder().randomSeed(-100).build();
        MalletCrfTrainerConfiguration configZero = MalletCrfTrainerConfiguration.builder().randomSeed(0).build();
        MalletCrfTrainerConfiguration configPositive = MalletCrfTrainerConfiguration.builder()
                .randomSeed(Integer.MAX_VALUE).build();

        assertEquals(-100, configNegative.randomSeed());
        assertEquals(0, configZero.randomSeed());
        assertEquals(Integer.MAX_VALUE, configPositive.randomSeed());
    }

    @Test
    void builder_trainingFraction_acceptsValidRange() {
        MalletCrfTrainerConfiguration configMin = MalletCrfTrainerConfiguration.builder().trainingFraction(0.001)
                .build();
        MalletCrfTrainerConfiguration configMax = MalletCrfTrainerConfiguration.builder().trainingFraction(1.0).build();

        assertEquals(0.001, configMin.trainingFraction());
        assertEquals(1.0, configMax.trainingFraction());
    }

    @Test
    void builder_weightsType_acceptsAllEnumValues() {
        for (WeightsType type : WeightsType.values()) {
            MalletCrfTrainerConfiguration config = MalletCrfTrainerConfiguration.builder().weightsType(type).build();

            assertEquals(type, config.weightsType());
        }
    }

    @Test
    void builder_withAllCustomValues_returnsConfigWithCustomValues() {
        MalletCrfTrainerConfiguration config = MalletCrfTrainerConfiguration.builder().gaussianVariance(5.0)
                .trainingFraction(0.8).randomSeed(42).iterations(1000).fullyConnected(false)
                .weightsType(WeightsType.SPARSE).threads(12).build();

        assertEquals(5.0, config.gaussianVariance());
        assertEquals(0.8, config.trainingFraction());
        assertEquals(42, config.randomSeed());
        assertEquals(1000, config.iterations());
        assertFalse(config.fullyConnected());
        assertEquals(WeightsType.SPARSE, config.weightsType());
        assertEquals(12, config.threads());
    }

    @Test
    void builder_withNoCustomizations_returnsSameAsDefaults() {
        MalletCrfTrainerConfiguration fromBuilder = MalletCrfTrainerConfiguration.builder().build();
        MalletCrfTrainerConfiguration defaults = MalletCrfTrainerConfiguration.defaults();

        assertEquals(defaults.gaussianVariance(), fromBuilder.gaussianVariance());
        assertEquals(defaults.trainingFraction(), fromBuilder.trainingFraction());
        assertEquals(defaults.randomSeed(), fromBuilder.randomSeed());
        assertEquals(defaults.iterations(), fromBuilder.iterations());
        assertEquals(defaults.fullyConnected(), fromBuilder.fullyConnected());
        assertEquals(defaults.weightsType(), fromBuilder.weightsType());
        assertEquals(defaults.threads(), fromBuilder.threads());
    }

    @Test
    void defaults_returnsConfigWithDefaultValues() {
        MalletCrfTrainerConfiguration config = MalletCrfTrainerConfiguration.defaults();

        assertEquals(10.0, config.gaussianVariance());
        assertEquals(0.5, config.trainingFraction());
        assertEquals(0, config.randomSeed());
        assertEquals(500, config.iterations());
        assertTrue(config.fullyConnected());
        assertEquals(WeightsType.SOME_DENSE, config.weightsType());
        assertEquals(6, config.threads());
    }
}
