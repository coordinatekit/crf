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
package org.coordinatekit.crf.core.preprocessing;

import org.coordinatekit.crf.core.InputSequence;
import org.coordinatekit.crf.core.PositionedToken;
import org.coordinatekit.crf.core.Sequence;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NullMarked
class CompositeFeatureExtractorTest {
    @Test
    void combinesMultipleExtractors() {
        FeatureExtractor<String> lengthExtractor = (seq, pos) -> Set.of("LENGTH=" + seq.get(pos).token().length());
        FeatureExtractor<String> lowerExtractor = (seq, pos) -> Set
                .of("LOWER=" + seq.get(pos).token().toLowerCase(Locale.ROOT));

        CompositeFeatureExtractor<String> composite = new CompositeFeatureExtractor<>(
                List.of(lengthExtractor, lowerExtractor)
        );

        Sequence<PositionedToken> sequence = new InputSequence(List.of("Hello"));
        Set<String> features = composite.extractAt(sequence, 0);

        assertEquals(Set.of("LENGTH=5", "LOWER=hello"), features);
    }

    @Test
    void duplicateFeaturesAreDeduped() {
        FeatureExtractor<String> extractor1 = (seq, pos) -> Set.of("FEATURE_A", "FEATURE_B");
        FeatureExtractor<String> extractor2 = (seq, pos) -> Set.of("FEATURE_B", "FEATURE_C");

        CompositeFeatureExtractor<String> composite = new CompositeFeatureExtractor<>(List.of(extractor1, extractor2));

        Sequence<PositionedToken> sequence = new InputSequence(List.of("token"));
        Set<String> features = composite.extractAt(sequence, 0);

        assertEquals(Set.of("FEATURE_A", "FEATURE_B", "FEATURE_C"), features);
    }

    @Test
    void emptyExtractorList() {
        CompositeFeatureExtractor<String> composite = new CompositeFeatureExtractor<>(List.of());

        Sequence<PositionedToken> sequence = new InputSequence(List.of("hello"));
        Set<String> features = composite.extractAt(sequence, 0);

        assertEquals(Set.of(), features);
    }

    @Test
    void extractorReturningEmptySet() {
        FeatureExtractor<String> emptyExtractor = (seq, pos) -> Set.of();
        FeatureExtractor<String> lengthExtractor = (seq, pos) -> Set.of("LENGTH=" + seq.get(pos).token().length());

        CompositeFeatureExtractor<String> composite = new CompositeFeatureExtractor<>(
                List.of(emptyExtractor, lengthExtractor)
        );

        Sequence<PositionedToken> sequence = new InputSequence(List.of("hello"));
        Set<String> features = composite.extractAt(sequence, 0);

        assertEquals(Set.of("LENGTH=5"), features);
    }

    @Test
    void extractsAtCorrectPosition() {
        FeatureExtractor<String> tokenExtractor = (seq, pos) -> Set.of("TOKEN=" + seq.get(pos).token());

        CompositeFeatureExtractor<String> composite = new CompositeFeatureExtractor<>(List.of(tokenExtractor));

        Sequence<PositionedToken> sequence = new InputSequence(List.of("first", "second", "third"));

        assertEquals(Set.of("TOKEN=first"), composite.extractAt(sequence, 0));
        assertEquals(Set.of("TOKEN=second"), composite.extractAt(sequence, 1));
        assertEquals(Set.of("TOKEN=third"), composite.extractAt(sequence, 2));
    }

    @Test
    void preservesOrderOfExtraction() {
        FeatureExtractor<String> first = (seq, pos) -> Set.of("FIRST");
        FeatureExtractor<String> second = (seq, pos) -> Set.of("SECOND");
        FeatureExtractor<String> third = (seq, pos) -> Set.of("THIRD");

        CompositeFeatureExtractor<String> composite = new CompositeFeatureExtractor<>(List.of(first, second, third));

        Sequence<PositionedToken> sequence = new InputSequence(List.of("token"));
        Set<String> features = composite.extractAt(sequence, 0);

        assertEquals(Set.of("FIRST", "SECOND", "THIRD"), features);
    }

    @Test
    void singleExtractor() {
        FeatureExtractor<String> extractor = (seq, pos) -> Set.of("TOKEN=" + seq.get(pos).token());

        CompositeFeatureExtractor<String> composite = new CompositeFeatureExtractor<>(List.of(extractor));

        Sequence<PositionedToken> sequence = new InputSequence(List.of("test"));
        Set<String> features = composite.extractAt(sequence, 0);

        assertEquals(Set.of("TOKEN=test"), features);
    }

    @Test
    void worksWithIntegerFeatures() {
        FeatureExtractor<Integer> lengthExtractor = (seq, pos) -> Set.of(seq.get(pos).token().length());
        FeatureExtractor<Integer> hashExtractor = (seq, pos) -> Set.of(seq.get(pos).token().hashCode());

        CompositeFeatureExtractor<Integer> composite = new CompositeFeatureExtractor<>(
                List.of(lengthExtractor, hashExtractor)
        );

        Sequence<PositionedToken> sequence = new InputSequence(List.of("hi"));
        Set<Integer> features = composite.extractAt(sequence, 0);

        assertEquals(Set.of(2, "hi".hashCode()), features);
    }
}
