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
import org.jspecify.annotations.Nullable;

/**
 * Signals that a command could not be started because its domain services could not be resolved or
 * a supplied model could not be loaded — a user-actionable configuration problem, not a bug.
 *
 * <p>
 * The message is written verbatim to standard error and the command exits {@code 1}. It is thrown
 * before the interactive terminal is opened, so a misconfiguration fails fast rather than
 * corrupting output.
 */
@NullMarked
final class CrfStartupException extends RuntimeException {
    CrfStartupException(String message) {
        super(message);
    }

    CrfStartupException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
