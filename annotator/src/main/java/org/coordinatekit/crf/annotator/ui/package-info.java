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
 * User-interface abstractions for presenting a sequence to a user for tagging.
 *
 * <p>
 * The core abstraction is {@link org.coordinatekit.crf.annotator.ui.TaggingInterface}, which a
 * higher-level orchestrator invokes once per sequence. The default implementation,
 * {@link org.coordinatekit.crf.annotator.ui.JLineTaggingInterface}, renders the sequence and edit
 * screens to a JLine terminal.
 */
@NullMarked
package org.coordinatekit.crf.annotator.ui;

import org.jspecify.annotations.NullMarked;
