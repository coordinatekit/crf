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
package org.coordinatekit.crf.annotator.ui;

import java.util.List;

/**
 * The outcome of presenting a sequence to a user for tagging.
 *
 * <p>
 * The {@link #action() action} describes what the user chose to do. The {@link #finalTags()
 * finalTags} list carries the chosen tags when the action is {@link TaggingAction#ACCEPT}; for
 * {@link TaggingAction#SKIP} and {@link TaggingAction#EXIT} the list is empty.
 *
 * <p>
 * Instances are constructed via {@link AnnotatorModels#taggingResult(TaggingAction, List)}.
 *
 * @param <T> the tag type
 * @see TaggingInterface
 * @see AnnotatorModels#taggingResult(TaggingAction, List)
 */
public interface TaggingResult<T> {
    /**
     * Returns the action chosen by the user.
     *
     * @return the action
     */
    TaggingAction action();

    /**
     * Returns the per-token tags chosen by the user, in the same order as the presented sequence.
     *
     * @return the final tags
     */
    List<T> finalTags();
}
