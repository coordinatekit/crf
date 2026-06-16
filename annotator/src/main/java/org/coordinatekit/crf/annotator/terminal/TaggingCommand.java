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
 * A parsed sequence-screen command: the result of mapping one line of user input to an intention,
 * independent of how it is rendered or applied.
 */
sealed interface TaggingCommand {
    /** Accept the current tags for the sequence. */
    record Accept() implements TaggingCommand {}

    /** Open the edit screen for the token at the given zero-based position. */
    record EditToken(int position) implements TaggingCommand {}

    /** Exit the annotation session. */
    record Exit() implements TaggingCommand {}

    /** Unrecognized or empty input: redraw without changing anything. */
    record Noop() implements TaggingCommand {}

    /**
     * A command that reduces the session state in place rather than ending the loop. These are the only
     * commands {@link TaggingSession#apply(Reducer)} accepts, so the reducer never has to handle the
     * control-flow commands ({@link Accept}, {@link Skip}, {@link Exit}, {@link EditToken},
     * {@link Noop}).
     */
    sealed interface Reducer extends TaggingCommand {}

    /** Skip the sequence without recording any tags. */
    record Skip() implements TaggingCommand {}

    /** Toggle the all-features (key + verbose union) view. */
    record ToggleAllFeatures() implements Reducer {}

    /** Toggle the key-features view. */
    record ToggleKeyFeatures() implements Reducer {}

    /** Undo the most recent tag change. */
    record Undo() implements Reducer {}
}
