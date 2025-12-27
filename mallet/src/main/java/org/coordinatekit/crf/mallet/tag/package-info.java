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
 * MALLET-based CRF tagging implementation.
 *
 * <p>
 * This package provides the MALLET-based implementation for tagging sequences using trained CRF
 * models.
 *
 * <h2>Example Usage</h2>
 *
 * <pre>
 * <code>
 * // Load a trained model and create a tagger
 * MalletCrfTagger&lt;String, MyTag&gt; tagger = new MalletCrfTagger&lt;&gt;(
 *         featureExtractor,
 *         Path.of("model.ser"),
 *         tagProvider,
 *         tokenizer);
 *
 * // Tag an input string
 * Sequence&lt;TaggedPositionedToken&lt;String, MyTag&gt;&gt; result = tagger.tag("input text to tag");
 *
 * // Process the results
 * for (TaggedPositionedToken&lt;String, MyTag&gt; token : result) {
 *     String text = token.token();           // The token text
 *     MyTag bestTag = token.tag();           // Most likely tag
 *     List&lt;MyTag&gt; topTags = token.tag(3);    // Top 3 tags by score
 *
 *     // Access all tag scores
 *     for (TagScore&lt;MyTag&gt; score : token.tagScores()) {
 *         MyTag tag = score.tag();
 *         double probability = score.score();
 *     }
 * }
 * </code>
 * </pre>
 *
 * <h2>Result Types</h2>
 *
 * <p>
 * The tagger returns a {@link org.coordinatekit.crf.core.Sequence} of
 * {@link org.coordinatekit.crf.core.tag.TaggedPositionedToken} objects, each containing:
 *
 * <ul>
 * <li>{@code token()} - The original token text
 * <li>{@code position()} - The token's index in the sequence
 * <li>{@code features()} - The features extracted for this token
 * <li>{@code tag()} - The most likely tag (highest score)
 * <li>{@code tag(n)} - The top N tags ranked by score
 * <li>{@code tagScores()} - All tags with their probability scores
 * </ul>
 *
 * @see org.coordinatekit.crf.mallet.tag.MalletCrfTagger
 * @see org.coordinatekit.crf.core.tag.CrfTagger
 * @see org.coordinatekit.crf.core.tag.TaggedPositionedToken
 * @see org.coordinatekit.crf.core.tag.TagScore
 */
@NullMarked
package org.coordinatekit.crf.mallet.tag;

import org.jspecify.annotations.NullMarked;
