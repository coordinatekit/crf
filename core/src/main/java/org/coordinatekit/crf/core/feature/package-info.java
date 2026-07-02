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
 * Feature extraction for CRF training and tagging.
 *
 * <p>
 * A {@link org.coordinatekit.crf.core.feature.FeatureExtractor} turns the positioned tokens
 * produced by {@link org.coordinatekit.crf.core.preprocessing.Tokenizer preprocessing} into
 * feature-rich sequences the model can learn from and tag against. Each token carries a set of
 * structured {@link org.coordinatekit.crf.core.feature.Feature}s, and features become the model's
 * flat feature strings at a single edge, the
 * {@link org.coordinatekit.crf.core.feature.FeatureFormat}.
 *
 * <p>
 * Two marker subinterfaces label the extractors the rest of the system looks up by role:
 *
 * <ul>
 * <li>{@link org.coordinatekit.crf.core.feature.FullFeatureExtractor} marks the extractor used for
 * training and tagging, the complete feature set the model sees. It also feeds the annotator's
 * verbose ("all features") view.
 * <li>{@link org.coordinatekit.crf.core.feature.KeyFeatureExtractor} marks the simpler pre-window
 * extractor that feeds the annotator's key ("key features") view. When none is registered the key
 * view falls back to the {@link org.coordinatekit.crf.core.feature.FullFeatureExtractor}.
 * </ul>
 *
 * <h2>Feature extractors</h2>
 *
 * <p>
 * The following {@link org.coordinatekit.crf.core.feature.FeatureExtractor} implementations are
 * provided:
 *
 * <ul>
 * <li>{@link org.coordinatekit.crf.core.feature.CompositeFeatureExtractor} combines multiple
 * feature extractors into one
 * <li>{@link org.coordinatekit.crf.core.feature.LengthFeatureExtractor} generates features based on
 * sequence length
 * <li>{@link org.coordinatekit.crf.core.feature.PatternMatchingFeatureExtractor} matches tokens
 * against regex patterns
 * <li>{@link org.coordinatekit.crf.core.feature.PositionFeatureExtractor} generates features based
 * on token position (first, last, index)
 * <li>{@link org.coordinatekit.crf.core.feature.SubstringFeatureExtractor} extracts prefix and
 * suffix substrings from tokens
 * <li>{@link org.coordinatekit.crf.core.feature.TransformingFeatureExtractor} applies a custom
 * transformation function to tokens
 * <li>{@link org.coordinatekit.crf.core.feature.WindowFeatureExtractor} extracts features from
 * neighboring tokens within a sliding window
 * <li>{@link org.coordinatekit.crf.core.feature.XPathFeatureExtractor} checks tokens against values
 * loaded from an XML file
 * </ul>
 *
 * <h2>Combining feature extractors</h2>
 *
 * <p>
 * Use {@link org.coordinatekit.crf.core.feature.CompositeFeatureExtractor} to blend multiple
 * extractors, then wrap the result in a
 * {@link org.coordinatekit.crf.core.feature.WindowFeatureExtractor} to fold in neighboring context:
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
 * FeatureExtractor&lt;String&gt; combined = CompositeFeatureExtractor.of(
 *         prefixExtractor,
 *         suffixExtractor,
 *         capsExtractor,
 *         positionExtractor);
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
 * @see org.coordinatekit.crf.core.feature.FeatureExtractor
 * @see org.coordinatekit.crf.core.feature.CompositeFeatureExtractor
 * @see org.coordinatekit.crf.core.preprocessing
 */
@NullMarked
package org.coordinatekit.crf.core.feature;

import org.jspecify.annotations.NullMarked;
