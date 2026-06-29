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
package org.coordinatekit.crf.core.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.preprocessing.DefaultFeatureFormat;
import org.coordinatekit.crf.core.preprocessing.FeatureExtractor;
import org.coordinatekit.crf.core.preprocessing.FeatureFormat;
import org.coordinatekit.crf.core.preprocessing.Tokenizer;
import org.coordinatekit.crf.core.preprocessing.WhitespaceTokenizer;
import org.coordinatekit.crf.core.tag.CrfTagger;
import org.coordinatekit.crf.core.tag.CrfTaggerLoader;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Tests {@link CrfServices}: the canonical defaults (the {@link WhitespaceTokenizer} tokenizer
 * default and the absent full and key feature extractors, tagger loader, and tag provider) and that
 * an explicit override wins. The discovery path is covered by the command tests.
 */
class CrfServicesTest {
    record EmptyParameters(String name, Supplier<Optional<?>> accessor) {}

    record ExplicitWinsParameters(
            String name,
            Function<FeatureExtractor<String>, Optional<FeatureExtractor<String>>> resolver
    ) {}

    record SelectExceptionParameters(String name, Executable action, Class<? extends Exception> expectedClass) {}

    record SelectParameters(
            String name,
            List<CrfTaggerLoader> discovered,
            @Nullable String requestedName,
            @Nullable CrfTaggerLoader expected
    ) {}

    static Stream<ExplicitWinsParameters> explicitWins() {
        return Stream.of(
                new ExplicitWinsParameters("fullFeatureExtractor", CrfServices::fullFeatureExtractor),
                new ExplicitWinsParameters("keyFeatureExtractor", CrfServices::keyFeatureExtractor)
        );
    }

    @MethodSource
    @ParameterizedTest
    void explicitWins(ExplicitWinsParameters parameters) {
        // ARRANGE //
        FeatureExtractor<String> featureExtractor = (sequence, position) -> Set.of();

        // ACT //
        Optional<FeatureExtractor<String>> result = parameters.resolver().apply(featureExtractor);

        // ASSERT //
        assertSame(featureExtractor, result.orElseThrow(), parameters.name() + " explicit should win");
    }

    @Test
    void featureFormat__defaultsToDefaultFeatureFormat() {
        // ACT & ASSERT //
        assertInstanceOf(DefaultFeatureFormat.class, CrfServices.featureFormat(null));
    }

    @Test
    void featureFormat__explicitWins() {
        // ARRANGE //
        FeatureFormat explicit = new DefaultFeatureFormat();

        // ACT //
        FeatureFormat result = CrfServices.featureFormat(explicit);

        // ASSERT //
        assertSame(explicit, result, "an explicit feature format should win");
    }

    static Stream<EmptyParameters> isEmptyWhenNothingRegistered() {
        return Stream.of(
                new EmptyParameters("fullFeatureExtractor", CrfServices::fullFeatureExtractor),
                new EmptyParameters("keyFeatureExtractor", CrfServices::keyFeatureExtractor),
                new EmptyParameters("tagProvider", CrfServices::tagProvider),
                new EmptyParameters("taggerLoader", CrfServices::taggerLoader)
        );
    }

    @MethodSource
    @ParameterizedTest
    void isEmptyWhenNothingRegistered(EmptyParameters parameters) {
        // ACT & ASSERT //
        assertTrue(
                parameters.accessor().get().isEmpty(),
                parameters.name() + " should be empty when nothing is registered"
        );
    }

    private static CrfTaggerLoader namedLoader(String name) {
        return new CrfTaggerLoader() {
            @Override
            public <F, T extends Comparable<T>> CrfTagger<F, T> load(
                    Path modelPath,
                    FeatureExtractor<F> featureExtractor,
                    TagProvider<T> tagProvider,
                    Tokenizer tokenizer
            ) {
                throw new UnsupportedOperationException("synthetic loader does not load");
            }

            @Override
            public String name() {
                return name;
            }
        };
    }

    static Stream<SelectParameters> selectTaggerLoader() {
        CrfTaggerLoader mallet = namedLoader("mallet");
        CrfTaggerLoader other = namedLoader("other");
        return Stream.of(
                new SelectParameters("name_selects_match", List.of(mallet, other), "other", other),
                new SelectParameters("null_name_single", List.of(mallet), null, mallet),
                new SelectParameters("null_name_none", List.of(), null, null)
        );
    }

    @MethodSource
    @ParameterizedTest
    void selectTaggerLoader(SelectParameters parameters) {
        // ACT //
        Optional<CrfTaggerLoader> selected = CrfServices
                .selectTaggerLoader(parameters.discovered(), parameters.requestedName());

        // ASSERT //
        assertSame(parameters.expected(), selected.orElse(null), parameters.name());
    }

    static Stream<SelectExceptionParameters> selectTaggerLoader__exception() {
        return Stream.of(
                new SelectExceptionParameters(
                        "duplicate_name",
                        () -> CrfServices
                                .selectTaggerLoader(List.of(namedLoader("mallet"), namedLoader("mallet")), "mallet"),
                        AmbiguousServiceException.class
                ),
                new SelectExceptionParameters(
                        "null_name_multiple",
                        () -> CrfServices
                                .selectTaggerLoader(List.of(namedLoader("mallet"), namedLoader("other")), null),
                        AmbiguousServiceException.class
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void selectTaggerLoader__exception(SelectExceptionParameters parameters) {
        // ACT & ASSERT //
        assertThrows(parameters.expectedClass(), parameters.action());
    }

    @Test
    void selectTaggerLoader__unknownNameThrowsWithAvailableNames() {
        // ARRANGE //
        // unsorted input so the assertion fails if UnknownServiceException stops sorting.
        List<CrfTaggerLoader> discovered = List.of(namedLoader("other"), namedLoader("mallet"));

        // ACT //
        UnknownServiceException exception = assertThrows(
                UnknownServiceException.class,
                () -> CrfServices.selectTaggerLoader(discovered, "missing")
        );

        // ASSERT //
        assertEquals("missing", exception.requestedName());
        assertEquals(List.of("mallet", "other"), exception.availableNames(), "available names should be sorted");
    }

    @Test
    void selectTaggerLoader__unknownNameWithNoLoadersReportsNone() {
        // ACT //
        UnknownServiceException exception = assertThrows(
                UnknownServiceException.class,
                () -> CrfServices.selectTaggerLoader(List.of(), "x")
        );

        // ASSERT //
        assertTrue(exception.availableNames().isEmpty());
        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(message.contains("(none)"), "message should report (none); was: " + message);
    }

    @Test
    void taggerLoader__explicitWinsOverName() {
        // ARRANGE //
        CrfTaggerLoader explicit = namedLoader("explicit");

        // ACT //
        Optional<CrfTaggerLoader> selected = CrfServices.taggerLoader(explicit, "ignored");

        // ASSERT //
        assertSame(explicit, selected.orElseThrow(), "an explicit loader wins over name selection");
    }

    @Test
    void tokenizer__defaultsToWhitespace() {
        // ACT & ASSERT //
        assertInstanceOf(WhitespaceTokenizer.class, CrfServices.tokenizer(null));
    }
}
