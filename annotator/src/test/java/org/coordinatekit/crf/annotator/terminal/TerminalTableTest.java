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
package org.coordinatekit.crf.annotator.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.coordinatekit.crf.annotator.AnnotatorTestSupport;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

class TerminalTableTest {
    private static final String BOLD_YELLOW = AnnotatorTestSupport.boldYellowEscape();
    private static final String NEW_LINE = System.lineSeparator();

    private static String render(TerminalTable table) {
        AttributedStringBuilder builder = new AttributedStringBuilder();
        table.appendTo(builder);
        return builder.toAttributedString().toString();
    }

    @Test
    void appendTo__appliesRowStyleToDataRowsOnly() {
        // ARRANGE //
        TerminalTable table = TerminalTable.builder().column("Tag")
                .row(AttributedStyle.BOLD.foreground(AttributedStyle.YELLOW), "Noun").build();
        AttributedStringBuilder builder = new AttributedStringBuilder();

        // ACT //
        table.appendTo(builder);
        List<String> lines = builder.toAttributedString().toAnsi().lines().toList();

        // ASSERT //
        assertFalse(lines.get(0).contains(BOLD_YELLOW), "header row must not be styled");
        assertFalse(lines.get(1).contains(BOLD_YELLOW), "dash row must not be styled");
        assertTrue(lines.get(2).contains(BOLD_YELLOW), "data row must carry the supplied style");
    }

    @Test
    void appendTo__cappedColumnTruncatesWithEllipsis() {
        // ARRANGE //
        TerminalTable table = TerminalTable.builder().column("Token", 5).row(AttributedStyle.DEFAULT, "abcdefgh")
                .build();

        // ACT //
        String rendered = render(table);

        // ASSERT //
        String expected = "Token" + NEW_LINE + "-----" + NEW_LINE + "abcd…" + NEW_LINE;
        assertEquals(expected, rendered);
    }

    @Test
    void appendTo__rendersHeaderSeparatorAndPaddedRows() {
        // ARRANGE //
        TerminalTable table = TerminalTable.builder().column("##").column("Tag").column("Confidence")
                .row(AttributedStyle.DEFAULT, "1", "Noun", "0.9000").row(AttributedStyle.DEFAULT, "2", "Verb", "0.1000")
                .build();

        // ACT //
        String rendered = render(table);

        // ASSERT //
        String expected = "##  Tag   Confidence" + NEW_LINE + "--  ----  ----------" + NEW_LINE + "1   Noun  0.9000    "
                + NEW_LINE + "2   Verb  0.1000    " + NEW_LINE;
        assertEquals(expected, rendered);
    }

    record WrapParameters(String name, int terminalWidth, String features, String expected) {}

    static Stream<WrapParameters> appendTo__wrapsLastColumn() {
        String shortHeader = "##  Token  Features" + NEW_LINE + "--  -----  --------" + NEW_LINE;
        String wideHeader = "##  Token  Features     " + NEW_LINE + "--  -----  -------------" + NEW_LINE;
        String cappedHeader = "##  Token  Features " + NEW_LINE + "--  -----  ---------" + NEW_LINE;
        return Stream.of(
                new WrapParameters("unwrapped_when_fits", 80, "A, B, C", shortHeader + "1   fox    A, B, C" + NEW_LINE),
                new WrapParameters(
                        "wraps_with_trailing_comma_marker",
                        20,
                        "AAA, BBB, CCC",
                        cappedHeader + "1   fox    AAA, BBB," + NEW_LINE + " ".repeat(11) + "CCC" + NEW_LINE
                ),
                new WrapParameters(
                        "no_wrap_when_width_zero",
                        0,
                        "AAA, BBB, CCC",
                        wideHeader + "1   fox    AAA, BBB, CCC" + NEW_LINE
                ),
                new WrapParameters("placeholder_em_dash_not_padded", 80, "—", shortHeader + "1   fox    —" + NEW_LINE),
                new WrapParameters(
                        "cjk_feature_wraps_by_display_width",
                        19,
                        "中文, 字",
                        shortHeader + "1   fox    中文," + NEW_LINE + " ".repeat(11) + "字" + NEW_LINE
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void appendTo__wrapsLastColumn(WrapParameters parameters) {
        // ARRANGE //
        TerminalTable table = TerminalTable.builder().terminalWidth(parameters.terminalWidth()).column("##")
                .column("Token", 30).wrappingColumn("Features", ", ")
                .row(AttributedStyle.DEFAULT, "1", "fox", parameters.features()).build();

        // ACT //
        String rendered = render(table);

        // ASSERT //
        assertEquals(parameters.expected(), rendered);
    }

    record BuildExceptionParameters(
            String name,
            Executable action,
            Class<? extends Exception> expectedClass,
            String expectedMessage
    ) {}

    static Stream<BuildExceptionParameters> build__exception() {
        return Stream.of(
                new BuildExceptionParameters(
                        "no_columns",
                        () -> TerminalTable.builder().build(),
                        IllegalStateException.class,
                        "at least one column must be declared"
                ),
                new BuildExceptionParameters(
                        "row_cell_count_mismatch",
                        () -> TerminalTable.builder().column("A").column("B").row(AttributedStyle.DEFAULT, "x").build(),
                        IllegalStateException.class,
                        "row has 1 cells but 2 columns were declared"
                ),
                new BuildExceptionParameters(
                        "wrapping_column_not_last",
                        () -> TerminalTable.builder().wrappingColumn("Features", ", ").column("Tag").build(),
                        IllegalStateException.class,
                        "a wrapping column must be the last column"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void build__exception(BuildExceptionParameters parameters) {
        // ACT //
        Exception exception = assertThrows(parameters.expectedClass(), parameters.action());

        // ASSERT //
        assertEquals(parameters.expectedMessage(), exception.getMessage());
    }

    record DisplayWidthParameters(String name, String text, int expectedWidth) {}

    static Stream<DisplayWidthParameters> displayWidth() {
        return Stream.of(
                new DisplayWidthParameters("ascii", "abc", 3),
                new DisplayWidthParameters("control_char_contributes_zero", "a\u0001b", 2),
                new DisplayWidthParameters("empty", "", 0),
                new DisplayWidthParameters("wide_cjk_codepoint_among_ascii", "a中b", 4),
                new DisplayWidthParameters("zero_width_combining_mark", "é", 1)
        );
    }

    @MethodSource
    @ParameterizedTest
    void displayWidth(DisplayWidthParameters parameters) {
        // ACT //
        int actual = TerminalTable.displayWidth(parameters.text());

        // ASSERT //
        assertEquals(parameters.expectedWidth(), actual);
    }

    record PadToWidthParameters(String name, String text, int cells, String expected) {}

    static Stream<PadToWidthParameters> padToWidth() {
        return Stream.of(
                new PadToWidthParameters("pads_to_target", "ab", 5, "ab   "),
                new PadToWidthParameters("no_op_when_at_width", "abc", 3, "abc"),
                new PadToWidthParameters("no_op_when_wider", "abcde", 3, "abcde")
        );
    }

    @MethodSource
    @ParameterizedTest
    void padToWidth(PadToWidthParameters parameters) {
        // ACT //
        String actual = TerminalTable.padToWidth(parameters.text(), parameters.cells());

        // ASSERT //
        assertEquals(parameters.expected(), actual);
    }

    record TruncateToWidthParameters(String name, String text, int cells, String expected) {}

    static Stream<TruncateToWidthParameters> truncateToWidth() {
        return Stream.of(
                new TruncateToWidthParameters("no_op_when_fits", "ab", 5, "ab"),
                new TruncateToWidthParameters("no_op_when_exactly_fits", "abcd", 4, "abcd"),
                new TruncateToWidthParameters("truncates_ascii_with_ellipsis", "abcdef", 4, "abc…"),
                new TruncateToWidthParameters("truncates_wide_codepoints", "中文字", 4, "中…")
        );
    }

    @MethodSource
    @ParameterizedTest
    void truncateToWidth(TruncateToWidthParameters parameters) {
        // ACT //
        String actual = TerminalTable.truncateToWidth(parameters.text(), parameters.cells());

        // ASSERT //
        assertEquals(parameters.expected(), actual);
    }
}
