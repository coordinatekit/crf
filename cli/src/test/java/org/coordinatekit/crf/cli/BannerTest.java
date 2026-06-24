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

import org.coordinatekit.crf.cli.Banner.ColorMode;
import org.coordinatekit.crf.cli.Banner.ColorRole;
import org.coordinatekit.crf.cli.Banner.Segment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Unit tests for the pure {@link Banner#compose(int, ColorMode, Banner.Product)} layout/color
 * ladders and the {@link Banner#markSegments(String)} glyph-coloring rule. Everything here drives
 * width and color mode directly, so no terminal is involved and the assertions describe structure
 * (mark presence, width bands, SGR form) rather than exact art.
 */
class BannerTest {
    /**
     * The CRF wordmark that drives the layout and color ladders; mirrors what {@code CrfLauncher}
     * builds.
     */
    private static final Banner.Product CRF = Banner.Product
            .fromResources(Banner.class, "banner/crf-big.txt", "banner/crf-small.txt", 91, 199, 227);

    /** The escape that opens every ANSI control sequence. */
    private static final String ESCAPE = "\u001b";

    /** The globe glyph appears only in the mark, never in the figlet words — a clean mark marker. */
    private static final String MARK_GLYPH = "+";

    /** The mark is 45 columns wide; mirrors {@code Banner.MARK_WIDTH}. */
    private static final int MARK_WIDTH = 45;

    static Stream<ComposeWidthParameters> compose__markPresence() {
        return Stream.of(
                new ComposeWidthParameters("full_big_shows_mark", 100, true),
                new ComposeWidthParameters("full_small_shows_mark", 78, true),
                new ComposeWidthParameters("crf_big_with_mark", 50, true),
                new ComposeWidthParameters("crf_big_no_mark", 30, false),
                new ComposeWidthParameters("crf_small_no_mark", 16, false)
        );
    }

    @MethodSource
    @ParameterizedTest
    void compose__markPresence(ComposeWidthParameters parameters) {
        // ACT //
        String art = Banner.compose(parameters.width(), ColorMode.MONOCHROME, CRF);

        // ASSERT //
        assertEquals(
                parameters.expectMark(),
                art.contains(MARK_GLYPH),
                "mark presence at width " + parameters.width() + " should be " + parameters.expectMark()
        );
    }

    static Stream<MarkRowParameters> markSegments() {
        return Stream.of(
                new MarkRowParameters(
                        "plain_glyphs",
                        "#+-.: ",
                        List.of(
                                ColorRole.PIN,
                                ColorRole.GLOBE,
                                ColorRole.PIN,
                                ColorRole.PIN,
                                ColorRole.PIN,
                                ColorRole.NONE
                        )
                ),
                new MarkRowParameters("equals_after_pin_is_pin", "#=", List.of(ColorRole.PIN, ColorRole.PIN)),
                new MarkRowParameters("equals_after_globe_is_globe", "+=", List.of(ColorRole.GLOBE, ColorRole.GLOBE)),
                new MarkRowParameters("equals_after_space_is_globe", " =", List.of(ColorRole.NONE, ColorRole.GLOBE)),
                new MarkRowParameters(
                        "equals_chain_stays_pin",
                        "#==",
                        List.of(ColorRole.PIN, ColorRole.PIN, ColorRole.PIN)
                ),
                new MarkRowParameters("equals_at_line_start_is_globe", "=", List.of(ColorRole.GLOBE)),
                new MarkRowParameters(
                        "plus_between_hashes_is_pin",
                        "#+#",
                        List.of(ColorRole.PIN, ColorRole.PIN, ColorRole.PIN)
                ),
                new MarkRowParameters(
                        "equals_between_hashes_is_pin",
                        "#=#",
                        List.of(ColorRole.PIN, ColorRole.PIN, ColorRole.PIN)
                ),
                new MarkRowParameters("plus_left_of_left_hash_is_globe", "+#", List.of(ColorRole.GLOBE, ColorRole.PIN)),
                new MarkRowParameters(
                        "plus_right_of_right_hash_is_globe",
                        "#+",
                        List.of(ColorRole.PIN, ColorRole.GLOBE)
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void markSegments(MarkRowParameters parameters) {
        // ACT //
        List<ColorRole> roles = new ArrayList<>();
        for (Segment segment : Banner.markSegments(parameters.row())) {
            for (int i = 0; i < segment.text().length(); i++) {
                roles.add(segment.role());
            }
        }

        // ASSERT //
        assertEquals(parameters.expectedRoles(), roles);
    }

    @Test
    void compose__belowSmallestTierIsEmpty() {
        // ACT //
        String art = Banner.compose(13, ColorMode.TRUECOLOR, CRF);

        // ASSERT //
        assertEquals("", art);
    }

    @Test
    void compose__c16EmitsSixteenColorSgrNotIndexedOrRgb() {
        // ACT //
        String art = Banner.compose(100, ColorMode.C16, CRF);

        // ASSERT //
        assertTrue(art.contains(ESCAPE + "["), "16-color output should contain SGR sequences");
        assertFalse(art.contains("38;5;"), "16-color output must not use the 256-indexed form");
        assertFalse(art.contains("38;2;"), "16-color output must not use the truecolor form");
    }

    @Test
    void compose__c256EmitsIndexedNotRgb() {
        // ACT //
        String art = Banner.compose(100, ColorMode.C256, CRF);

        // ASSERT //
        assertTrue(art.contains("38;5;"), "256-color output should use the indexed form");
        assertFalse(art.contains("38;2;"), "256-color output must not use the truecolor form");
    }

    @Test
    void compose__monochromeEmitsNoAnsi() {
        // ACT //
        String art = Banner.compose(100, ColorMode.MONOCHROME, CRF);

        // ASSERT //
        assertFalse(art.contains(ESCAPE), "monochrome output must carry zero ANSI bytes");
    }

    @Test
    void compose__nameShownOnlyForWideTiers() {
        // ACT //
        int fullBig = widestLine(Banner.compose(100, ColorMode.MONOCHROME, CRF));
        int fullSmall = widestLine(Banner.compose(78, ColorMode.MONOCHROME, CRF));
        int crfWithMark = widestLine(Banner.compose(50, ColorMode.MONOCHROME, CRF));

        // ASSERT //
        assertTrue(fullBig > fullSmall, "the big name banner is wider than the small one");
        assertTrue(fullSmall > crfWithMark, "the name tiers are wider than the CRF-only tier");
        assertTrue(fullSmall >= 70, "the name tiers span the full composed width");
        assertTrue(crfWithMark <= MARK_WIDTH, "the CRF-only block is no wider than the mark");
    }

    @Test
    void compose__truecolorColorsMarkAndText() {
        // ACT //
        String art = Banner.compose(100, ColorMode.TRUECOLOR, CRF);

        // ASSERT //
        assertTrue(art.contains("38;2;"), "truecolor output should use the RGB form");
        assertTrue(art.contains("38;2;107;126;145"), "mark pin glyphs should carry the lightened pin color");
        assertTrue(art.contains("38;2;74;163;68"), "mark globe glyphs should carry the globe color");
        assertTrue(art.contains("38;2;252;142;45"), "the CoordinateKit word should carry the brand color");
        assertTrue(art.contains("38;2;91;199;227"), "the CRF word should carry the product color");
    }

    /** Returns the length of the longest line in a rendered (monochrome) art block. */
    private static int widestLine(String art) {
        int widest = 0;
        for (String line : art.split("\n", -1)) {
            widest = Math.max(widest, line.length());
        }
        return widest;
    }

    record ComposeWidthParameters(String name, int width, boolean expectMark) {}

    record MarkRowParameters(String name, String row, List<ColorRole> expectedRoles) {}
}
