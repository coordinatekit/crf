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

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * The terminal transport for the tagging interface: it owns the JLine {@link LineReader} and
 * {@link Terminal}, renders a screen by handing a builder to a renderer and writing the result, and
 * reads a line of input. This is the one component that touches real I/O, so isolating it keeps the
 * control loop, rendering, and tagging state terminal-free.
 *
 * <p>
 * The terminal supplied at construction is borrowed, not owned; this class never closes it.
 */
final class TerminalScreen {
    private final LineReader lineReader;
    private final Terminal terminal;

    /**
     * Creates a screen over {@code terminal}, building a {@link LineReader} with history disabled.
     *
     * @param terminal the terminal to read from and write to, must not be null
     */
    TerminalScreen(Terminal terminal) {
        this.terminal = Objects.requireNonNull(terminal, "terminal must not be null");
        this.lineReader = LineReaderBuilder.builder().terminal(terminal).variable(LineReader.HISTORY_SIZE, 0).build();
    }

    /**
     * Reads a line from the terminal, returning null when the input stream signals end-of-file or the
     * user interrupts it, for example with Ctrl-D or Ctrl-C.
     *
     * @return the line read, or null when input ends
     */
    @Nullable
    String readLine() {
        try {
            return lineReader.readLine();
        } catch (EndOfFileException | UserInterruptException exception) {
            return null;
        }
    }

    /**
     * Renders the edit screen described by {@code viewModel} using {@code renderer}.
     *
     * @param renderer the edit-screen renderer
     * @param viewModel the screen description to render
     */
    void render(EditScreenRenderer renderer, EditViewModel viewModel) {
        AttributedStringBuilder builder = new AttributedStringBuilder();
        renderer.appendTo(builder, viewModel);
        write(builder);
    }

    /**
     * Renders the sequence screen described by {@code viewModel} using {@code renderer}, wrapping the
     * feature column to the terminal's current width.
     *
     * @param renderer the sequence-screen renderer
     * @param viewModel the screen description to render
     */
    void render(SequenceScreenRenderer renderer, TaggingViewModel viewModel) {
        AttributedStringBuilder builder = new AttributedStringBuilder();
        renderer.appendTo(builder, viewModel, terminal.getWidth());
        write(builder);
    }

    private void write(AttributedStringBuilder builder) {
        terminal.writer().println(builder.toAttributedString().toAnsi());
        terminal.writer().flush();
    }
}
