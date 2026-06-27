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

import org.coordinatekit.crf.mallet.train.OutputConfigurationTestSupport.BuilderExceptionParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.coordinatekit.crf.mallet.train.OutputConfigurationTestSupport.outputBuilderExceptionCases;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelOutputConfigurationTest {

    private static final Path TEST_OUTPUT_DIR = Path.of("test/output");

    static Stream<BuilderExceptionParameters> builder__exception() {
        return outputBuilderExceptionCases(
                ModelOutputConfiguration::builder,
                ModelOutputConfiguration.Builder::filePrefix,
                ModelOutputConfiguration.Builder::fileSuffix,
                ModelOutputConfiguration.Builder::iterationInterval,
                ModelOutputConfiguration.Builder::outputDirectory
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
    void builder_withRequiredFieldsOnly_usesDefaults() {
        ModelOutputConfiguration config = ModelOutputConfiguration.builder().build();

        assertEquals(Paths.get(""), config.outputDirectory());
        assertEquals("model_", config.filePrefix());
        assertEquals(".ser", config.fileSuffix());
        assertEquals(10, config.iterationInterval());
    }

    @Test
    void builder_withAllCustomValues_returnsConfigWithCustomValues() {
        Path customDirectory = Path.of("custom", "dir");
        ModelOutputConfiguration config = ModelOutputConfiguration.builder().outputDirectory(customDirectory)
                .filePrefix("crf_model").fileSuffix("bin").iterationInterval(5).build();

        assertEquals(customDirectory, config.outputDirectory());
        assertEquals("crf_model", config.filePrefix());
        assertEquals("bin", config.fileSuffix());
        assertEquals(5, config.iterationInterval());
    }

    @Test
    void builder_canBeReused() {
        var builder = ModelOutputConfiguration.builder().filePrefix("test");

        ModelOutputConfiguration config1 = builder.build();
        ModelOutputConfiguration config2 = builder.fileSuffix(".bin").build();

        assertEquals("test", config1.filePrefix());
        assertEquals(".ser", config1.fileSuffix());

        assertEquals("test", config2.filePrefix());
        assertEquals(".bin", config2.fileSuffix());
    }

    @Test
    void builder_filePrefix_acceptsValidValues() {
        ModelOutputConfiguration config = ModelOutputConfiguration.builder().outputDirectory(TEST_OUTPUT_DIR)
                .filePrefix("my_prefix").build();

        assertEquals("my_prefix", config.filePrefix());
    }

    @Test
    void builder_fileSuffix_acceptsValidValues() {
        ModelOutputConfiguration config = ModelOutputConfiguration.builder().outputDirectory(TEST_OUTPUT_DIR)
                .fileSuffix("bin").build();

        assertEquals("bin", config.fileSuffix());
    }

    @Test
    void builder_iterationInterval_acceptsPositive() {
        ModelOutputConfiguration config = ModelOutputConfiguration.builder().outputDirectory(TEST_OUTPUT_DIR)
                .iterationInterval(1).build();

        assertEquals(1, config.iterationInterval());
    }

    @Test
    void builder_outputDirectory_acceptsPath() {
        ModelOutputConfiguration config = ModelOutputConfiguration.builder().outputDirectory(TEST_OUTPUT_DIR).build();

        assertEquals(TEST_OUTPUT_DIR, config.outputDirectory());
    }

    @Test
    void defaults_returnsSameAsEmptyBuilder() {
        ModelOutputConfiguration fromDefaults = ModelOutputConfiguration.defaults();
        ModelOutputConfiguration fromBuilder = ModelOutputConfiguration.builder().build();

        assertEquals(fromBuilder.outputDirectory(), fromDefaults.outputDirectory());
        assertEquals(fromBuilder.filePrefix(), fromDefaults.filePrefix());
        assertEquals(fromBuilder.fileSuffix(), fromDefaults.fileSuffix());
        assertEquals(fromBuilder.iterationInterval(), fromDefaults.iterationInterval());
    }
}
