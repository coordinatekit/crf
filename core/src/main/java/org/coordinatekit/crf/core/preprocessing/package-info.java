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
 * Tokenization and training-data structures for CRF pipelines.
 *
 * <p>
 * This package is the first pipeline stage: it turns raw input into the positioned tokens that
 * {@link org.coordinatekit.crf.core.feature feature extraction} consumes, and it models the
 * training sequences read from labeled data.
 *
 * <ul>
 * <li>{@link org.coordinatekit.crf.core.preprocessing.Tokenizer} splits input text into positioned
 * tokens, with {@link org.coordinatekit.crf.core.preprocessing.WhitespaceTokenizer} as the built-in
 * default
 * <li>{@link org.coordinatekit.crf.core.preprocessing.Tokenization} holds the tokens and
 * {@link org.coordinatekit.crf.core.preprocessing.Segment segments} produced from an input
 * <li>{@link org.coordinatekit.crf.core.preprocessing.TrainingSequence} is a sequence of tagged
 * tokens for training, with {@link org.coordinatekit.crf.core.preprocessing.TrainingSegments} and
 * {@link org.coordinatekit.crf.core.preprocessing.TrainingPositionedToken} carrying its labeled
 * parts
 * </ul>
 *
 * <p>
 * Once tokenized, a sequence passes to a
 * {@link org.coordinatekit.crf.core.feature.FeatureExtractor} in the
 * {@link org.coordinatekit.crf.core.feature feature} package, which attaches the features the model
 * trains and tags on.
 *
 * @see org.coordinatekit.crf.core.preprocessing.Tokenizer
 * @see org.coordinatekit.crf.core.feature
 */
@NullMarked
package org.coordinatekit.crf.core.preprocessing;

import org.jspecify.annotations.NullMarked;
