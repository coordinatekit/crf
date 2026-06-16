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

/** Shared footer-prompt strings asserted across the terminal tagging tests. */
final class TaggingPrompts {
    static final String ALL_VIEW_PROMPT = "Enter A to accept, the number to edit the token, F to show key features only, S to skip, U to undo, or X to exit.";
    static final String KEY_FEATURES_PROMPT = "Enter A to accept, the number to edit the token, F to show key features, S to skip, U to undo, or X to exit.";
    static final String KEY_ONLY_VIEW_PROMPT = "Enter A to accept, the number to edit the token, F to hide features, S to skip, U to undo, or X to exit.";
    static final String KEY_VIEW_PROMPT = "Enter A to accept, the number to edit the token, F to hide features, FA to show all features, S to skip, U to undo, or X to exit.";
    static final String SEQUENCE_PROMPT = "Enter A to accept, the number to edit the token, S to skip, U to undo, or X to exit.";
    static final String VERBOSE_FEATURES_PROMPT = "Enter A to accept, the number to edit the token, FA to show all features, S to skip, U to undo, or X to exit.";
    static final String VERBOSE_ONLY_VIEW_PROMPT = "Enter A to accept, the number to edit the token, FA to hide features, S to skip, U to undo, or X to exit.";

    private TaggingPrompts() {}
}
