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
 * Terminal-based implementation of the annotator's tagging interface.
 *
 * <p>
 * {@link org.coordinatekit.crf.annotator.terminal.TerminalTaggingInterface} is the only public type
 * here; it implements {@link org.coordinatekit.crf.annotator.TaggingInterface} by driving a JLine
 * terminal. Everything else — the session state, view models, input parsers, screen renderers, and
 * the terminal screen and table primitives — is package-private and exists solely to support that
 * implementation.
 */
@NullMarked
package org.coordinatekit.crf.annotator.terminal;

import org.jspecify.annotations.NullMarked;
