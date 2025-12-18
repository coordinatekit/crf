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

/**
 * Unchecked exception thrown when an error occurs during conditional random field (CRF) operations.
 *
 * <p>
 * This runtime exception is used to wrap or signal errors that occur during CRF model training,
 * inference, or preprocessing operations.
 */
public class UncheckedCrfException extends RuntimeException {
    /**
     * Constructs a new exception with no detail message.
     */
    public UncheckedCrfException() {
        super();
    }

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message
     */
    public UncheckedCrfException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public UncheckedCrfException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new exception with the specified cause.
     *
     * @param cause the cause of this exception
     */
    public UncheckedCrfException(Throwable cause) {
        super(cause);
    }
}
