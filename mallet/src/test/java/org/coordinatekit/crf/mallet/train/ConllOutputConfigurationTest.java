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

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NullMarked
class ConllOutputConfigurationTest {

    private static final Path TEST_OUTPUT_DIR = Path.of("test/output");

    @Test
    void builder_withRequiredFieldsOnly_usesDefaults() {
        ConllOutputConfiguration config = ConllOutputConfiguration.builder().build();

        assertEquals(Paths.get(""), config.outputDirectory());
        assertEquals("output_iter", config.filePrefix());
        assertEquals(".conll", config.fileSuffix());
        assertEquals(10, config.iterationInterval());
    }

    @Test
    void builder_withAllCustomValues_returnsConfigWithCustomValues() {
        Path customDirectory = Path.of("custom", "dir");
        ConllOutputConfiguration config = ConllOutputConfiguration.builder().outputDirectory(customDirectory)
                .filePrefix("predictions").fileSuffix("tsv").iterationInterval(5).build();

        assertEquals(customDirectory, config.outputDirectory());
        assertEquals("predictions", config.filePrefix());
        assertEquals("tsv", config.fileSuffix());
        assertEquals(5, config.iterationInterval());
    }

    @Test
    void builder_canBeReused() {
        var builder = ConllOutputConfiguration.builder().filePrefix("test");

        ConllOutputConfiguration config1 = builder.build();
        ConllOutputConfiguration config2 = builder.fileSuffix(".txt").build();

        assertEquals("test", config1.filePrefix());
        assertEquals(".conll", config1.fileSuffix());

        assertEquals("test", config2.filePrefix());
        assertEquals(".txt", config2.fileSuffix());
    }

    @Test
    void builder_filePrefix_acceptsValidValues() {
        ConllOutputConfiguration config = ConllOutputConfiguration.builder().outputDirectory(TEST_OUTPUT_DIR)
                .filePrefix("my_prefix").build();

        assertEquals("my_prefix", config.filePrefix());
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void builder_filePrefix_rejectsNull() {
        var builder = ConllOutputConfiguration.builder();

        NullPointerException exception = assertThrows(NullPointerException.class, () -> builder.filePrefix(null));

        assertTrue(exception.getMessage().contains("filePrefix"));
        assertTrue(exception.getMessage().contains("null"));
    }

    @Test
    void builder_fileSuffix_acceptsValidValues() {
        ConllOutputConfiguration config = ConllOutputConfiguration.builder().outputDirectory(TEST_OUTPUT_DIR)
                .fileSuffix("txt").build();

        assertEquals("txt", config.fileSuffix());
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void builder_fileSuffix_rejectsNull() {
        var builder = ConllOutputConfiguration.builder();

        NullPointerException exception = assertThrows(NullPointerException.class, () -> builder.fileSuffix(null));

        assertTrue(exception.getMessage().contains("fileSuffix"));
        assertTrue(exception.getMessage().contains("null"));
    }

    @Test
    void builder_iterationInterval_acceptsPositive() {
        ConllOutputConfiguration config = ConllOutputConfiguration.builder().outputDirectory(TEST_OUTPUT_DIR)
                .iterationInterval(1).build();

        assertEquals(1, config.iterationInterval());
    }

    @Test
    void builder_iterationInterval_rejectsZero() {
        var builder = ConllOutputConfiguration.builder();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> builder.iterationInterval(0)
        );

        assertTrue(exception.getMessage().contains("iterationInterval"));
        assertTrue(exception.getMessage().contains("positive"));
    }

    @Test
    void builder_iterationInterval_rejectsNegative() {
        var builder = ConllOutputConfiguration.builder();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> builder.iterationInterval(-5)
        );

        assertTrue(exception.getMessage().contains("iterationInterval"));
        assertTrue(exception.getMessage().contains("positive"));
    }

    @Test
    void builder_iterationInterval_acceptsPath() {
        ConllOutputConfiguration config = ConllOutputConfiguration.builder().outputDirectory(TEST_OUTPUT_DIR).build();

        assertEquals(TEST_OUTPUT_DIR, config.outputDirectory());
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void builder_outputDirectory_rejectsNull() {
        var builder = ConllOutputConfiguration.builder();

        NullPointerException exception = assertThrows(NullPointerException.class, () -> builder.outputDirectory(null));

        assertTrue(exception.getMessage().contains("outputDirectory"));
        assertTrue(exception.getMessage().contains("null"));
    }
}
