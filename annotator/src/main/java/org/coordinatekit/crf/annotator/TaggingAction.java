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
package org.coordinatekit.crf.annotator;

/**
 * The action selected by the user when presented with a sequence to tag.
 *
 * @see TaggingInterface
 * @see TaggingResult
 */
public enum TaggingAction {
    /**
     * The user accepted the sequence's tags (possibly after editing).
     *
     * <p>
     * The {@link TaggingResult#finalTags() finalTags} list on the result reflects the chosen tagging
     * for every token in the presented sequence.
     */
    ACCEPT,

    /**
     * The user chose to exit the tagging session entirely.
     *
     * <p>
     * The orchestrator should treat this as a signal to stop presenting sequences. The
     * {@link TaggingResult#finalTags() finalTags} list is empty.
     */
    EXIT,

    /**
     * The user chose to skip the current sequence without producing a tagging.
     *
     * <p>
     * The orchestrator should move to the next sequence (if any). The {@link TaggingResult#finalTags()
     * finalTags} list is empty.
     */
    SKIP
}
