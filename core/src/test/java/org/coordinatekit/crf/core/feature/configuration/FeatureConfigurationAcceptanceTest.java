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
package org.coordinatekit.crf.core.feature.configuration;

import static org.coordinatekit.crf.core.feature.Feature.createFeatureWithValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.coordinatekit.crf.core.feature.CompositeFeatureExtractor;
import org.coordinatekit.crf.core.feature.FeatureExtractor;
import org.coordinatekit.crf.core.feature.SubstringFeatureExtractor;
import org.coordinatekit.crf.core.feature.TransformingFeatureExtractor;
import org.coordinatekit.crf.core.feature.WindowFeatureExtractor;
import org.coordinatekit.crf.core.feature.XPathFeatureExtractor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * The Phase 3 acceptance guarantee: a windowed, composite tree assembled from a configuration
 * <strong>file</strong> renders the exact same feature strings — including the {@code PREV_n__}/
 * {@code NEXT_n__} offsets — as the equivalent hand-coded extractor composition, so a model trained
 * through a config-assembled extractor is interchangeable with one trained from code.
 */
class FeatureConfigurationAcceptanceTest {
    private static final List<String> TOKENS = List.of("Ohio", "is", "a", "state");

    private static Path baseDirectory() {
        return ConfigurationTestSupport
                .resourceDirectory("/org/coordinatekit/crf/core/feature/configuration/states.xml");
    }

    private static FeatureExtractor handCoded(Path baseDirectory) throws IOException {
        FeatureExtractor length = new TransformingFeatureExtractor(
                token -> Set.of(createFeatureWithValue("LENGTH", "" + token.length()))
        );
        FeatureExtractor prefix = SubstringFeatureExtractor
                .builder(substring -> createFeatureWithValue("PREFIX2", substring)).ending(false)
                .includeIfLessThanLength(true).length(2).build();
        FeatureExtractor lookup;
        try (InputStream states = Files.newInputStream(baseDirectory.resolve("states.xml"))) {
            lookup = XPathFeatureExtractor.builder(states, "/states/state").caseSensitive(true)
                    .presentFeature(createFeatureWithValue("STATE", "US")).build();
        }
        CompositeFeatureExtractor composite = CompositeFeatureExtractor.of(length, prefix, lookup);
        return WindowFeatureExtractor.builder(composite).windowBefore(3).windowAfter(3).includeCurrentToken(true)
                .build();
    }

    private static Path featuresXml() {
        return baseDirectory().resolve("features.xml");
    }

    private static Set<String> render(FeatureExtractor extractor, int position) {
        return ConfigurationTestSupport.renderFeatures(extractor, TOKENS, position);
    }

    @Test
    void assembledTreeMatchesHandCodedComposition() throws IOException {
        // ARRANGE //
        Path baseDirectory = baseDirectory();
        FeatureExtractor assembled = FeatureConfiguration.load(featuresXml()).fullFeatureExtractor();
        FeatureExtractor handCoded = handCoded(baseDirectory);

        // ACT & ASSERT //
        for (int position = 0; position < TOKENS.size(); position++) {
            assertEquals(
                    render(handCoded, position),
                    render(assembled, position),
                    "rendered features differ at position " + position
            );
        }
    }

    @Test
    void assembledTreeStampsWindowOffsetsAtFirstToken() {
        // ARRANGE //
        FeatureExtractor assembled = FeatureConfiguration.load(featuresXml()).fullFeatureExtractor();

        // ACT //
        Set<String> rendered = render(assembled, 0);

        // ASSERT //
        // "Ohio" is the current token and the only dictionary hit; the three following tokens are
        // stamped NEXT_1__/NEXT_2__/NEXT_3__, and none of them is a state so no NEXT_n__STATE appears.
        assertEquals(
                Set.of(
                        "LENGTH=4",
                        "PREFIX2=Oh",
                        "STATE=US",
                        "NEXT_1__LENGTH=2",
                        "NEXT_1__PREFIX2=is",
                        "NEXT_2__LENGTH=1",
                        "NEXT_2__PREFIX2=a",
                        "NEXT_3__LENGTH=5",
                        "NEXT_3__PREFIX2=st"
                ),
                rendered
        );
    }
}
