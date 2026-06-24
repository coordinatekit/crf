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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Tests the {@link CrfLauncher} routing: how the root {@code crf} command dispatches to its
 * subcommands and what it does when no subcommand, an unknown subcommand, or {@code --help} is
 * given, plus the fail-fast startup behavior when no {@code TagProvider} is registered (no service
 * provider is on the test classpath). Driven through the public {@link CrfLauncher#run(String[])}
 * entry against the captured standard streams.
 */
class CrfLauncherTest {
    @RegisterExtension
    final CapturedStandardStreams streams = new CapturedStandardStreams();

    @Test
    void run__annotateWithoutTagProviderFailsFast() {
        // ACT //
        int exitCode = CrfLauncher.run(new String[] {"annotate", "-i", "in.txt", "-o", "out.xml"});

        // ASSERT //
        assertEquals(1, exitCode);
        assertTrue(
                streams.err().contains("TagProvider"),
                "stderr should guide the user to register a TagProvider; was: " + streams.err()
        );
    }

    @Test
    void run__helpListsSubcommandsAndReturnsZero() {
        // ACT //
        int exitCode = CrfLauncher.run(new String[] {"--help"});

        // ASSERT //
        assertEquals(0, exitCode);
        assertTrue(
                streams.out().contains("annotate"),
                "help should list the annotate subcommand; was: " + streams.out()
        );
        assertTrue(
                streams.out().contains("retokenize"),
                "help should list the retokenize subcommand; was: " + streams.out()
        );
    }

    @Test
    void run__noSubcommandPrintsUsageAndReturnsTwo() {
        // ACT //
        int exitCode = CrfLauncher.run(new String[] {});

        // ASSERT //
        assertEquals(2, exitCode);
        assertTrue(streams.err().contains("annotate"), "usage should list the subcommands; was: " + streams.err());
    }

    @Test
    void run__retokenizeWithoutTagProviderFailsFast() {
        // ACT //
        int exitCode = CrfLauncher.run(new String[] {"retokenize", "-i", "in.xml", "-o", "out.xml"});

        // ASSERT //
        assertEquals(1, exitCode);
        assertTrue(
                streams.err().contains("TagProvider"),
                "stderr should guide the user to register a TagProvider; was: " + streams.err()
        );
    }

    @Test
    void run__unknownSubcommandReturnsTwo() {
        // ACT //
        int exitCode = CrfLauncher.run(new String[] {"bogus"});

        // ASSERT //
        assertEquals(2, exitCode);
        assertTrue(streams.err().contains("bogus"), "stderr should name the unknown argument; was: " + streams.err());
    }

    @Test
    void run__versionFlagPrintsVersionAndReturnsZero() {
        // ACT //
        int exitCode = CrfLauncher.run(new String[] {"--version"});

        // ASSERT //
        assertEquals(0, exitCode);
        assertTrue(
                streams.out().contains("crf (development build)"),
                "version output should be the documented loose-classpath fallback; was: " + streams.out()
        );
    }
}
