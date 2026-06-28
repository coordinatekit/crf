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

import static java.util.Objects.requireNonNull;

import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.preprocessing.FeatureExtractor;
import org.coordinatekit.crf.core.preprocessing.FullFeatureExtractor;
import org.coordinatekit.crf.core.preprocessing.KeyFeatureExtractor;
import org.coordinatekit.crf.core.preprocessing.Tokenizer;
import org.coordinatekit.crf.core.preprocessing.WhitespaceTokenizer;
import org.coordinatekit.crf.core.tag.CrfTaggerLoader;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Discovers the CRF domain SPIs, applying each slot's canonical default.
 *
 * <p>
 * Each slot resolves by the precedence {@code explicit > single registered provider > built-in
 * default}. This is where {@code core} takes ownership of the canonical defaults — most notably
 * that {@link WhitespaceTokenizer} is the default tokenizer. Requiredness is left to the caller: an
 * empty {@link Optional} means "nothing registered and no default," which an entry point may treat
 * as acceptable or as a fatal misconfiguration depending on the slot.
 *
 * <p>
 * Each slot offers a no-arg discover-only overload for callers with no override, and an overload
 * taking an explicit provider that wins when present.
 */
@NullMarked
public final class CrfServices {
    private CrfServices() {
        throw new UnsupportedOperationException("CrfServices is a utility class");
    }

    /**
     * Discovers the full feature extractor by {@code single registered FullFeatureExtractor > none}.
     *
     * <p>
     * The full extractor is the one used for training and tagging — the complete feature set the model
     * sees — and it also feeds the annotator's verbose ("all features") view.
     *
     * @param <F> the feature type
     * @return the resolved full feature extractor, or empty if none is registered
     * @throws AmbiguousServiceException if more than one full feature extractor is registered
     */
    public static <F> Optional<FeatureExtractor<F>> fullFeatureExtractor() {
        return fullFeatureExtractor(null);
    }

    /**
     * Resolves the full feature extractor by
     * {@code explicit > single registered FullFeatureExtractor > none}.
     *
     * @param explicit the explicitly supplied full feature extractor, or {@code null} if none was set
     * @param <F> the feature type
     * @return the resolved full feature extractor, or empty if none was supplied or registered
     * @throws AmbiguousServiceException if more than one full feature extractor is registered and none
     *         is explicit
     */
    public static <F> Optional<FeatureExtractor<F>> fullFeatureExtractor(@Nullable FeatureExtractor<F> explicit) {
        return resolveExtractor(FullFeatureExtractor.class, explicit);
    }

    /**
     * Discovers the key feature extractor by {@code single registered KeyFeatureExtractor > none}.
     *
     * <p>
     * The key extractor is the simpler pre-window extractor that feeds the annotator's key ("key
     * features") view. When none is registered, callers fall the key view back to the full extractor.
     *
     * @param <F> the feature type
     * @return the resolved key feature extractor, or empty if none is registered
     * @throws AmbiguousServiceException if more than one key feature extractor is registered
     */
    public static <F> Optional<FeatureExtractor<F>> keyFeatureExtractor() {
        return keyFeatureExtractor(null);
    }

    /**
     * Resolves the key feature extractor by
     * {@code explicit > single registered KeyFeatureExtractor > none}.
     *
     * @param explicit the explicitly supplied key feature extractor, or {@code null} if none was set
     * @param <F> the feature type
     * @return the resolved key feature extractor, or empty if none was supplied or registered
     * @throws AmbiguousServiceException if more than one key feature extractor is registered and none
     *         is explicit
     */
    public static <F> Optional<FeatureExtractor<F>> keyFeatureExtractor(@Nullable FeatureExtractor<F> explicit) {
        return resolveExtractor(KeyFeatureExtractor.class, explicit);
    }

    /**
     * Resolves a marker-typed feature extractor slot by
     * {@code explicit > single registered provider > none}, shared by the full and key overloads.
     *
     * @param serviceType the marker subinterface to discover ({@code FullFeatureExtractor} or
     *        {@code KeyFeatureExtractor}); also identifies the slot in
     *        {@link AmbiguousServiceException}
     * @param explicit the explicitly supplied extractor, or {@code null} if none was set
     * @param <F> the feature type
     * @return the resolved extractor, or empty if none was supplied or registered
     * @throws AmbiguousServiceException if more than one provider is registered and none is explicit
     */
    // ServiceLoader erases the type; F is bound from explicit or assumed of the discovered provider
    @SuppressWarnings("unchecked")
    private static <F> Optional<FeatureExtractor<F>> resolveExtractor(
            Class<?> serviceType,
            @Nullable FeatureExtractor<F> explicit
    ) {
        List<FeatureExtractor<F>> discovered = (List<FeatureExtractor<F>>) ServiceResolution.discover(serviceType);
        return Optional.ofNullable(ServiceResolution.resolve(serviceType, explicit, discovered, null));
    }

    /**
     * Selects a tagger loader from {@code discovered} by name, the discovery-only core of
     * {@link #taggerLoader(CrfTaggerLoader, String)}; kept separate for unit testing with synthetic
     * loaders.
     *
     * @param discovered the loaders discovered through {@link java.util.ServiceLoader}
     * @param name the requested loader name, or {@code null} to resolve without name selection
     * @return the selected loader, or empty if none was discovered and no name was requested
     * @throws AmbiguousServiceException if {@code name} is {@code null} and more than one loader is
     *         discovered, or if more than one discovered loader carries the requested {@code name}
     * @throws UnknownServiceException if {@code name} is non-null and no discovered loader carries it
     */
    static Optional<CrfTaggerLoader> selectTaggerLoader(List<CrfTaggerLoader> discovered, @Nullable String name) {
        if (name == null) {
            return Optional.ofNullable(ServiceResolution.resolve(CrfTaggerLoader.class, null, discovered, null));
        }
        List<CrfTaggerLoader> matches = discovered.stream().filter(loader -> loader.name().equals(name)).toList();
        if (matches.size() > 1) {
            throw new AmbiguousServiceException(CrfTaggerLoader.class, matches);
        }
        if (matches.isEmpty()) {
            throw new UnknownServiceException(
                    "CrfTaggerLoader",
                    name,
                    discovered.stream().map(CrfTaggerLoader::name).toList()
            );
        }
        return Optional.of(matches.getFirst());
    }

    /**
     * Discovers the tag provider by {@code single registered TagProvider > none}.
     *
     * @param <T> the tag type
     * @return the resolved tag provider, or empty if none is registered
     * @throws AmbiguousServiceException if more than one tag provider is registered
     */
    public static <T extends Comparable<T>> Optional<TagProvider<T>> tagProvider() {
        return tagProvider(null);
    }

    /**
     * Resolves the tag provider by {@code explicit > single registered TagProvider > none}.
     *
     * <p>
     * Returns empty when nothing is registered; requiredness is the caller's decision.
     *
     * @param explicit the explicitly supplied tag provider, or {@code null} if none was set
     * @param <T> the tag type
     * @return the resolved tag provider, or empty if none was supplied or registered
     * @throws AmbiguousServiceException if more than one tag provider is registered and none is
     *         explicit
     */
    // ServiceLoader erases the type; T is bound from explicit or assumed of the discovered provider
    @SuppressWarnings("unchecked")
    public static <T extends Comparable<T>> Optional<TagProvider<T>> tagProvider(@Nullable TagProvider<T> explicit) {
        List<TagProvider<T>> discovered = (List<TagProvider<T>>) (List<?>) ServiceResolution
                .discover(TagProvider.class);
        return Optional.ofNullable(ServiceResolution.resolve(TagProvider.class, explicit, discovered, null));
    }

    /**
     * Discovers the tagger loader by {@code single registered CrfTaggerLoader > none}.
     *
     * @return the resolved tagger loader, or empty if none is registered
     * @throws AmbiguousServiceException if more than one tagger loader is registered
     */
    public static Optional<CrfTaggerLoader> taggerLoader() {
        return taggerLoader(null);
    }

    /**
     * Resolves the tagger loader by {@code explicit > single registered CrfTaggerLoader > none}.
     *
     * @param explicit the explicitly supplied tagger loader, or {@code null} if none was set
     * @return the resolved tagger loader, or empty if none was supplied or registered
     * @throws AmbiguousServiceException if more than one tagger loader is registered and none is
     *         explicit
     */
    public static Optional<CrfTaggerLoader> taggerLoader(@Nullable CrfTaggerLoader explicit) {
        return taggerLoader(explicit, null);
    }

    /**
     * Resolves the tagger loader by
     * {@code explicit > name-matched registered CrfTaggerLoader > single registered CrfTaggerLoader >
     * none}.
     *
     * <p>
     * When {@code name} is {@code null} this matches {@link #taggerLoader(CrfTaggerLoader)}: a single
     * registered loader wins and more than one is ambiguous. When {@code name} is non-null, selection
     * is strict — the loader whose {@link CrfTaggerLoader#name()} equals {@code name} is chosen, and a
     * name matching nothing fails rather than falling back to a lone registered loader, so a script
     * that names a loader never silently runs a different one.
     *
     * @param explicit the explicitly supplied tagger loader, or {@code null} if none was set
     * @param name the requested loader name, or {@code null} to resolve without name selection
     * @return the resolved tagger loader, or empty if none was supplied or registered
     * @throws AmbiguousServiceException if {@code name} is {@code null} and more than one loader is
     *         registered, or if more than one registered loader carries the requested {@code name}
     * @throws UnknownServiceException if {@code name} is non-null and no registered loader carries it
     */
    public static Optional<CrfTaggerLoader> taggerLoader(@Nullable CrfTaggerLoader explicit, @Nullable String name) {
        if (explicit != null) {
            return Optional.of(explicit);
        }
        return selectTaggerLoader(ServiceResolution.discover(CrfTaggerLoader.class), name);
    }

    /**
     * Resolves the tokenizer by {@code single registered Tokenizer > WhitespaceTokenizer}.
     *
     * @return the resolved tokenizer, never {@code null}
     * @throws AmbiguousServiceException if more than one tokenizer is registered
     */
    public static Tokenizer tokenizer() {
        return tokenizer(null);
    }

    /**
     * Resolves the tokenizer by {@code explicit > single registered Tokenizer > WhitespaceTokenizer}.
     *
     * @param explicit the explicitly supplied tokenizer, or {@code null} if none was set
     * @return the resolved tokenizer, never {@code null}
     * @throws AmbiguousServiceException if more than one tokenizer is registered and none is explicit
     */
    public static Tokenizer tokenizer(@Nullable Tokenizer explicit) {
        return requireNonNull(ServiceResolution.resolve(Tokenizer.class, explicit, new WhitespaceTokenizer()));
    }
}
