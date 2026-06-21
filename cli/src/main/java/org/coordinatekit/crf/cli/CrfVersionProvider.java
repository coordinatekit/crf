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

import picocli.CommandLine.IVersionProvider;

/**
 * Supplies the {@code --version} string for the {@code crf} command and its subcommands.
 *
 * <p>
 * The version is read from the jar manifest's {@code Implementation-Version} attribute, which
 * {@code cli/build.gradle} sets to the project version. When the tool runs from loose classes
 * instead of a packaged jar — under tests or {@code ./gradlew run} — no manifest is present, so a
 * development-build fallback is returned rather than a blank line.
 */
@NullMarked
final class CrfVersionProvider implements IVersionProvider {
    @Override
    public String[] getVersion() {
        String version = getClass().getPackage().getImplementationVersion();
        return new String[] {version == null ? "crf (development build)" : "crf " + version};
    }
}
