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
 * Interactive annotation session: orchestrator, per-sequence tagging interface, and JLine terminal
 * implementation.
 *
 * <p>
 * The {@link org.coordinatekit.crf.annotator.Annotator} ties together the I/O surface from
 * {@code core} (tokenization, the XML training-data appender) with the
 * {@link org.coordinatekit.crf.annotator.TaggingInterface} presentation contract, walking an input
 * file line-by-line and persisting each accepted sequence to the output XML file. The default
 * presentation implementation is {@link org.coordinatekit.crf.annotator.JLineTaggingInterface},
 * which renders to a JLine terminal.
 */
@NullMarked
package org.coordinatekit.crf.annotator;

import org.jspecify.annotations.NullMarked;
