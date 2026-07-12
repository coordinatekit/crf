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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.coordinatekit.crf.core.PositionedToken;
import org.coordinatekit.crf.core.Sequence;
import org.coordinatekit.crf.core.feature.FeatureExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Tests {@link FeatureExtractorFactoryRegistry}: the list-based
 * {@link FeatureExtractorFactoryRegistry#of of} indexes synthetic providers by type, a missing type
 * looks up empty, two providers sharing a type fail fast with a
 * {@link DuplicateFactoryTypeException} that names both classes, and a factory whose kind and
 * declared child bounds are inconsistent fails fast with an
 * {@link InvalidFactoryDeclarationException}.
 */
class FeatureExtractorFactoryRegistryTest {
    /** A synthetic leaf factory declaring a configurable type and emitting nothing. */
    private record SyntheticFactory(String type) implements LeafFeatureExtractorFactory {
        @Override
        public FeatureExtractor create(FeatureExtractorParameters parameters) {
            return (Sequence<? extends PositionedToken> sequence, int position) -> Set.of();
        }

        @Override
        public String type() {
            return type;
        }
    }

    /** A second synthetic class so a duplicate type is a genuine two-class collision. */
    private static final class ShadowFactory implements LeafFeatureExtractorFactory {
        @Override
        public FeatureExtractor create(FeatureExtractorParameters parameters) {
            return (Sequence<? extends PositionedToken> sequence, int position) -> Set.of();
        }

        @Override
        public String type() {
            return "length";
        }
    }

    /** A factory that implements only the root interface, neither leaf nor nesting. */
    private static final class RootOnlyFactory implements FeatureExtractorFactory {
        @Override
        public String type() {
            return "root-only";
        }
    }

    /** A nesting factory whose declared bounds are otherwise valid, for a shared arity-bound base. */
    private static class ValidNestingFactory implements NestingFeatureExtractorFactory {
        @Override
        public FeatureExtractor create(FeatureExtractorParameters parameters, List<FeatureExtractor> children) {
            return (Sequence<? extends PositionedToken> sequence, int position) -> Set.of();
        }

        @Override
        public int maximumChildren() {
            return Integer.MAX_VALUE;
        }

        @Override
        public String type() {
            return "valid-nesting";
        }
    }

    /** A nesting factory whose declared minimum children is negative. */
    private static final class NegativeMinimumFactory extends ValidNestingFactory {
        @Override
        public int minimumChildren() {
            return -1;
        }

        @Override
        public String type() {
            return "negative-minimum";
        }
    }

    /** A nesting factory whose declared maximum children is below its minimum. */
    private static final class MaximumBelowMinimumFactory extends ValidNestingFactory {
        @Override
        public int maximumChildren() {
            return 2;
        }

        @Override
        public int minimumChildren() {
            return 3;
        }

        @Override
        public String type() {
            return "maximum-below-minimum";
        }
    }

    /** A nesting factory whose descriptor incorrectly declares that it requires no children. */
    private static final class NestingWithNoChildrenFactory extends ValidNestingFactory {
        @Override
        public int minimumChildren() {
            return 0;
        }

        @Override
        public String type() {
            return "nesting-with-no-children";
        }
    }

    /** A leaf factory declaring two parameters that share a name but differ in kind. */
    private static final class DuplicateParameterFactory implements LeafFeatureExtractorFactory {
        @Override
        public FeatureExtractor create(FeatureExtractorParameters parameters) {
            return (Sequence<? extends PositionedToken> sequence, int position) -> Set.of();
        }

        @Override
        public Set<ParameterDescriptor> parameters() {
            return Set.of(
                    ParameterDescriptor.builder("name", ParameterKind.STRING).build(),
                    ParameterDescriptor.builder("name", ParameterKind.INTEGER).build()
            );
        }

        @Override
        public String type() {
            return "duplicate-parameter";
        }
    }

    /** A factory that implements both leaf and nesting, an ambiguous dispatch. */
    private static final class DualKindFactory implements LeafFeatureExtractorFactory, NestingFeatureExtractorFactory {
        @Override
        public FeatureExtractor create(FeatureExtractorParameters parameters) {
            return (Sequence<? extends PositionedToken> sequence, int position) -> Set.of();
        }

        @Override
        public FeatureExtractor create(FeatureExtractorParameters parameters, List<FeatureExtractor> children) {
            return (Sequence<? extends PositionedToken> sequence, int position) -> Set.of();
        }

        @Override
        public int maximumChildren() {
            return 1;
        }

        @Override
        public int minimumChildren() {
            return 0;
        }

        @Override
        public String type() {
            return "dual-kind";
        }
    }

    @Test
    void of__duplicateTypeThrowsNamingBothClasses() {
        // ARRANGE //
        List<FeatureExtractorFactory> factories = List.of(new SyntheticFactory("length"), new ShadowFactory());

        // ACT //
        DuplicateFactoryTypeException exception = assertThrows(
                DuplicateFactoryTypeException.class,
                () -> FeatureExtractorFactoryRegistry.of(factories)
        );

        // ASSERT //
        assertEquals("length", exception.type());
        assertEquals(
                List.of(ShadowFactory.class.getName(), SyntheticFactory.class.getName()),
                exception.factoryClassNames()
        );
        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(message.contains("length"), "message should name the duplicated type; was: " + message);
    }

    @Test
    void of__indexesByType() {
        // ARRANGE //
        SyntheticFactory window = new SyntheticFactory("window");
        SyntheticFactory length = new SyntheticFactory("length");
        FeatureExtractorFactoryRegistry registry = FeatureExtractorFactoryRegistry.of(List.of(window, length));

        // ACT & ASSERT //
        assertSame(window, registry.find("window").orElseThrow());
        assertSame(length, registry.find("length").orElseThrow());
    }

    record OfInvalidDeclarationParameters(
            String name,
            Executable action,
            Class<? extends Exception> expectedClass,
            String expectedMessageFragment
    ) {}

    static Stream<OfInvalidDeclarationParameters> of__invalidDeclaration() {
        return Stream.of(
                new OfInvalidDeclarationParameters(
                        "root_only",
                        () -> FeatureExtractorFactoryRegistry.of(List.of(new RootOnlyFactory())),
                        InvalidFactoryDeclarationException.class,
                        "must implement LeafFeatureExtractorFactory or NestingFeatureExtractorFactory"
                ),
                new OfInvalidDeclarationParameters(
                        "dual_kind",
                        () -> FeatureExtractorFactoryRegistry.of(List.of(new DualKindFactory())),
                        InvalidFactoryDeclarationException.class,
                        "must implement only one of LeafFeatureExtractorFactory or NestingFeatureExtractorFactory"
                ),
                new OfInvalidDeclarationParameters(
                        "negative_minimum",
                        () -> FeatureExtractorFactoryRegistry.of(List.of(new NegativeMinimumFactory())),
                        InvalidFactoryDeclarationException.class,
                        "declares minimum children that must be non-negative, got: -1"
                ),
                new OfInvalidDeclarationParameters(
                        "maximum_below_minimum",
                        () -> FeatureExtractorFactoryRegistry.of(List.of(new MaximumBelowMinimumFactory())),
                        InvalidFactoryDeclarationException.class,
                        "declares maximum children (2) that must not be less than minimum children (3)"
                ),
                new OfInvalidDeclarationParameters(
                        "nesting_with_no_children",
                        () -> FeatureExtractorFactoryRegistry.of(List.of(new NestingWithNoChildrenFactory())),
                        InvalidFactoryDeclarationException.class,
                        "is a nesting factory but declares a minimum children of 0; a nesting factory must"
                                + " declare at least one child"
                ),
                new OfInvalidDeclarationParameters(
                        "duplicate_parameter",
                        () -> FeatureExtractorFactoryRegistry.of(List.of(new DuplicateParameterFactory())),
                        InvalidFactoryDeclarationException.class,
                        "declares duplicate parameter 'name'"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void of__invalidDeclaration(OfInvalidDeclarationParameters parameters) {
        // ACT //
        Exception exception = assertThrows(parameters.expectedClass(), parameters.action());

        // ASSERT //
        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(
                message.contains(parameters.expectedMessageFragment()),
                "message should explain the inconsistency; was: " + message
        );
    }

    @Test
    void of__invalidDeclarationExposesTypeAndFactoryClassName() {
        // ARRANGE //
        RootOnlyFactory factory = new RootOnlyFactory();

        // ACT //
        InvalidFactoryDeclarationException exception = assertThrows(
                InvalidFactoryDeclarationException.class,
                () -> FeatureExtractorFactoryRegistry.of(List.of(factory))
        );

        // ASSERT //
        assertEquals("root-only", exception.type());
        assertEquals(RootOnlyFactory.class.getName(), exception.factoryClassName());
    }

    @Test
    void find__unknownTypeIsEmpty() {
        // ARRANGE //
        FeatureExtractorFactoryRegistry registry = FeatureExtractorFactoryRegistry
                .of(List.of(new SyntheticFactory("window")));

        // ACT & ASSERT //
        assertTrue(registry.find("missing").isEmpty());
    }
}
