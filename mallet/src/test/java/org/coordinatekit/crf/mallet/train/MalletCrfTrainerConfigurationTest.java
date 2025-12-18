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

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NullMarked
class MalletCrfTrainerConfigurationTest {

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
    void builder_gaussianVariance_rejectsNegative() {
        var builder = MalletCrfTrainerConfiguration.builder();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> builder.gaussianVariance(-1.0)
        );

        assertTrue(exception.getMessage().contains("gaussianVariance"));
        assertTrue(exception.getMessage().contains("positive"));
    }

    @Test
    void builder_gaussianVariance_rejectsZero() {
        var builder = MalletCrfTrainerConfiguration.builder();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> builder.gaussianVariance(0)
        );

        assertTrue(exception.getMessage().contains("gaussianVariance"));
        assertTrue(exception.getMessage().contains("positive"));
    }

    @Test
    void builder_iterations_acceptsPositive() {
        MalletCrfTrainerConfiguration config = MalletCrfTrainerConfiguration.builder().iterations(1).build();

        assertEquals(1, config.iterations());
    }

    @Test
    void builder_iterations_rejectsNegative() {
        var builder = MalletCrfTrainerConfiguration.builder();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> builder.iterations(-10)
        );

        assertTrue(exception.getMessage().contains("iterations"));
        assertTrue(exception.getMessage().contains("positive"));
    }

    @Test
    void builder_iterations_rejectsZero() {
        var builder = MalletCrfTrainerConfiguration.builder();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> builder.iterations(0));

        assertTrue(exception.getMessage().contains("iterations"));
        assertTrue(exception.getMessage().contains("positive"));
    }

    @Test
    void builder_threads_acceptsPositive() {
        MalletCrfTrainerConfiguration config = MalletCrfTrainerConfiguration.builder().threads(1).build();

        assertEquals(1, config.threads());
    }

    @Test
    void builder_threads_rejectsNegative() {
        var builder = MalletCrfTrainerConfiguration.builder();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> builder.threads(-4));

        assertTrue(exception.getMessage().contains("threads"));
        assertTrue(exception.getMessage().contains("positive"));
    }

    @Test
    void builder_threads_rejectsZero() {
        var builder = MalletCrfTrainerConfiguration.builder();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> builder.threads(0));

        assertTrue(exception.getMessage().contains("threads"));
        assertTrue(exception.getMessage().contains("positive"));
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
    void builder_trainingFraction_rejectsGreaterThanOne() {
        var builder = MalletCrfTrainerConfiguration.builder();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> builder.trainingFraction(1.01)
        );

        assertTrue(exception.getMessage().contains("trainingFraction"));
    }

    @Test
    void builder_trainingFraction_rejectsNegative() {
        var builder = MalletCrfTrainerConfiguration.builder();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> builder.trainingFraction(-0.5)
        );

        assertTrue(exception.getMessage().contains("trainingFraction"));
    }

    @Test
    void builder_trainingFraction_rejectsZero() {
        var builder = MalletCrfTrainerConfiguration.builder();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> builder.trainingFraction(0.0)
        );

        assertTrue(exception.getMessage().contains("trainingFraction"));
        assertTrue(exception.getMessage().contains("(0.0, 1.0]"));
    }

    @Test
    void builder_weightsType_acceptsAllEnumValues() {
        for (WeightsType type : WeightsType.values()) {
            MalletCrfTrainerConfiguration config = MalletCrfTrainerConfiguration.builder().weightsType(type).build();

            assertEquals(type, config.weightsType());
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void builder_weightsType_rejectsNull() {
        var builder = MalletCrfTrainerConfiguration.builder();

        NullPointerException exception = assertThrows(NullPointerException.class, () -> builder.weightsType(null));

        assertTrue(exception.getMessage().contains("weightsType"));
        assertTrue(exception.getMessage().contains("null"));
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
