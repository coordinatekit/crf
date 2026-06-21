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
package org.coordinatekit.crf.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.coordinatekit.crf.annotator.AnnotatorConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import picocli.CommandLine;

/**
 * Tests the {@code annotate} subcommand's configuration parsing: the standard flags are parsed into
 * an {@link AnnotatorConfiguration} via the {@link AnnotatorCommand#configuration()} seam. The exit
 * codes picocli produces for help, a rejected argument list, the version flag, and a startup
 * failure are covered by {@link CommandExecutionTestSupport}; the terminal wiring is covered by
 * {@code AnnotatorRunnerTest} in the annotator module.
 */
class AnnotatorCommandTest extends CommandExecutionTestSupport {
    record ThresholdAcceptedParameters(String name, String threshold, double expected) {}

    @Test
    void configuration__defaults(@TempDir Path tempDirectory) {
        // ACT //
        AnnotatorConfiguration configuration = parse(
                "--input",
                tempDirectory.resolve("in.txt").toString(),
                "--output",
                tempDirectory.resolve("out.xml").toString()
        );

        // ASSERT //
        assertEquals(tempDirectory.resolve("in.txt"), configuration.input());
        assertEquals(tempDirectory.resolve("out.xml"), configuration.output());
        assertNull(configuration.model());
        assertEquals(AnnotatorConfiguration.DEFAULT_THRESHOLD, configuration.threshold());
    }

    @Test
    void configuration__longAndShortFlagsParseIdentically(@TempDir Path tempDirectory) {
        // ARRANGE //
        String input = tempDirectory.resolve("in.txt").toString();
        String output = tempDirectory.resolve("out.xml").toString();
        String model = tempDirectory.resolve("model.bin").toString();

        // ACT //
        AnnotatorConfiguration longConfiguration = parse(
                "--input",
                input,
                "--output",
                output,
                "--model",
                model,
                "--threshold",
                "0.5"
        );
        AnnotatorConfiguration shortConfiguration = parse("-i", input, "-o", output, "-m", model, "-t", "0.5");

        // ASSERT //
        assertEquals(longConfiguration.input(), shortConfiguration.input());
        assertEquals(longConfiguration.model(), shortConfiguration.model());
        assertEquals(longConfiguration.output(), shortConfiguration.output());
        assertEquals(longConfiguration.threshold(), shortConfiguration.threshold());
    }

    static Stream<ThresholdAcceptedParameters> configuration__thresholdBoundariesAccepted() {
        return Stream.of(
                new ThresholdAcceptedParameters("lower_bound", "0.0", 0.0),
                new ThresholdAcceptedParameters("upper_bound", "1.0", 1.0)
        );
    }

    @MethodSource
    @ParameterizedTest
    void configuration__thresholdBoundariesAccepted(
            ThresholdAcceptedParameters parameters,
            @TempDir Path tempDirectory
    ) {
        // ACT //
        AnnotatorConfiguration configuration = parse(
                "--input",
                tempDirectory.resolve("in.txt").toString(),
                "--output",
                tempDirectory.resolve("out.xml").toString(),
                "--threshold",
                parameters.threshold()
        );

        // ASSERT //
        assertEquals(parameters.expected(), configuration.threshold(), "inclusive boundary must be accepted");
    }

    @Override
    Callable<Integer> newCommand(ResolvedServices.Builder servicesBuilder) {
        return new AnnotatorCommand(servicesBuilder);
    }

    private static AnnotatorConfiguration parse(String... arguments) {
        AnnotatorCommand command = new AnnotatorCommand(ResolvedServices.builder());
        new CommandLine(command).parseArgs(arguments);
        return command.configuration();
    }
}
