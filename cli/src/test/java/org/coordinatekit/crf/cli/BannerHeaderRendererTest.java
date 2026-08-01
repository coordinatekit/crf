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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Help.ColorScheme;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Tests the crf-owned slice of the banner wiring: the "CRF" product art resolving and the ANSI
 * hand-off from picocli's color scheme. crf's own wordmark ({@link #CRF_BIG_MARKER} /
 * {@link #CRF_SMALL_MARKER}) is the contract asserted here; {@link #LIBRARY_MARK_MARKER} is only a
 * smoke test that the hand-off to {@code org.coordinatekit.foundation.cli.brand.Banner} composed at
 * all. The layout/color ladders themselves belong to that library and are tested there, not here.
 */
class BannerHeaderRendererTest {
    /** The escape that opens every ANSI control sequence; its absence proves uncolored output. */
    private static final String ANSI_ESCAPE = "\u001b";

    /** The last row of {@code crf-big.txt}; the wide rungs of the library's ladder render this one. */
    private static final String CRF_BIG_MARKER = " \\_____|_|  \\_\\_|";

    /** The last row of {@code crf-small.txt}; the 80-column non-TTY fallback renders this one. */
    private static final String CRF_SMALL_MARKER = " \\___|_|_\\_|";

    /** A run of glyphs from cli-brand's own mark art; a smoke test that the library composed at all. */
    private static final String LIBRARY_MARK_MARKER = "#########";

    @Test
    void render__ansiOffEmitsNoEscapes() {
        // ARRANGE //
        CommandLine.Help help = help(Ansi.OFF);

        // ACT //
        String rendered = new BannerHeaderRenderer().render(help);

        // ASSERT //
        assertFalse(rendered.contains(ANSI_ESCAPE), "Ansi.OFF should emit no escape sequences; was: " + rendered);
    }

    @Test
    void render__composesLibraryMark() {
        // ARRANGE //
        CommandLine.Help help = help(Ansi.OFF);

        // ACT //
        String rendered = new BannerHeaderRenderer().render(help);

        // ASSERT //
        assertTrue(
                rendered.contains(LIBRARY_MARK_MARKER),
                "the library should have composed its mark into the banner; was: " + rendered
        );
    }

    record RenderExceptionParameters(
            String name,
            Executable action,
            Class<? extends Exception> expectedClass,
            String expectedMessage
    ) {}

    @SuppressWarnings({"DataFlowIssue", "NullAway"})
    static Stream<RenderExceptionParameters> render__exception() {
        return Stream.of(
                new RenderExceptionParameters(
                        "null_banner_renderer",
                        () -> new BannerHeaderRenderer(null),
                        NullPointerException.class,
                        "bannerRenderer must not be null"
                ),
                new RenderExceptionParameters(
                        "null_help",
                        () -> new BannerHeaderRenderer().render(null),
                        NullPointerException.class,
                        "help must not be null"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void render__exception(RenderExceptionParameters parameters) {
        // ACT //
        Exception exception = assertThrows(parameters.expectedClass(), parameters.action());

        // ASSERT //
        assertEquals(parameters.expectedMessage(), exception.getMessage());
    }

    record ForwardsAnsiDecisionParameters(String name, Ansi ansi, boolean expectedAnsiEnabled) {}

    static Stream<ForwardsAnsiDecisionParameters> render__forwardsAnsiDecision() {
        return Stream.of(
                new ForwardsAnsiDecisionParameters("ansi_off", Ansi.OFF, false),
                new ForwardsAnsiDecisionParameters("ansi_on", Ansi.ON, true)
        );
    }

    @MethodSource
    @ParameterizedTest
    void render__forwardsAnsiDecision(ForwardsAnsiDecisionParameters parameters) {
        // ARRANGE //
        List<Boolean> received = new ArrayList<>();
        BannerHeaderRenderer renderer = new BannerHeaderRenderer(ansiEnabled -> {
            received.add(ansiEnabled);
            return "banner";
        });

        // ACT //
        String rendered = renderer.render(help(parameters.ansi()));

        // ASSERT //
        assertAll(
                () -> assertEquals(List.of(parameters.expectedAnsiEnabled()), received, "forwarded decision"),
                () -> assertEquals("banner", rendered, "the library's output is returned verbatim")
        );
    }

    @Test
    void render__includesCrfWordmark() {
        // ARRANGE //
        CommandLine.Help help = help(Ansi.OFF);

        // ACT //
        String rendered = new BannerHeaderRenderer().render(help);

        // ASSERT //
        // The library chooses big or small art by terminal width; either proves crf's own art
        // reached the render, so the assertion does not depend on how wide the terminal is.
        assertTrue(
                rendered.contains(CRF_SMALL_MARKER) || rendered.contains(CRF_BIG_MARKER),
                "rendered banner should carry the CRF wordmark; was: " + rendered
        );
    }

    private static CommandLine.Help help(Ansi ansi) {
        CommandSpec commandSpec = CommandSpec.forAnnotatedObject(new RootCommand());
        ColorScheme colorScheme = new ColorScheme.Builder().ansi(ansi).build();
        return new CommandLine.Help(commandSpec, colorScheme);
    }
}
