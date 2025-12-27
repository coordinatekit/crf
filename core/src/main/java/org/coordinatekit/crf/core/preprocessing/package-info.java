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
 * Preprocessing components for tokenization and feature extraction in CRF pipelines.
 *
 * <p>
 * This package provides the preprocessing pipeline that transforms raw input into feature-rich
 * sequences suitable for CRF training and tagging:
 *
 * <ul>
 * <li>{@link org.coordinatekit.crf.core.preprocessing.Tokenizer} - Splits input text into
 * positioned tokens
 * <li>{@link org.coordinatekit.crf.core.preprocessing.FeatureExtractor} - Extracts features from
 * tokens within a sequence context
 * <li>{@link org.coordinatekit.crf.core.preprocessing.TrainingSequence} - A sequence with tagged
 * tokens for training
 * </ul>
 *
 * <h2>Feature Extractors</h2>
 *
 * <p>
 * The following {@link org.coordinatekit.crf.core.preprocessing.FeatureExtractor} implementations
 * are provided:
 *
 * <ul>
 * <li>{@link org.coordinatekit.crf.core.preprocessing.CompositeFeatureExtractor} - Combines
 * multiple feature extractors into one
 * <li>{@link org.coordinatekit.crf.core.preprocessing.LengthFeatureExtractor} - Generates features
 * based on sequence length
 * <li>{@link org.coordinatekit.crf.core.preprocessing.PatternMatchingFeatureExtractor} - Matches
 * tokens against regex patterns
 * <li>{@link org.coordinatekit.crf.core.preprocessing.PositionFeatureExtractor} - Generates
 * features based on token position (first, last, index)
 * <li>{@link org.coordinatekit.crf.core.preprocessing.SubstringFeatureExtractor} - Extracts prefix
 * and suffix substrings from tokens
 * <li>{@link org.coordinatekit.crf.core.preprocessing.TransformingFeatureExtractor} - Applies a
 * custom transformation function to tokens
 * <li>{@link org.coordinatekit.crf.core.preprocessing.WindowFeatureExtractor} - Extracts features
 * from neighboring tokens within a sliding window
 * <li>{@link org.coordinatekit.crf.core.preprocessing.XPathFeatureExtractor} - Checks tokens
 * against values loaded from an XML file
 * </ul>
 *
 * <h2>Example: Combining Feature Extractors</h2>
 *
 * <p>
 * Use {@link org.coordinatekit.crf.core.preprocessing.CompositeFeatureExtractor} to blend multiple
 * extractors:
 *
 * <pre>
 * <code>
 * // Create individual extractors
 * FeatureExtractor&lt;String&gt; prefixExtractor = SubstringFeatureExtractor
 *         .&lt;String&gt;builder(s -&gt; "PREFIX_" + s)
 *         .length(3)
 *         .build();
 *
 * FeatureExtractor&lt;String&gt; suffixExtractor = SubstringFeatureExtractor
 *         .&lt;String&gt;builder(s -&gt; "SUFFIX_" + s)
 *         .length(3)
 *         .ending(true)
 *         .build();
 *
 * FeatureExtractor&lt;String&gt; capsExtractor = PatternMatchingFeatureExtractor
 *         .&lt;String&gt;builder("[A-Z]+")
 *         .matchedFeature("ALL_CAPS")
 *         .build();
 *
 * FeatureExtractor&lt;String&gt; positionExtractor = PositionFeatureExtractor
 *         .&lt;String&gt;builder()
 *         .firstFeature("FIRST_TOKEN")
 *         .lastFeature("LAST_TOKEN")
 *         .build();
 *
 * // Combine into a single extractor
 * FeatureExtractor&lt;String&gt; combined = new CompositeFeatureExtractor&lt;&gt;(List.of(
 *         prefixExtractor,
 *         suffixExtractor,
 *         capsExtractor,
 *         positionExtractor));
 *
 * // Wrap with window context for neighboring token features
 * FeatureExtractor&lt;String&gt; windowExtractor = WindowFeatureExtractor
 *         .builder(combined, (feature, offset) -&gt; feature + "@" + offset)
 *         .windowBefore(2)
 *         .windowAfter(2)
 *         .build();
 * </code>
 * </pre>
 *
 * @see org.coordinatekit.crf.core.preprocessing.FeatureExtractor
 * @see org.coordinatekit.crf.core.preprocessing.CompositeFeatureExtractor
 * @see org.coordinatekit.crf.core.preprocessing.Tokenizer
 */
@NullMarked
package org.coordinatekit.crf.core.preprocessing;

import org.jspecify.annotations.NullMarked;
