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
package org.coordinatekit.crf.annotator;

import static org.coordinatekit.crf.core.preprocessing.TrainingSegments.excluded;
import static org.coordinatekit.crf.core.preprocessing.TrainingSegments.token;

import org.coordinatekit.crf.core.PositionedToken;
import org.coordinatekit.crf.core.Sequence;
import org.coordinatekit.crf.core.preprocessing.Feature;
import org.coordinatekit.crf.core.preprocessing.FeatureExtractor;
import org.coordinatekit.crf.core.preprocessing.Segment;
import org.coordinatekit.crf.core.preprocessing.SegmentKind;
import org.coordinatekit.crf.core.preprocessing.Tokenization;
import org.coordinatekit.crf.core.preprocessing.TrainingSegment;
import org.coordinatekit.crf.core.tag.TaggedPositionedToken;
import org.coordinatekit.crf.core.tag.TaggedTokenization;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pure helpers shared by the orchestrators in this package — {@link Annotator} and
 * {@link RetokenizeReviewer}.
 *
 * <p>
 * Each method is stateless: it derives its result solely from its arguments, with no dependence on
 * any orchestrator's configuration. Keeping them here lets both orchestrators present and persist a
 * sequence identically — the display-feature projection, the verbose-feature source priority, and
 * the surface-preserving segment assembly are defined once.
 */
@NullMarked
final class AnnotatorSupport {
    private AnnotatorSupport() {}

    /**
     * Runs a display feature extractor over every position of a positioned-token sequence.
     *
     * @param extractor the extractor to run, or {@code null} when not configured
     * @param positionedTokens the positioned tokens to extract features from
     * @return one feature set per token, or {@code null} when {@code extractor} is {@code null}
     */
    static @Nullable List<Set<Feature>> extractDisplayFeatures(
            @Nullable FeatureExtractor extractor,
            Sequence<? extends PositionedToken> positionedTokens
    ) {
        if (extractor == null) {
            return null;
        }
        List<Set<Feature>> features = new ArrayList<>(positionedTokens.size());
        for (int position = 0; position < positionedTokens.size(); position++) {
            features.add(extractor.extractAt(positionedTokens, position));
        }
        return features;
    }

    /**
     * Resolves the per-token verbose display features for a sequence, by source priority: the supplied
     * {@code verboseFeatureExtractor} when set, then the tagger's embedded
     * {@link TaggedPositionedToken#features() features} when the sequence was tagged, then none.
     *
     * @param verboseFeatureExtractor the verbose display feature extractor, or {@code null} when not
     *        configured
     * @param tagged the tagger's output for the sequence, or {@code null} on the no-tagger path
     * @param positionedTokens the positioned tokens of the sequence
     * @param <T> the tag type
     * @return one verbose feature set per token, or {@code null} when no verbose source applies
     */
    static <T extends Comparable<T>> @Nullable List<Set<Feature>> resolveVerboseFeatures(
            @Nullable FeatureExtractor verboseFeatureExtractor,
            @Nullable TaggedTokenization<T> tagged,
            Sequence<? extends PositionedToken> positionedTokens
    ) {
        if (verboseFeatureExtractor != null) {
            return extractDisplayFeatures(verboseFeatureExtractor, positionedTokens);
        }
        if (tagged != null) {
            return tagged.taggedSequence().stream().map(TaggedPositionedToken::features).toList();
        }
        return null;
    }

    /**
     * Assembles the ordered training segments for an accepted sequence by mapping each tokenizer
     * segment in document order: excluded runs pass through untagged, and each token segment is paired
     * with the user-chosen tag at the matching token index. The mapping is surface-preserving, so a
     * {@link org.coordinatekit.crf.core.preprocessing.TrainingSequence#surface() surface} built from
     * the result reproduces the tokenizer's input exactly.
     *
     * @param tokenized the authoritative tokenization, carrying tokens and excluded runs as segments
     * @param finalTags the per-token tags chosen by the user, one per token in order
     * @param <T> the tag type
     * @return the segments in document order
     * @throws IllegalArgumentException if {@code finalTags} does not have one tag per token segment
     */
    static <T> List<TrainingSegment<T>> toSegments(Tokenization tokenized, List<T> finalTags) {
        long tokenSegmentCount = tokenized.segments().stream().filter(segment -> segment.kind() == SegmentKind.TOKEN)
                .count();
        if (finalTags.size() != tokenSegmentCount) {
            throw new IllegalArgumentException(
                    String.format(
                            "Expected one tag per token segment: got %d tags for %d token segments.",
                            finalTags.size(),
                            tokenSegmentCount
                    )
            );
        }
        List<TrainingSegment<T>> segments = new ArrayList<>();
        int tokenIndex = 0;
        for (Segment segment : tokenized.segments()) {
            if (segment.kind() == SegmentKind.TOKEN) {
                segments.add(token(finalTags.get(tokenIndex++), segment.text()));
            } else {
                segments.add(excluded(segment.text()));
            }
        }
        return segments;
    }
}
