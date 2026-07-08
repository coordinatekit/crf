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

import static org.coordinatekit.crf.core.feature.Feature.createFeature;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.coordinatekit.crf.core.PositionedToken;
import org.coordinatekit.crf.core.Sequence;
import org.coordinatekit.crf.core.feature.Feature;
import org.coordinatekit.crf.core.feature.FeatureExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tests {@link FeatureExtractorAssembler}: it dispatches to a leaf factory and unions the features
 * of a nesting factory's children, using synthetic factories decoupled from any built-in one; it
 * builds deeply nested trees whose rendered features match the hand-coded equivalent; it enforces
 * child arity and the depth cap; and it rejects an unknown type and a factory that is neither a
 * leaf nor a nesting factory. The nested-window case pins down that {@link Feature#withOffset(int)}
 * replaces rather than accumulates the offset, so an outer window overwrites an inner one.
 */
class FeatureExtractorAssemblerTest {
    private static final URL BASE_LOCATION = ConfigurationTestSupport.pathUrl("/base");
    private static final FeatureExtractorAssembler DISCOVERED_ASSEMBLER = new FeatureExtractorAssembler(
            FeatureExtractorFactoryRegistry.load()
    );

    /** A synthetic leaf factory emitting a single fixed feature, decoupled from any real factory. */
    private record SyntheticLeafFactory(String type, String featureName) implements LeafFeatureExtractorFactory {
        @Override
        public FeatureExtractor create(FeatureExtractorParameters parameters) {
            return (Sequence<? extends PositionedToken> sequence, int position) -> Set.of(createFeature(featureName));
        }

        @Override
        public String type() {
            return type;
        }
    }

    /**
     * A synthetic nesting factory unioning its children's features, decoupled from any real factory.
     */
    private static final class SyntheticNestingFactory implements NestingFeatureExtractorFactory {
        @Override
        public FeatureExtractor create(FeatureExtractorParameters parameters, List<FeatureExtractor> children) {
            return (Sequence<? extends PositionedToken> sequence, int position) -> children.stream()
                    .flatMap(child -> child.extractAt(sequence, position).stream()).collect(Collectors.toSet());
        }

        @Override
        public int maximumChildren() {
            return Integer.MAX_VALUE;
        }

        @Override
        public String type() {
            return "synthetic-nesting";
        }
    }

    private static Set<String> render(FeatureExtractor extractor, List<String> tokens, int position) {
        return ConfigurationTestSupport.renderFeatures(extractor, tokens, position);
    }

    record ArityParameters(
            String name,
            Executable action,
            Class<? extends Exception> expectedClass,
            String expectedMessage
    ) {}

    static Stream<ArityParameters> arity() {
        return Stream.of(
                new ArityParameters(
                        "window_with_no_children",
                        () -> DISCOVERED_ASSEMBLER
                                .assemble(FeatureExtractorNodes.builder("window").build(), BASE_LOCATION),
                        FeatureConfigurationException.class,
                        "extractor 'window' — expected exactly 1 child but got 0"
                ),
                new ArityParameters(
                        "window_with_two_children",
                        () -> DISCOVERED_ASSEMBLER.assemble(
                                FeatureExtractorNodes.builder("window")
                                        .child(FeatureExtractorNodes.builder("length").build())
                                        .child(FeatureExtractorNodes.builder("length").build()).build(),
                                BASE_LOCATION
                        ),
                        FeatureConfigurationException.class,
                        "extractor 'window' — expected exactly 1 child but got 2"
                ),
                new ArityParameters(
                        "composite_with_no_children",
                        () -> DISCOVERED_ASSEMBLER
                                .assemble(FeatureExtractorNodes.builder("composite").build(), BASE_LOCATION),
                        FeatureConfigurationException.class,
                        "extractor 'composite' — expected at least 1 child but got 0"
                ),
                new ArityParameters(
                        "leaf_given_children",
                        () -> DISCOVERED_ASSEMBLER.assemble(
                                FeatureExtractorNodes.builder("length")
                                        .child(FeatureExtractorNodes.builder("length").build()).build(),
                                BASE_LOCATION
                        ),
                        FeatureConfigurationException.class,
                        "extractor 'length' — expected no children but got 1"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void arity(ArityParameters parameters) {
        // ACT //
        Exception exception = assertThrows(parameters.expectedClass(), parameters.action());

        // ASSERT //
        assertEquals(parameters.expectedMessage(), exception.getMessage());
    }

    @Test
    void assemble__deepNestingWithinCapSucceeds() {
        // ARRANGE //
        FeatureExtractorNode node = nestedComposites(FeatureExtractorNode.MAXIMUM_NESTING_DEPTH - 1);

        // ACT //
        FeatureExtractor extractor = DISCOVERED_ASSEMBLER.assemble(node, BASE_LOCATION);

        // ASSERT //
        assertEquals(Set.of("LENGTH=3"), render(extractor, List.of("abc"), 0));
    }

    record DepthCapParameters(String name, int depth) {}

    static Stream<DepthCapParameters> assemble__depthCapExceeded() {
        return Stream.of(
                new DepthCapParameters("at_cap", FeatureExtractorNode.MAXIMUM_NESTING_DEPTH),
                new DepthCapParameters("over_cap", FeatureExtractorNode.MAXIMUM_NESTING_DEPTH + 5)
        );
    }

    @MethodSource
    @ParameterizedTest
    void assemble__depthCapExceeded(DepthCapParameters parameters) {
        // ARRANGE //
        FeatureExtractorNode node = nestedComposites(parameters.depth());

        // ACT //
        FeatureConfigurationException exception = assertThrows(
                FeatureConfigurationException.class,
                () -> DISCOVERED_ASSEMBLER.assemble(node, BASE_LOCATION)
        );

        // ASSERT //
        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(
                message.contains("nesting is deeper than the maximum of " + FeatureExtractorNode.MAXIMUM_NESTING_DEPTH),
                "message should report the depth cap; was: " + message
        );
    }

    @Test
    void assemble__factoryCreateRejectionBecomesLocatedException() {
        // ARRANGE //
        FeatureExtractorFactory strictFactory = new LeafFeatureExtractorFactory() {
            @Override
            public FeatureExtractor create(FeatureExtractorParameters parameters) {
                throw new IllegalArgumentException("configuration is invalid");
            }

            @Override
            public String type() {
                return "strict-create";
            }
        };
        FeatureExtractorAssembler assembler = new FeatureExtractorAssembler(
                FeatureExtractorFactoryRegistry.of(List.of(strictFactory))
        );

        // ACT //
        FeatureConfigurationException exception = assertThrows(
                FeatureConfigurationException.class,
                () -> assembler.assemble(FeatureExtractorNodes.builder("strict-create").build(), BASE_LOCATION)
        );

        // ASSERT //
        assertEquals("extractor 'strict-create' — configuration is invalid", exception.getMessage());
        assertEquals("strict-create", exception.extractorType());
        assertTrue(exception.sourceLocation().isEmpty());
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    }

    @Test
    void assemble__factoryValidateRejectionBecomesLocatedException() {
        // ARRANGE //
        FeatureExtractorFactory strictFactory = new LeafFeatureExtractorFactory() {
            @Override
            public FeatureExtractor create(FeatureExtractorParameters parameters) {
                throw new AssertionError("create must not run when validate rejects the configuration");
            }

            @Override
            public String type() {
                return "strict";
            }

            @Override
            public void validate(FeatureExtractorParameters parameters) {
                throw new IllegalArgumentException("configuration is invalid");
            }
        };
        FeatureExtractorAssembler assembler = new FeatureExtractorAssembler(
                FeatureExtractorFactoryRegistry.of(List.of(strictFactory))
        );

        // ACT //
        FeatureConfigurationException exception = assertThrows(
                FeatureConfigurationException.class,
                () -> assembler.assemble(FeatureExtractorNodes.builder("strict").build(), BASE_LOCATION)
        );

        // ASSERT //
        assertEquals("extractor 'strict' — configuration is invalid", exception.getMessage());
        assertEquals("strict", exception.extractorType());
        assertTrue(exception.sourceLocation().isEmpty());
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    }

    @Test
    void assemble__leaf() {
        // ARRANGE //
        FeatureExtractorNode node = FeatureExtractorNodes.builder("synthetic-leaf").build();
        FeatureExtractorAssembler assembler = new FeatureExtractorAssembler(
                FeatureExtractorFactoryRegistry.of(List.of(new SyntheticLeafFactory("synthetic-leaf", "SYNTH")))
        );

        // ACT //
        FeatureExtractor extractor = assembler.assemble(node, BASE_LOCATION);

        // ASSERT //
        assertEquals(Set.of("SYNTH"), render(extractor, List.of("abc"), 0));
    }

    @Test
    void assemble__nestedWindowOverwritesInnerOffset() {
        // ARRANGE //
        // Two windows each looking back one token. Because withOffset replaces the offset, the
        // outer window overwrites the inner one's -1 rather than accumulating to -2, so no PREV_2__
        // feature is ever produced.
        FeatureExtractorNode node = FeatureExtractorNodes.builder("window").parameter("before", "1")
                .parameter("after", "0")
                .child(
                        FeatureExtractorNodes.builder("window").parameter("before", "1").parameter("after", "0")
                                .child(FeatureExtractorNodes.builder("length").build()).build()
                ).build();

        // ACT //
        FeatureExtractor extractor = DISCOVERED_ASSEMBLER.assemble(node, BASE_LOCATION);

        // ASSERT //
        assertEquals(
                Set.of("LENGTH=4", "PREV_1__LENGTH=3", "PREV_1__LENGTH=2"),
                render(extractor, List.of("a", "bb", "ccc", "dddd"), 3)
        );
    }

    @Test
    void assemble__nestingUnionsChildren() {
        // ARRANGE //
        FeatureExtractorNode node = FeatureExtractorNodes.builder("synthetic-nesting")
                .child(FeatureExtractorNodes.builder("synthetic-a").build())
                .child(FeatureExtractorNodes.builder("synthetic-b").build()).build();
        FeatureExtractorAssembler assembler = new FeatureExtractorAssembler(
                FeatureExtractorFactoryRegistry.of(
                        List.of(
                                new SyntheticNestingFactory(),
                                new SyntheticLeafFactory("synthetic-a", "A"),
                                new SyntheticLeafFactory("synthetic-b", "B")
                        )
                )
        );

        // ACT //
        FeatureExtractor extractor = assembler.assemble(node, BASE_LOCATION);

        // ASSERT //
        assertEquals(Set.of("A", "B"), render(extractor, List.of("x"), 0));
    }

    @Test
    void assemble__unknownType() {
        // ARRANGE //
        FeatureExtractorNode node = FeatureExtractorNodes.builder("bogus").build();

        // ACT //
        FeatureConfigurationException exception = assertThrows(
                FeatureConfigurationException.class,
                () -> DISCOVERED_ASSEMBLER.assemble(node, BASE_LOCATION)
        );

        // ASSERT //
        assertEquals("extractor 'bogus' — unknown extractor type 'bogus'", exception.getMessage());
    }

    /**
     * Builds {@code depth} nested {@code composite} nodes wrapping a single {@code length} leaf, a tree
     * whose assembled depth is {@code depth + 1}.
     *
     * @param depth the number of nested composites
     * @return the root composite node
     */
    private static FeatureExtractorNode nestedComposites(int depth) {
        FeatureExtractorNode node = FeatureExtractorNodes.builder("length").build();
        for (int level = 0; level < depth; level++) {
            node = FeatureExtractorNodes.builder("composite").child(node).build();
        }
        return node;
    }
}
