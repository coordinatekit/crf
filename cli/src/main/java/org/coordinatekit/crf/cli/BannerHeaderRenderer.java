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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.coordinatekit.foundation.cli.brand.Banner;

import picocli.CommandLine;

/**
 * Renders the CoordinateKit brand banner as the root command's header section, using crf's own
 * "CRF" wordmark art and cyan accent. Registered as the
 * {@link picocli.CommandLine.Model.UsageMessageSpec#SECTION_KEY_HEADER} renderer on the root
 * command only (see {@code CrfLauncher}), so {@code --help} and a bare command both show it while
 * subcommands and {@code --version} do not.
 *
 * <p>
 * The globe mark, the "CoordinateKit" wordmark, and the layout/color engine live in {@link Banner},
 * shared verbatim across CoordinateKit command-line tools; this class supplies only what is crf's
 * own — the "CRF" wordmark art and its accent color — and hands picocli's ANSI decision to the
 * library through the {@link BannerRenderer} seam. The banner itself is built lazily by
 * {@link BannerHolder}, so a malformed or missing art resource cannot fail a command that never
 * renders one.
 */
@NullMarked
final class BannerHeaderRenderer implements CommandLine.IHelpSectionRenderer {
    private final BannerRenderer bannerRenderer;

    BannerHeaderRenderer() {
        // A lambda, not BannerHolder.BANNER::render: a bound method reference evaluates its receiver
        // where it is written, which would initialize the holder here and defeat the deferral.
        this(ansiEnabled -> BannerHolder.BANNER.render(ansiEnabled));
    }

    BannerHeaderRenderer(BannerRenderer bannerRenderer) {
        this.bannerRenderer = Objects.requireNonNull(bannerRenderer, "bannerRenderer must not be null");
    }

    @Override
    public String render(CommandLine.Help help) {
        Objects.requireNonNull(help, "help must not be null");
        return bannerRenderer.render(help.colorScheme().ansi().enabled());
    }

    /**
     * Loads a brand-art resource into its lines, dropping trailing blank lines so the renderer alone
     * controls vertical spacing. Resolves {@code resource} against this class's package. Fails loudly
     * if the resource is missing from the jar.
     */
    private static List<String> load(String resource) {
        try (InputStream in = BannerHeaderRenderer.class.getResourceAsStream(resource)) {
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
     * Holds the product banner so its art is read on the first render rather than when the renderer is
     * constructed, keeping a malformed or missing resource from failing commands that draw no banner.
     */
    private static final class BannerHolder {
        private static final Banner BANNER = new Banner(
                Banner.Product.of(load("banner/crf-big.txt"), load("banner/crf-small.txt"), "#5BC7E3")
        );
    }

    /**
     * Renders the brand banner for an already-resolved ANSI decision. The seam that makes crf's one
     * line of banner logic — handing picocli's decision to the library — observable to a test, which a
     * rendered string cannot be: the library collapses to monochrome without a TTY.
     */
    @FunctionalInterface
    interface BannerRenderer {
        /**
         * Renders the banner.
         *
         * @param ansiEnabled whether the caller resolved ANSI output as enabled
         * @return the rendered banner block
         */
        String render(boolean ansiEnabled);
    }
}
