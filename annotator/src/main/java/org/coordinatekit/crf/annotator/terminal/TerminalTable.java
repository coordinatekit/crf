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

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.WCWidth;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Renders a set of aligned text columns to a JLine {@link AttributedStringBuilder}: a header row, a
 * dash-underline row, and styled data rows, each cell padded to a computed column width and joined
 * by a configurable gutter.
 *
 * <p>
 * Each column is sized to the larger of its header width and its widest cell. A column may declare
 * a maximum width, in which case its data cells contribute at most that many cells to the width and
 * are truncated with an ellipsis when they exceed it; the header is never truncated and remains a
 * floor. The last column may instead be a {@link Builder#wrappingColumn(String, String) wrapping
 * column}, whose cell content wraps to the terminal width with continuation lines indented to align
 * under the column. The rendered table is appended via {@link #appendTo(AttributedStringBuilder)}.
 *
 * <p>
 * This is deliberately a narrow aligned-columns helper, not a bordered-grid renderer. Instances are
 * built via {@link #builder()} and are immutable once built.
 */
final class TerminalTable {
    private final int[] columnWidths;
    private final List<ColumnSpecification> columns;
    private final List<Row> rows;
    private final String separator;
    private final int separatorWidth;
    private final int terminalWidth;

    /**
     * Creates a table from {@code builder}, copying its columns and rows and computing each column's
     * width.
     *
     * @param builder the builder holding the columns, rows, separator, and terminal width
     */
    private TerminalTable(Builder builder) {
        this.columns = List.copyOf(builder.columns);
        this.rows = List.copyOf(builder.rows);
        this.separator = builder.separator;
        this.separatorWidth = displayWidth(builder.separator);
        this.terminalWidth = builder.terminalWidth;
        this.columnWidths = computeColumnWidths(this.columns, this.rows);
    }

    /**
     * Appends a single data row to {@code builder}. When the last column is a wrapping column, its cell
     * is wrapped to the terminal width with continuation lines indented to align under the column;
     * otherwise every cell renders on one line. Each line is terminated by
     * {@link System#lineSeparator()}.
     *
     * @param builder the builder to append to
     * @param row the row to render
     */
    private void appendDataRow(AttributedStringBuilder builder, Row row) {
        int lastColumn = columns.size() - 1;
        String wrapSeparator = columns.get(lastColumn).wrapSeparator();
        if (wrapSeparator == null) {
            List<String> cells = new ArrayList<>(columns.size());
            for (int column = 0; column < columns.size(); column++) {
                String cell = row.cells().get(column);
                cells.add(padToWidth(truncateToWidth(cell, columnWidths[column]), columnWidths[column]));
            }
            appendRow(builder, row.style(), separator, cells);
            builder.append(System.lineSeparator());
            return;
        }
        List<String> cells = new ArrayList<>(columns.size());
        for (int column = 0; column < lastColumn; column++) {
            String cell = row.cells().get(column);
            cells.add(padToWidth(truncateToWidth(cell, columnWidths[column]), columnWidths[column]));
        }
        int indent = offsetOfColumn(lastColumn);
        List<String> wrappedLines = wrap(row.cells().get(lastColumn), wrapSeparator, indent);
        cells.add(wrappedLines.getFirst());
        appendRow(builder, row.style(), separator, cells);
        builder.append(System.lineSeparator());
        String indentPadding = " ".repeat(indent);
        for (int line = 1; line < wrappedLines.size(); line++) {
            builder.append(indentPadding);
            builder.append(new AttributedString(wrappedLines.get(line), row.style()));
            builder.append(System.lineSeparator());
        }
    }

    /**
     * Appends {@code cells} to {@code builder}, separated by {@code separator}. Each cell is styled
     * with {@code style}; the separators are always rendered with {@link AttributedStyle#DEFAULT}.
     *
     * @param builder the builder to append to
     * @param style the style applied to the cells
     * @param separator the gutter rendered between adjacent cells
     * @param cells the cell contents, in column order
     */
    private static void appendRow(
            AttributedStringBuilder builder,
            AttributedStyle style,
            String separator,
            List<String> cells
    ) {
        for (int index = 0; index < cells.size(); index++) {
            if (index > 0) {
                builder.append(separator, AttributedStyle.DEFAULT);
            }
            builder.append(new AttributedString(cells.get(index), style));
        }
    }

    /**
     * Appends the rendered table to {@code builder}: the header row, a dash-underline row, and each
     * data row, every line terminated by {@link System#lineSeparator()}. The header and dash rows use
     * {@link AttributedStyle#DEFAULT}; each data row uses the style supplied when it was added.
     *
     * @param builder the builder to append to
     */
    void appendTo(AttributedStringBuilder builder) {
        List<String> headerCells = new ArrayList<>(columns.size());
        List<String> dashCells = new ArrayList<>(columns.size());
        for (int column = 0; column < columns.size(); column++) {
            headerCells.add(padToWidth(columns.get(column).header(), columnWidths[column]));
            dashCells.add("-".repeat(columnWidths[column]));
        }
        appendRow(builder, AttributedStyle.DEFAULT, separator, headerCells);
        builder.append(System.lineSeparator());
        appendRow(builder, AttributedStyle.DEFAULT, separator, dashCells);
        builder.append(System.lineSeparator());
        for (Row row : rows) {
            appendDataRow(builder, row);
        }
    }

    /**
     * Returns a new builder with an empty column and row set and the default two-space gutter.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Computes each column's width as the larger of its header width and its widest cell. A column that
     * declares a maximum width caps each cell's contribution at that width, though the header width is
     * always honoured as a floor. A wrapping last column is additionally capped to the terminal width
     * remaining after the preceding columns, so its dash underline and header padding do not extend
     * past the screen; its cells wrap to that same width when rendered.
     *
     * @param columns the column specifications, in column order
     * @param rows the data rows
     * @return the width, in cells, of each column
     */
    private int[] computeColumnWidths(List<ColumnSpecification> columns, List<Row> rows) {
        int[] widths = new int[columns.size()];
        for (int column = 0; column < columns.size(); column++) {
            ColumnSpecification specification = columns.get(column);
            int width = displayWidth(specification.header());
            for (Row row : rows) {
                int cellWidth = displayWidth(row.cells().get(column));
                if (specification.maximumWidth() > 0) {
                    cellWidth = Math.min(specification.maximumWidth(), cellWidth);
                }
                width = Math.max(width, cellWidth);
            }
            widths[column] = width;
        }
        int lastColumn = columns.size() - 1;
        if (columns.get(lastColumn).wrapSeparator() != null && terminalWidth > 0) {
            int offset = 0;
            for (int column = 0; column < lastColumn; column++) {
                offset += widths[column] + separatorWidth;
            }
            int available = terminalWidth - offset;
            if (available > 0) {
                widths[lastColumn] = Math.min(widths[lastColumn], available);
            }
        }
        return widths;
    }

    /**
     * Returns the display width of {@code text} in terminal cells, honouring wide (for example CJK)
     * codepoints via {@link WCWidth}. Codepoints with non-positive width contribute nothing.
     *
     * @param text the text to measure
     * @return the display width in cells
     */
    static int displayWidth(CharSequence text) {
        int total = 0;
        int index = 0;
        int length = text.length();
        while (index < length) {
            int codePoint = Character.codePointAt(text, index);
            int width = WCWidth.wcwidth(codePoint);
            if (width > 0) {
                total += width;
            }
            index += Character.charCount(codePoint);
        }
        return total;
    }

    /**
     * Returns the left offset, in terminal cells, at which the column at {@code columnIndex} starts:
     * the sum of the widths of all preceding columns plus one gutter per preceding column. For
     * {@code columnIndex == 0} this is {@code 0}.
     *
     * @param columnIndex the zero-based column index
     * @return the column's starting offset in cells
     */
    private int offsetOfColumn(int columnIndex) {
        int offset = 0;
        for (int column = 0; column < columnIndex; column++) {
            offset += columnWidths[column] + separatorWidth;
        }
        return offset;
    }

    /**
     * Right-pads {@code text} with spaces so that it occupies at least {@code cells} terminal cells.
     * Text already at or beyond the target width is returned unchanged.
     *
     * @param text the text to pad
     * @param cells the target width in cells
     * @return the padded text
     */
    static String padToWidth(String text, int cells) {
        int width = displayWidth(text);
        if (width >= cells) {
            return text;
        }
        StringBuilder builder = new StringBuilder(text);
        builder.repeat(' ', cells - width);
        return builder.toString();
    }

    /**
     * Truncates {@code text} so that it fits within {@code cells} terminal cells, replacing the
     * overflow with a trailing ellipsis ({@code …}). Text that already fits is returned unchanged.
     *
     * @param text the text to truncate
     * @param cells the maximum width in cells
     * @return the truncated text, fitting within {@code cells} cells
     */
    static String truncateToWidth(String text, int cells) {
        if (displayWidth(text) <= cells) {
            return text;
        }
        StringBuilder builder = new StringBuilder();
        int accumulated = 0;
        int index = 0;
        int length = text.length();
        int limit = cells - 1;
        while (index < length) {
            int codePoint = Character.codePointAt(text, index);
            int width = WCWidth.wcwidth(codePoint);
            int positiveWidth = Math.max(width, 0);
            if (accumulated + positiveWidth > limit) {
                break;
            }
            builder.appendCodePoint(codePoint);
            accumulated += positiveWidth;
            index += Character.charCount(codePoint);
        }
        builder.append('…');
        return builder.toString();
    }

    /**
     * Wraps {@code text} so that each line fits within {@code terminalWidth - indent} cells, breaking
     * only on {@code wrapSeparator} boundaries. The returned list always has at least one entry; every
     * non-final line keeps a trailing continuation marker ({@code wrapSeparator} with trailing
     * whitespace stripped, for example {@code ","} for {@code ", "}) to signal that the content
     * continues. A single segment wider than the available width is not broken further.
     *
     * <p>
     * If {@code terminalWidth} is non-positive or {@code indent} leaves no room for content, the text
     * is returned unwrapped as a single line.
     *
     * @param text the cell content to wrap
     * @param wrapSeparator the boundary on which the content may be broken, treated literally
     * @param indent the number of cells the column is indented from the left margin
     * @return the wrapped lines, never empty
     */
    private List<String> wrap(String text, String wrapSeparator, int indent) {
        int available = terminalWidth - indent;
        if (terminalWidth <= 0 || available < 1) {
            return List.of(text);
        }
        String continuationMarker = wrapSeparator.stripTrailing();
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String segment : text.split(Pattern.quote(wrapSeparator), -1)) {
            if (current.isEmpty()) {
                current.append(segment);
            } else if (displayWidth(current) + displayWidth(wrapSeparator) + displayWidth(segment)
                    + displayWidth(continuationMarker) > available) {
                lines.add(current + continuationMarker);
                current = new StringBuilder(segment);
            } else {
                current.append(wrapSeparator).append(segment);
            }
        }
        lines.add(current.toString());
        return lines;
    }

    private record ColumnSpecification(String header, int maximumWidth, @Nullable String wrapSeparator) {}

    private record Row(AttributedStyle style, List<String> cells) {}

    /**
     * Builder for {@link TerminalTable}. Columns and rows may be added in any order; every row must
     * carry exactly one cell per declared column, which is checked by {@link #build()}.
     */
    static final class Builder {
        private final List<ColumnSpecification> columns = new ArrayList<>();
        private final List<Row> rows = new ArrayList<>();
        private final String separator = "  ";
        private int terminalWidth = 0;

        private Builder() {}

        /**
         * Builds the table, computing each column's width.
         *
         * @return a new {@link TerminalTable}
         * @throws IllegalStateException if no columns were declared, if a wrapping column is not the last
         *         column, or if a row's cell count does not match the column count
         */
        TerminalTable build() {
            if (columns.isEmpty()) {
                throw new IllegalStateException("at least one column must be declared");
            }
            for (int column = 0; column < columns.size() - 1; column++) {
                if (columns.get(column).wrapSeparator() != null) {
                    throw new IllegalStateException("a wrapping column must be the last column");
                }
            }
            for (Row row : rows) {
                if (row.cells().size() != columns.size()) {
                    throw new IllegalStateException(
                            "row has " + row.cells().size() + " cells but " + columns.size() + " columns were declared"
                    );
                }
            }
            return new TerminalTable(this);
        }

        /**
         * Declares an uncapped column whose width grows to fit its widest cell.
         *
         * @param header the column header, must not be null
         * @return this builder
         */
        Builder column(String header) {
            return column(header, 0);
        }

        /**
         * Declares a column whose data cells contribute at most {@code maximumWidth} cells to the column
         * width and are truncated when they exceed it. A non-positive {@code maximumWidth} leaves the
         * column uncapped.
         *
         * @param header the column header, must not be null
         * @param maximumWidth the maximum data-cell width in cells, or non-positive for uncapped
         * @return this builder
         */
        Builder column(String header, int maximumWidth) {
            columns.add(
                    new ColumnSpecification(
                            Objects.requireNonNull(header, "header must not be null"),
                            maximumWidth,
                            null
                    )
            );
            return this;
        }

        /**
         * Adds a data row, styled with {@code style}.
         *
         * @param style the style applied to the row's cells
         * @param cells the cell contents, one per column in column order
         * @return this builder
         */
        Builder row(AttributedStyle style, String... cells) {
            rows.add(new Row(Objects.requireNonNull(style, "style must not be null"), List.of(cells)));
            return this;
        }

        /**
         * Sets the terminal width, in cells, used to wrap {@link #wrappingColumn(String, String) wrapping
         * columns}. A non-positive width disables wrapping, so wrapping cells render on a single line.
         * Defaults to {@code 0}.
         *
         * @param terminalWidth the terminal width in cells
         * @return this builder
         */
        Builder terminalWidth(int terminalWidth) {
            this.terminalWidth = terminalWidth;
            return this;
        }

        /**
         * Declares an uncapped, wrapping last column. Its cell content is wrapped to the terminal width
         * (see {@link #terminalWidth(int)}), breaking only on {@code separator} boundaries, with
         * continuation lines indented to align under the column. A wrapping column must be the last column
         * declared, which is checked by {@link #build()}.
         *
         * @param header the column header, must not be null
         * @param separator the boundary on which the cell content may be wrapped, must not be null
         * @return this builder
         */
        Builder wrappingColumn(String header, String separator) {
            columns.add(
                    new ColumnSpecification(
                            Objects.requireNonNull(header, "header must not be null"),
                            0,
                            Objects.requireNonNull(separator, "separator must not be null")
                    )
            );
            return this;
        }
    }
}
