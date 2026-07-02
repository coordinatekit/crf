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

import static org.coordinatekit.crf.core.preprocessing.Feature.createFeature;
import static org.coordinatekit.crf.core.preprocessing.Feature.createFeatureWithValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.coordinatekit.crf.core.InputSequence;
import org.coordinatekit.crf.core.PositionedToken;
import org.coordinatekit.crf.core.Sequence;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CompositeFeatureExtractorTest {
    record ExtractAtParameters(
            String name,
            List<FeatureExtractor> extractors,
            List<String> tokens,
            int position,
            Set<Feature> expectedResult
    ) {}

    static Stream<ExtractAtParameters> extractAt() {
        FeatureExtractor length = (seq, pos) -> Set
                .of(createFeatureWithValue("LENGTH", String.valueOf(seq.get(pos).token().length())));
        FeatureExtractor lower = (seq, pos) -> Set
                .of(createFeatureWithValue("LOWER", seq.get(pos).token().toLowerCase(Locale.ROOT)));
        FeatureExtractor token = (seq, pos) -> Set.of(createFeatureWithValue("TOKEN", seq.get(pos).token()));
        FeatureExtractor empty = (seq, pos) -> Set.of();

        return Stream.of(
                new ExtractAtParameters("no_extractors", List.of(), List.of("hello"), 0, Set.of()),
                new ExtractAtParameters(
                        "single_extractor",
                        List.of(token),
                        List.of("test"),
                        0,
                        Set.of(createFeatureWithValue("TOKEN", "test"))
                ),
                new ExtractAtParameters(
                        "combines_multiple",
                        List.of(length, lower),
                        List.of("Hello"),
                        0,
                        Set.of(createFeatureWithValue("LENGTH", "5"), createFeatureWithValue("LOWER", "hello"))
                ),
                new ExtractAtParameters(
                        "dedupes_duplicates",
                        List.of(
                                (seq, pos) -> Set.of(createFeature("FEATURE_A"), createFeature("FEATURE_B")),
                                (seq, pos) -> Set.of(createFeature("FEATURE_B"), createFeature("FEATURE_C"))
                        ),
                        List.of("token"),
                        0,
                        Set.of(createFeature("FEATURE_A"), createFeature("FEATURE_B"), createFeature("FEATURE_C"))
                ),
                new ExtractAtParameters(
                        "skips_empty_extractor",
                        List.of(empty, length),
                        List.of("hello"),
                        0,
                        Set.of(createFeatureWithValue("LENGTH", "5"))
                ),
                new ExtractAtParameters(
                        "position_0",
                        List.of(token),
                        List.of("first", "second", "third"),
                        0,
                        Set.of(createFeatureWithValue("TOKEN", "first"))
                ),
                new ExtractAtParameters(
                        "position_1",
                        List.of(token),
                        List.of("first", "second", "third"),
                        1,
                        Set.of(createFeatureWithValue("TOKEN", "second"))
                ),
                new ExtractAtParameters(
                        "position_2",
                        List.of(token),
                        List.of("first", "second", "third"),
                        2,
                        Set.of(createFeatureWithValue("TOKEN", "third"))
                ),
                new ExtractAtParameters(
                        "preserves_order",
                        List.of(
                                (seq, pos) -> Set.of(createFeature("FIRST")),
                                (seq, pos) -> Set.of(createFeature("SECOND")),
                                (seq, pos) -> Set.of(createFeature("THIRD"))
                        ),
                        List.of("token"),
                        0,
                        Set.of(createFeature("FIRST"), createFeature("SECOND"), createFeature("THIRD"))
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void extractAt(ExtractAtParameters parameters) {
        // ARRANGE //
        CompositeFeatureExtractor composite = CompositeFeatureExtractor
                .of(parameters.extractors().toArray(new FeatureExtractor[0]));
        Sequence<PositionedToken> sequence = new InputSequence(parameters.tokens());

        // ACT //
        Set<Feature> actual = composite.extractAt(sequence, parameters.position());

        // ASSERT //
        assertEquals(parameters.expectedResult(), actual);
    }
}
