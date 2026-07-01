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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositeFeatureExtractorTest {
    @Test
    void combinesMultipleExtractors() {
        FeatureExtractor lengthExtractor = (seq, pos) -> Set
                .of(Features.of("LENGTH", String.valueOf(seq.get(pos).token().length())));
        FeatureExtractor lowerExtractor = (seq, pos) -> Set
                .of(Features.of("LOWER", seq.get(pos).token().toLowerCase(Locale.ROOT)));

        CompositeFeatureExtractor composite = CompositeFeatureExtractor.of(lengthExtractor, lowerExtractor);

        Sequence<PositionedToken> sequence = new InputSequence(List.of("Hello"));
        Set<Feature> features = composite.extractAt(sequence, 0);

        assertEquals(Set.of(Features.of("LENGTH", "5"), Features.of("LOWER", "hello")), features);
    }

    @Test
    void duplicateFeaturesAreDeduped() {
        FeatureExtractor extractor1 = (seq, pos) -> Set.of(Features.of("FEATURE_A"), Features.of("FEATURE_B"));
        FeatureExtractor extractor2 = (seq, pos) -> Set.of(Features.of("FEATURE_B"), Features.of("FEATURE_C"));

        CompositeFeatureExtractor composite = CompositeFeatureExtractor.of(extractor1, extractor2);

        Sequence<PositionedToken> sequence = new InputSequence(List.of("token"));
        Set<Feature> features = composite.extractAt(sequence, 0);

        assertEquals(Set.of(Features.of("FEATURE_A"), Features.of("FEATURE_B"), Features.of("FEATURE_C")), features);
    }

    @Test
    void emptyExtractorList() {
        CompositeFeatureExtractor composite = CompositeFeatureExtractor.of();

        Sequence<PositionedToken> sequence = new InputSequence(List.of("hello"));
        Set<Feature> features = composite.extractAt(sequence, 0);

        assertEquals(Set.of(), features);
    }

    @Test
    void of__combinesMultipleExtractors() {
        FeatureExtractor lengthExtractor = (seq, pos) -> Set
                .of(Features.of("LENGTH", String.valueOf(seq.get(pos).token().length())));
        FeatureExtractor lowerExtractor = (seq, pos) -> Set
                .of(Features.of("LOWER", seq.get(pos).token().toLowerCase(Locale.ROOT)));

        CompositeFeatureExtractor composite = CompositeFeatureExtractor.of(lengthExtractor, lowerExtractor);

        Sequence<PositionedToken> sequence = new InputSequence(List.of("Hello"));
        Set<Feature> features = composite.extractAt(sequence, 0);

        assertEquals(Set.of(Features.of("LENGTH", "5"), Features.of("LOWER", "hello")), features);
    }

    @Test
    void of__noExtractors() {
        CompositeFeatureExtractor composite = CompositeFeatureExtractor.of();

        Sequence<PositionedToken> sequence = new InputSequence(List.of("hello"));
        Set<Feature> features = composite.extractAt(sequence, 0);

        assertEquals(Set.of(), features);
    }

    @Test
    void of__singleExtractor() {
        FeatureExtractor extractor = (seq, pos) -> Set.of(Features.of("TOKEN", seq.get(pos).token()));

        CompositeFeatureExtractor composite = CompositeFeatureExtractor.of(extractor);

        Sequence<PositionedToken> sequence = new InputSequence(List.of("test"));
        Set<Feature> features = composite.extractAt(sequence, 0);

        assertEquals(Set.of(Features.of("TOKEN", "test")), features);
    }

    @Test
    void extractorReturningEmptySet() {
        FeatureExtractor emptyExtractor = (seq, pos) -> Set.of();
        FeatureExtractor lengthExtractor = (seq, pos) -> Set
                .of(Features.of("LENGTH", String.valueOf(seq.get(pos).token().length())));

        CompositeFeatureExtractor composite = CompositeFeatureExtractor.of(emptyExtractor, lengthExtractor);

        Sequence<PositionedToken> sequence = new InputSequence(List.of("hello"));
        Set<Feature> features = composite.extractAt(sequence, 0);

        assertEquals(Set.of(Features.of("LENGTH", "5")), features);
    }

    @Test
    void extractsAtCorrectPosition() {
        FeatureExtractor tokenExtractor = (seq, pos) -> Set.of(Features.of("TOKEN", seq.get(pos).token()));

        CompositeFeatureExtractor composite = CompositeFeatureExtractor.of(tokenExtractor);

        Sequence<PositionedToken> sequence = new InputSequence(List.of("first", "second", "third"));

        assertEquals(Set.of(Features.of("TOKEN", "first")), composite.extractAt(sequence, 0));
        assertEquals(Set.of(Features.of("TOKEN", "second")), composite.extractAt(sequence, 1));
        assertEquals(Set.of(Features.of("TOKEN", "third")), composite.extractAt(sequence, 2));
    }

    @Test
    void preservesOrderOfExtraction() {
        FeatureExtractor first = (seq, pos) -> Set.of(Features.of("FIRST"));
        FeatureExtractor second = (seq, pos) -> Set.of(Features.of("SECOND"));
        FeatureExtractor third = (seq, pos) -> Set.of(Features.of("THIRD"));

        CompositeFeatureExtractor composite = CompositeFeatureExtractor.of(first, second, third);

        Sequence<PositionedToken> sequence = new InputSequence(List.of("token"));
        Set<Feature> features = composite.extractAt(sequence, 0);

        assertEquals(Set.of(Features.of("FIRST"), Features.of("SECOND"), Features.of("THIRD")), features);
    }

    @Test
    void singleExtractor() {
        FeatureExtractor extractor = (seq, pos) -> Set.of(Features.of("TOKEN", seq.get(pos).token()));

        CompositeFeatureExtractor composite = CompositeFeatureExtractor.of(extractor);

        Sequence<PositionedToken> sequence = new InputSequence(List.of("test"));
        Set<Feature> features = composite.extractAt(sequence, 0);

        assertEquals(Set.of(Features.of("TOKEN", "test")), features);
    }
}
