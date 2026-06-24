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

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedCharSequence;
import org.jline.utils.AttributedCharSequence.ForceMode;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.Colors;
import org.jline.utils.InfoCmp;

import picocli.CommandLine;

/**
 * Renders the CoordinateKit brand art — the globe "mark" stacked above a figlet-style word banner —
 * as the header section of a tool's root usage message. Registered as the
 * {@link picocli.CommandLine.Model.UsageMessageSpec#SECTION_KEY_HEADER} renderer on the root
 * command only (see {@code CrfLauncher}), so {@code --help} and a bare command both show it while
 * subcommands and {@code --version} do not.
 *
 * <p>
 * The class is written to be shared verbatim across CoordinateKit command-line tools: the globe
 * mark, the "CoordinateKit" wordmark, the layout/color engine, and the brand colors are fixed,
 * while the per-tool <em>product</em> wordmark and its accent color are supplied through a
 * {@link Product} passed to the constructor. Nothing here names a specific tool; the owning
 * launcher builds its {@link Product} and only the product's {@code .txt} art differs between
 * tools.
 *
 * <p>
 * The art adapts to the terminal in two independent ways. <strong>Width</strong> selects a
 * {@link Layout}: the full mark plus "CoordinateKit &lt;product&gt;" when there is room, dropping
 * the brand name and then the mark as the window narrows, down to nothing below the smallest
 * legible size (see {@link #compose}). Each layout's threshold is derived from the art it renders,
 * so any product wordmark width adapts without hand-tuned numbers. <strong>Color
 * capability</strong> selects a {@link ColorMode}: 24-bit truecolor, 256-color, or 16-color when
 * attached to a capable terminal, and uncolored plain text otherwise — including whenever output is
 * piped or redirected, so a captured stream never carries stray ANSI bytes.
 *
 * <p>
 * The brand glyphs live as {@code .txt} classpath resources rather than Java text blocks because
 * the art is alignment-significant: trailing spaces carry meaning, and Spotless trims them from
 * {@code *.java} sources but leaves resources untouched.
 */
@NullMarked
public final class Banner implements CommandLine.IHelpSectionRenderer {
    /** Color capability tiers, from least to richest, used to pick how RGB is emitted. */
    enum ColorMode {
        C16, C256, MONOCHROME, TRUECOLOR
    }

    /**
     * The brand colors a cell can take. RGB values drive truecolor and are rounded down for the 256-
     * and 16-color modes. Constants name what the text <em>is</em> rather than its hue, so the same
     * role keeps its meaning even where a sibling tool paints it a different color.
     */
    enum ColorRole {
        /** The "CoordinateKit" wordmark color. */
        BRAND(252, 142, 45),
        /** The globe fill woven through the mark. */
        GLOBE(74, 163, 68),
        /** No styling; used for spaces and padding. */
        NONE(-1, -1, -1),
        /**
         * The map-pin color. The logo's pin {@code #2F3C49} is near-black and washes out on dark terminals,
         * so it is lightened to a blue-gray that reads on both dark and light backgrounds.
         */
        PIN(107, 126, 145),
        /**
         * The product wordmark color. Unlike the other roles its RGB is not fixed here: it is supplied by
         * the active {@link Product}, so each tool paints its own name in its own accent. The placeholder
         * components below are never read — {@link #styleFor} resolves this role from the product instead.
         */
        PRODUCT(-1, -1, -1);

        final int blue;
        final int green;
        final int red;

        ColorRole(int red, int green, int blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
        }
    }

    /**
     * A choice of what to render at a given width: whether to show the mark, which size of the brand
     * wordmark to pair with the product (or {@code null} to drop it), and which size of the product
     * wordmark to show. See {@link #LAYOUTS} and {@link #layout}.
     */
    record Layout(boolean mark, @Nullable Size brand, Size product) {}

    /**
     * A tool's product identity: the wordmark art in two sizes plus the accent color its name is
     * painted in. Public so a launcher in any package can build one; the implementation is a private
     * record, and {@link #fromResources} is the factory.
     */
    public interface Product {
        /**
         * Loads a product from two classpath art resources.
         *
         * @param anchor the class whose package the resource names resolve against
         * @param bigResource the resource name of the large wordmark art, relative to {@code anchor}
         * @param smallResource the resource name of the small wordmark art, relative to {@code anchor}
         * @param accentRed the red component of the accent color, 0-255
         * @param accentGreen the green component of the accent color, 0-255
         * @param accentBlue the blue component of the accent color, 0-255
         * @return the product backed by the loaded art
         */
        static Product fromResources(
                Class<?> anchor,
                String bigResource,
                String smallResource,
                int accentRed,
                int accentGreen,
                int accentBlue
        ) {
            return new ProductImplementation(
                    load(anchor, bigResource),
                    load(anchor, smallResource),
                    accentRed,
                    accentGreen,
                    accentBlue
            );
        }

        /**
         * The blue component of the accent color, 0-255.
         *
         * @return the blue component of the accent color
         */
        int accentBlue();

        /**
         * The green component of the accent color, 0-255.
         *
         * @return the green component of the accent color
         */
        int accentGreen();

        /**
         * The red component of the accent color, 0-255.
         *
         * @return the red component of the accent color
         */
        int accentRed();

        /**
         * The large wordmark art, one string per row.
         *
         * @return the large wordmark art
         */
        List<String> bigArt();

        /**
         * The small wordmark art, one string per row.
         *
         * @return the small wordmark art
         */
        List<String> smallArt();
    }

    /** A run of contiguous characters sharing one {@link ColorRole}. */
    record Segment(ColorRole role, String text) {}

    /** The two sizes each wordmark is cut in; {@link Layout} selects between them. */
    enum Size {
        BIG, SMALL
    }

    /** The default brand colors live on {@link ColorRole}; only the product accent varies. */
    private record ProductImplementation(
            List<String> bigArt,
            List<String> smallArt,
            int accentRed,
            int accentGreen,
            int accentBlue
    ) implements Product {}

    /** The mark is 45 columns wide; the centering axis is shared between it and the word banner. */
    private static final int MARK_WIDTH = 45;

    private static final List<String> COORDINATEKIT_BIG = load(Banner.class, "banner/coordinatekit-big.txt");
    private static final List<String> COORDINATEKIT_SMALL = load(Banner.class, "banner/coordinatekit-small.txt");

    /**
     * The width ladder, ordered richest to leanest. {@link #compose} renders the first layout whose own
     * width fits the terminal, so order is significant: the entries descend in visual completeness,
     * which for any plausible wordmark also descends in width. This is the deliberate ordering the
     * AGENTS.md alphabetical rule allows where another order is part of correctness.
     */
    private static final List<Layout> LAYOUTS = List.of(
            new Layout(true, Size.BIG, Size.BIG),
            new Layout(true, Size.SMALL, Size.SMALL),
            new Layout(true, null, Size.BIG),
            new Layout(false, null, Size.BIG),
            new Layout(false, null, Size.SMALL)
    );

    private static final List<String> MARK = load(Banner.class, "banner/mark.txt");

    private final Product product;

    /**
     * Creates a banner for {@code product}. The mark and "CoordinateKit" wordmark are fixed; the
     * product supplies its own wordmark art and accent color.
     *
     * @param product the per-tool wordmark and accent color
     */
    public Banner(Product product) {
        this.product = Objects.requireNonNull(product, "product must not be null");
    }

    /**
     * Renders the art for a given terminal width, color mode, and product. Pure — it touches no
     * terminal or environment — so tests drive the layout and color ladders directly. The returned
     * block ends with a trailing newline after its last line, or is empty when {@code width} is below
     * the smallest layout.
     *
     * @param width the terminal width in columns
     * @param mode the color capability to emit for
     * @param product the product wordmark and accent color to render
     * @return the rendered art block, or the empty string when nothing fits
     */
    static String compose(int width, ColorMode mode, Product product) {
        Layout layout = layout(width, product);
        if (layout == null) {
            return "";
        }

        List<String> productArt = layout.product() == Size.BIG ? product.bigArt() : product.smallArt();
        List<List<Segment>> textRows = layout.brand() == null ? productRows(productArt)
                : brandRows(layout.brand() == Size.BIG ? COORDINATEKIT_BIG : COORDINATEKIT_SMALL, productArt);

        int textWidth = blockWidth(textRows);
        int markWidth = layout.mark() ? MARK_WIDTH : 0;
        int blockWidth = Math.max(markWidth, textWidth);
        int markPadding = (blockWidth - markWidth) / 2;
        int textPadding = (blockWidth - textWidth) / 2;

        List<String> lines = new ArrayList<>();
        if (layout.mark()) {
            for (String markRow : MARK) {
                lines.add(renderLine(markPadding, markSegments(markRow), mode, product));
            }
        }
        for (List<Segment> textRow : textRows) {
            lines.add(renderLine(textPadding, textRow, mode, product));
        }
        return String.join("\n", lines) + "\n";
    }

    /**
     * Renders the brand art as the root command's header section. Probes the attached terminal lazily —
     * only help and bare-command invocations reach this method, so other runs pay no probe cost — then
     * delegates to the pure {@link #compose}. Owns the blank line that separates the art from
     * {@code Usage:}.
     *
     * @param help the picocli help model for the command being rendered (unused; the art is global)
     * @return the rendered header, or the empty string when no art fits
     */
    @Override
    public String render(CommandLine.Help help) {
        Objects.requireNonNull(help, "help must not be null");
        int width;
        ColorMode mode;
        try (Terminal terminal = TerminalBuilder.builder().system(true).dumb(true).build()) {
            width = terminalWidth(terminal);
            mode = colorMode(terminal);
        } catch (IOException exception) {
            // A terminal we cannot probe degrades to a safe, uncolored default rather than failing help.
            width = 80;
            mode = ColorMode.MONOCHROME;
        }
        String art = compose(width, mode, product);
        return art.isEmpty() ? "" : art + "\n";
    }

    /** Returns the width of the widest row in a block of colored rows. */
    private static int blockWidth(List<List<Segment>> rows) {
        int width = 0;
        for (List<Segment> row : rows) {
            int rowWidth = 0;
            for (Segment segment : row) {
                rowWidth += segment.text().length();
            }
            width = Math.max(width, rowWidth);
        }
        return width;
    }

    /**
     * Builds the composed "CoordinateKit &lt;product&gt;" word banner: the two figlet words side by
     * side with a one-space gutter, coloring the brand word in its brand color and the product word in
     * the product accent.
     */
    private static List<List<Segment>> brandRows(List<String> brandArt, List<String> productArt) {
        int brandWidth = textWidth(brandArt);
        int productWidth = textWidth(productArt);
        int height = Math.max(brandArt.size(), productArt.size());

        List<List<Segment>> rows = new ArrayList<>();
        for (int row = 0; row < height; row++) {
            String brandRow = rightPad(row < brandArt.size() ? brandArt.get(row) : "", brandWidth);
            String productRow = rightPad(row < productArt.size() ? productArt.get(row) : "", productWidth);
            rows.add(
                    List.of(
                            new Segment(ColorRole.BRAND, brandRow),
                            new Segment(ColorRole.NONE, " "),
                            new Segment(ColorRole.PRODUCT, productRow)
                    )
            );
        }
        return rows;
    }

    /** Probes the terminal's color capability, following the truecolor → 256 → 16 → mono ladder. */
    private static ColorMode colorMode(Terminal terminal) {
        String type = terminal.getType();
        if (Terminal.TYPE_DUMB.equals(type) || Terminal.TYPE_DUMB_COLOR.equals(type)) {
            // Not a real TTY (piped, redirected, dumb): never emit ANSI.
            return ColorMode.MONOCHROME;
        }
        // JLine's toAnsi(Terminal) caps at 256 and ignores COLORTERM, so truecolor is detected here.
        String colorterm = System.getenv("COLORTERM");
        if (colorterm != null) {
            String normalized = colorterm.toLowerCase(Locale.ROOT);
            if (normalized.contains("truecolor") || normalized.contains("24bit")) {
                return ColorMode.TRUECOLOR;
            }
        }
        Integer maxColors = terminal.getNumericCapability(InfoCmp.Capability.max_colors);
        int colors = maxColors == null ? 0 : maxColors;
        if (colors >= 256) {
            return ColorMode.C256;
        }
        if (colors >= 8) {
            return ColorMode.C16;
        }
        return ColorMode.MONOCHROME;
    }

    /**
     * Selects the layout for {@code width} as the richest one whose rendered width fits. Returns
     * {@code null} when even the leanest layout is wider than the terminal, in which case no art is
     * shown. Walks {@link #LAYOUTS} richest-first and takes the first that fits.
     */
    private static @Nullable Layout layout(int width, Product product) {
        for (Layout candidate : LAYOUTS) {
            if (width >= layoutWidth(candidate, product)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Returns the column width {@code layout} renders to for {@code product}: the mark width (when
     * shown) against the word block, which is the product wordmark alone or the brand and product
     * wordmarks with a one-space gutter. This is exactly the width {@link #compose} lays out, so a
     * layout is selectable precisely when the terminal is at least this wide.
     */
    private static int layoutWidth(Layout layout, Product product) {
        List<String> productArt = layout.product() == Size.BIG ? product.bigArt() : product.smallArt();
        int contentWidth = textWidth(productArt);
        if (layout.brand() != null) {
            List<String> brandArt = layout.brand() == Size.BIG ? COORDINATEKIT_BIG : COORDINATEKIT_SMALL;
            contentWidth = textWidth(brandArt) + 1 + textWidth(productArt);
        }
        int markWidth = layout.mark() ? MARK_WIDTH : 0;
        return Math.max(markWidth, contentWidth);
    }

    /**
     * Loads a brand-art resource into its lines, dropping trailing blank lines so the renderer alone
     * controls vertical spacing. Resolves {@code resource} against {@code anchor}'s package. Fails
     * loudly if the resource is missing from the jar.
     */
    private static List<String> load(Class<?> anchor, String resource) {
        try (InputStream in = anchor.getResourceAsStream(resource)) {
            Objects.requireNonNull(in, "missing banner resource: " + resource);
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>(List.of(content.split("\n", -1)));
            while (!lines.isEmpty() && lines.getLast().isBlank()) {
                lines.removeLast();
            }
            return List.copyOf(lines);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read banner resource: " + resource, exception);
        }
    }

    /**
     * Colors one row of the mark, reproducing the glyph rule from the source {@code mark.sh}: the pin
     * glyphs {@code # * - . :} and a context-dependent {@code =} are pin-colored, {@code +} is globe,
     * and spaces are unstyled. The shared {@code =} glyph is pin only when its immediate left neighbor
     * is itself a pin glyph (the pin set includes {@code =}), otherwise globe.
     *
     * <p>
     * A position rule then overrides color for the globe interior: any {@code +} or {@code =} that sits
     * strictly between the row's left-most and right-most {@code #} is pin-colored rather than globe,
     * so the sprout glyphs woven through the globe read as part of the mark. Glyphs outside that span —
     * the sprout proper to the right, and any stem to the left — keep their globe color.
     *
     * <p>
     * Adjacent cells of the same role are merged into a single segment. Package-private so the glyph
     * rule can be unit-tested directly.
     */
    static List<Segment> markSegments(String row) {
        int leftmostHash = row.indexOf('#');
        int rightmostHash = row.lastIndexOf('#');
        List<Segment> segments = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        ColorRole runRole = null;
        for (int i = 0; i < row.length(); i++) {
            char cell = row.charAt(i);
            boolean betweenHashes = i > leftmostHash && i < rightmostHash;
            ColorRole role;
            if (cell == ' ') {
                role = ColorRole.NONE;
            } else if (cell == '+') {
                role = betweenHashes ? ColorRole.PIN : ColorRole.GLOBE;
            } else if ("#*-.:".indexOf(cell) >= 0) {
                role = ColorRole.PIN;
            } else if (cell == '=') {
                char left = i > 0 ? row.charAt(i - 1) : ' ';
                role = betweenHashes || "#*-.:=".indexOf(left) >= 0 ? ColorRole.PIN : ColorRole.GLOBE;
            } else {
                role = ColorRole.NONE;
            }
            if (role != runRole) {
                if (runRole != null) {
                    segments.add(new Segment(runRole, run.toString()));
                }
                run.setLength(0);
                runRole = role;
            }
            run.append(cell);
        }
        if (runRole != null) {
            segments.add(new Segment(runRole, run.toString()));
        }
        return segments;
    }

    /** Builds the standalone product word banner, colored in the product accent. */
    private static List<List<Segment>> productRows(List<String> productArt) {
        int productWidth = textWidth(productArt);
        List<List<Segment>> rows = new ArrayList<>();
        for (String row : productArt) {
            rows.add(List.of(new Segment(ColorRole.PRODUCT, rightPad(row, productWidth))));
        }
        return rows;
    }

    /**
     * Renders one laid-out line: {@code padding} leading spaces followed by the colored segments,
     * emitted for {@code mode}. Monochrome returns the raw characters with no ANSI; the colored modes
     * force the matching SGR form so the result does not depend on JLine's own terminal probing.
     */
    private static String renderLine(int padding, List<Segment> segments, ColorMode mode, Product product) {
        AttributedStringBuilder builder = new AttributedStringBuilder();
        builder.append(" ".repeat(padding));
        for (Segment segment : segments) {
            builder.style(styleFor(mode, segment.role(), product));
            builder.append(segment.text());
        }
        AttributedString line = builder.toAttributedString();
        return switch (mode) {
            case TRUECOLOR -> line.toAnsi(AttributedCharSequence.TRUE_COLORS, ForceMode.ForceTrueColors);
            case C256 -> line.toAnsi(256, ForceMode.Force256Colors);
            case C16 -> line.toAnsi(16, ForceMode.None);
            case MONOCHROME -> line.toString();
        };
    }

    /** Right-pads {@code text} with spaces to {@code width}; returns it unchanged if already wider. */
    private static String rightPad(String text, int width) {
        return text.length() >= width ? text : text + " ".repeat(width - text.length());
    }

    /**
     * Maps a color role to a JLine style for the mode: RGB for truecolor/256, a rounded index for 16.
     * The {@link ColorRole#PRODUCT} role resolves to {@code product}'s accent; every other role carries
     * its own fixed RGB.
     */
    private static AttributedStyle styleFor(ColorMode mode, ColorRole role, Product product) {
        if (role == ColorRole.NONE) {
            return AttributedStyle.DEFAULT;
        }
        int red = role == ColorRole.PRODUCT ? product.accentRed() : role.red;
        int green = role == ColorRole.PRODUCT ? product.accentGreen() : role.green;
        int blue = role == ColorRole.PRODUCT ? product.accentBlue() : role.blue;
        return switch (mode) {
            case TRUECOLOR, C256 -> AttributedStyle.DEFAULT.foreground(red, green, blue);
            case C16 -> AttributedStyle.DEFAULT.foreground(Colors.roundRgbColor(red, green, blue, 16));
            case MONOCHROME -> AttributedStyle.DEFAULT;
        };
    }

    /** Resolves the terminal width, falling back to {@code COLUMNS} then 80 when it is unknown. */
    private static int terminalWidth(Terminal terminal) {
        int width = terminal.getWidth();
        if (width > 0) {
            return width;
        }
        String columns = System.getenv("COLUMNS");
        if (columns != null) {
            try {
                int parsed = Integer.parseInt(columns.trim());
                if (parsed > 0) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                // Unparseable COLUMNS falls through to the default.
            }
        }
        return 80;
    }

    /** Returns the width of the widest line in a list of art lines. */
    private static int textWidth(List<String> lines) {
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, line.length());
        }
        return width;
    }
}
