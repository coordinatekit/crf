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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    /** The escape that opens every ANSI control sequence; its absence proves uncolored output. */
    private static final String ANSI_ESCAPE = "\u001b";

    /** A run of mark glyphs unique to the brand banner; a clean marker that the art was rendered. */
    private static final String BANNER_MARKER = "#########";

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
    void run__annotateHelpOmitsBanner() {
        // ACT //
        int exitCode = CrfLauncher.run(new String[] {"annotate", "--help"});

        // ASSERT //
        assertEquals(0, exitCode);
        assertFalse(
                streams.out().contains(BANNER_MARKER),
                "subcommand help should not carry the root banner; was: " + streams.out()
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
        // The banner renders above the usage block...
        assertTrue(streams.out().contains(BANNER_MARKER), "help should show the brand banner; was: " + streams.out());
        assertTrue(
                streams.out().indexOf(BANNER_MARKER) < streams.out().indexOf("Usage"),
                "the banner should appear above the usage block; was: " + streams.out()
        );
        // ...and the test runs under a dumb terminal, so it must be uncolored.
        assertFalse(streams.out().contains(ANSI_ESCAPE), "captured help output must carry no ANSI bytes");
    }

    @Test
    void run__noSubcommandPrintsUsageAndReturnsTwo() {
        // ACT //
        int exitCode = CrfLauncher.run(new String[] {});

        // ASSERT //
        assertEquals(2, exitCode);
        assertTrue(streams.err().contains("annotate"), "usage should list the subcommands; was: " + streams.err());
        assertTrue(
                streams.err().contains(BANNER_MARKER),
                "the bare command should show the brand banner; was: " + streams.err()
        );
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
        assertTrue(streams.out().contains("crf"), "version output should name the tool; was: " + streams.out());
        assertFalse(
                streams.out().contains(BANNER_MARKER),
                "the version output should not carry the banner; was: " + streams.out()
        );
    }
}
