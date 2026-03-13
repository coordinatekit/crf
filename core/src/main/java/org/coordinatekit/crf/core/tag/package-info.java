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
 * Tagging interfaces and result types for CRF sequence labeling.
 *
 * <p>
 * This package provides the types for tagging input sequences using trained CRF models:
 *
 * <ul>
 * <li>{@link org.coordinatekit.crf.core.tag.CrfTagger} - Tags input sequences using a trained CRF
 * model
 * <li>{@link org.coordinatekit.crf.core.tag.TaggedSequence} - A sequence with predicted tags for
 * each token
 * <li>{@link org.coordinatekit.crf.core.tag.TaggedPositionedToken} - A token with its predicted tag
 * and confidence scores
 * <li>{@link org.coordinatekit.crf.core.tag.TagScore} - Associates a tag with its confidence score
 * </ul>
 *
 * @see org.coordinatekit.crf.core.tag.CrfTagger
 */
@NullMarked
package org.coordinatekit.crf.core.tag;

import org.jspecify.annotations.NullMarked;
