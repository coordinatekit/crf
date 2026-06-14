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

/**
 * A parsed edit-screen command: the result of mapping one line of user input to an intention while
 * a single token's tag is being chosen.
 */
sealed interface EditCommand {
    /** Cancel the edit and return to the sequence screen without changing the tag. */
    record Cancel() implements EditCommand {}

    /** Unrecognized or empty input: redraw the edit screen without changing anything. */
    record Noop() implements EditCommand {}

    /** Select the candidate tag at the given zero-based row index. */
    record SelectTag(int index) implements EditCommand {}
}
