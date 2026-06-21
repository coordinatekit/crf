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
/**
 * The command-line front end for the interactive {@code annotate} and {@code retokenize} flows.
 *
 * <p>
 * This package owns all of the project's picocli usage.
 * {@link org.coordinatekit.crf.cli.CrfLauncher} is the entry point: it wires the two subcommands
 * under a root {@code crf} command and resolves the domain services (tag provider, tokenizer,
 * feature extractor, model loader) through {@link java.util.ServiceLoader}, so a downstream
 * registers {@code META-INF/services} files instead of writing a {@code main} or a factory. Each
 * subcommand parses the standard flags, builds the matching parser-free {@code Configuration} from
 * the {@code annotator} module, and delegates to that module's {@code Runner}, which owns the
 * terminal precondition and the exit-code contract.
 */
@NullMarked
package org.coordinatekit.crf.cli;

import org.jspecify.annotations.NullMarked;
