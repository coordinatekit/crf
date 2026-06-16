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

import java.util.List;

/**
 * One edit screen, paired with the tags it offers. The {@link EditViewModel} carries the
 * pre-formatted display rows and {@code candidateTags} carries the selectable tags in the same
 * order, so the row a user picks maps back to a tag by index into a single shared list rather than
 * a separately derived one.
 *
 * @param viewModel the terminal-free description rendered to the screen
 * @param candidateTags the selectable tags, in the same order as {@link EditViewModel#tagRows()}
 * @param <T> the tag type
 */
record EditScreen<T> (EditViewModel viewModel, List<T> candidateTags) {
    EditScreen {
        candidateTags = List.copyOf(candidateTags);
    }
}
