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
package org.coordinatekit.crf.verification;

/**
 * Entry point of the native image that stands in for a downstream application.
 *
 * <p>
 * For now the launcher only proves that the GraalVM toolchain builds and runs an image in CI. It
 * will delegate to the {@code cli} module's launcher once the verification harness grows sibling
 * module dependencies.
 */
public final class VerificationLauncher {
    private VerificationLauncher() {}

    /**
     * Prints a single line to standard output and exits normally.
     *
     * @param arguments the command-line arguments, ignored
     */
    public static void main(String[] arguments) {
        System.out.println("crf verification image started");
    }
}
