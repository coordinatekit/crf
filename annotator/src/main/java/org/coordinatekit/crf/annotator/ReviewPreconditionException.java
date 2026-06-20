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

import java.nio.file.Path;

import org.jspecify.annotations.NullMarked;

/**
 * Thrown when a {@link RetokenizeReviewer#review(Path, Path) review} pass is asked to run with
 * input or output paths that violate its fresh-pass precondition: the input and output must be
 * different paths, and the output must be absent or empty.
 *
 * <p>
 * This is a dedicated, user-facing precondition signal — distinct from the generic
 * {@link IllegalArgumentException} / {@link IllegalStateException} a programming bug raises — so a
 * CLI can report a bad invocation while letting genuine bugs propagate loudly.
 */
@NullMarked
public final class ReviewPreconditionException extends RuntimeException {
    /**
     * Constructs the exception with a message describing the violated precondition.
     *
     * @param message the detail message
     */
    public ReviewPreconditionException(String message) {
        super(message);
    }
}
