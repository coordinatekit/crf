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
 * Core abstractions and interfaces for Conditional Random Fields (CRF) sequence labeling.
 *
 * <p>
 * This package provides the foundational types for building CRF-based sequence labeling
 * applications, including:
 *
 * <ul>
 * <li>{@link org.coordinatekit.crf.core.TagProvider} - Encodes and decodes tags between typed
 * values and strings, defining the label space for the CRF model
 * <li>{@link org.coordinatekit.crf.core.Sequence} - Represents an ordered sequence of positioned
 * tokens
 * <li>{@link org.coordinatekit.crf.core.InputSequence} - A sequence of input tokens ready for
 * tagging
 * <li>{@link org.coordinatekit.crf.core.PositionedToken} - A token with its position within the
 * original input
 * </ul>
 *
 * @see org.coordinatekit.crf.core.preprocessing
 * @see org.coordinatekit.crf.core.tag
 * @see org.coordinatekit.crf.core.train
 */
@NullMarked
package org.coordinatekit.crf.core;

import org.jspecify.annotations.NullMarked;
