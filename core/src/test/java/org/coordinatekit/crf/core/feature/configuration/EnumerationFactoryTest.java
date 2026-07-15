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
import static org.coordinatekit.crf.core.feature.configuration.ConfigurationTestSupport.currentDirectoryUrl;
import static org.coordinatekit.crf.core.feature.configuration.ConfigurationTestSupport.renderFeatures;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.coordinatekit.crf.core.PositionedToken;
import org.coordinatekit.crf.core.Sequence;
import org.coordinatekit.crf.core.feature.FeatureExtractor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/**
 * Covers the {@link ParameterKind#ENUMERATION} kind end-to-end with a synthetic factory, since no
 * built-in factory uses it: the assembler applies the declared default, honors a supplied value in
 * the allowed set, and rejects a value outside it with a located
 * {@link FeatureConfigurationException}.
 */
class EnumerationFactoryTest {
    /** A synthetic leaf factory that emits {@code MODE=<mode>} for an enumeration parameter. */
    private static final class ModeFactory implements LeafFeatureExtractorFactory {
        @Override
        public FeatureExtractor create(FeatureExtractorParameters parameters) {
            String mode = parameters.getEnumeration("mode");
            return (Sequence<? extends PositionedToken> sequence, int position) -> Set
                    .of(createFeatureWithValue("MODE", mode));
        }

        @Override
        public Set<ParameterDescriptor> parameters() {
            return Set.of(
                    ParameterDescriptor.builder("mode", ParameterKind.ENUMERATION).allowedValues(Set.of("fast", "slow"))
                            .defaultValue("fast").build()
            );
        }

        @Override
        public String type() {
            return "mode";
        }
    }

    private static final FeatureExtractorAssembler ASSEMBLER = new FeatureExtractorAssembler(
            FeatureExtractorFactoryRegistry.of(List.of(new ModeFactory()))
    );

    private static Set<String> render(FeatureExtractorNode node) {
        FeatureExtractor extractor = ASSEMBLER.assemble(node, currentDirectoryUrl()).fullFeatureExtractor();
        return renderFeatures(extractor, List.of("token"), 0);
    }

    @Test
    void assemble__appliesEnumerationDefault() {
        // ACT & ASSERT //
        assertEquals(Set.of("MODE=fast"), render(FeatureExtractorNodes.builder("mode").build()));
    }

    @Test
    void assemble__honorsSuppliedEnumeration() {
        // ACT & ASSERT //
        assertEquals(
                Set.of("MODE=slow"),
                render(FeatureExtractorNodes.builder("mode").parameter("mode", "slow").build())
        );
    }

    @Test
    void assemble__rejectsEnumerationOutsideAllowed() {
        // ACT //
        FeatureConfigurationException exception = assertThrows(
                FeatureConfigurationException.class,
                () -> render(FeatureExtractorNodes.builder("mode").parameter("mode", "medium").build())
        );

        // ASSERT //
        assertEquals(
                "extractor 'mode' — parameter 'mode' expects one of [fast, slow] but got 'medium'",
                exception.getMessage()
        );
    }
}
