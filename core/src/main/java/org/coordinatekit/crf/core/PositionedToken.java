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
package org.coordinatekit.crf.core;

import org.jspecify.annotations.NullMarked;

/**
 * A token with its position within a sequence.
 *
 * <p>
 * This interface represents the basic unit of a sequence, combining the token value with its
 * zero-based position index. It serves as the foundation for more specialized token types used in
 * training and inference.
 */
@NullMarked
public interface PositionedToken {
    /**
     * Returns the zero-based position of this token within its sequence.
     *
     * @return the position index
     */
    int position();

    /**
     * Returns the token value.
     *
     * @return the token string
     */
    String token();
}
