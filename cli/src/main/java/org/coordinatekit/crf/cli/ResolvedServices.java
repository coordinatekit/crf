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
package org.coordinatekit.crf.cli;

import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.UncheckedCrfException;
import org.coordinatekit.crf.core.preprocessing.FeatureExtractor;
import org.coordinatekit.crf.core.preprocessing.Tokenizer;
import org.coordinatekit.crf.core.preprocessing.WhitespaceTokenizer;
import org.coordinatekit.crf.core.spi.AmbiguousServiceException;
import org.coordinatekit.crf.core.spi.CrfServices;
import org.coordinatekit.crf.core.tag.CrfTagger;
import org.coordinatekit.crf.core.tag.CrfTaggerLoader;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

/**
 * The resolved set of services a command needs: a tag provider, a tokenizer, an optional full and
 * key feature extractor, and an optional model loader.
 *
 * <p>
 * Each slot is resolved independently by {@link Builder#resolve()} with the precedence
 * {@code explicit > single service-provider implementation > built-in default}. The full feature
 * extractor has no built-in default: when none is supplied or discovered it resolves to
 * {@code null}, and callers can warn before tagging without features. The key feature extractor
 * falls back to the full extractor when neither is supplied nor discovered. The set is internally
 * untyped — the feature type and tag type are erased to wildcards — because the slots come from
 * independent sources that cannot prove their agreement at compile time. The companion
 * {@link ResolvedServicesFactory} assembles them into a typed annotator or reviewer with the
 * unchecked casts that gap requires. The resolved full {@link FeatureExtractor} and
 * {@link TagProvider} are threaded into both the tagger (via {@link #loadTagger}) and the runner's
 * verbose view, so their types are coherent by construction; the key extractor feeds the runner's
 * key view only. {@link CrfTaggerLoader#load} is generic in the feature and tag types, so the
 * tagger it returns is bound to the services it is handed rather than to types of its own (see
 * {@link CrfTaggerLoader}).
 *
 * @see Builder
 * @see ResolvedServicesFactory
 */
@NullMarked
final class ResolvedServices {
    private final @Nullable FeatureExtractor<?> fullFeatureExtractor;
    private final @Nullable FeatureExtractor<?> keyFeatureExtractor;
    private final TagProvider<?> tagProvider;
    private final @Nullable CrfTaggerLoader taggerLoader;
    private final Tokenizer tokenizer;

    private ResolvedServices(
            TagProvider<?> tagProvider,
            Tokenizer tokenizer,
            @Nullable FeatureExtractor<?> fullFeatureExtractor,
            @Nullable FeatureExtractor<?> keyFeatureExtractor,
            @Nullable CrfTaggerLoader taggerLoader
    ) {
        this.fullFeatureExtractor = fullFeatureExtractor;
        this.keyFeatureExtractor = keyFeatureExtractor;
        this.tagProvider = tagProvider;
        this.taggerLoader = taggerLoader;
        this.tokenizer = tokenizer;
    }

    /**
     * Re-words an {@link AmbiguousServiceException} into a {@link CrfStartupException} with
     * launcher-facing guidance, preserving the original as the cause.
     *
     * @param exception the ambiguity raised while resolving a slot
     * @return a startup exception naming the conflicting implementations and how to resolve them
     */
    static CrfStartupException ambiguityStartupException(AmbiguousServiceException exception) {
        return new CrfStartupException(
                "multiple " + exception.serviceName() + " service implementations found on the classpath: "
                        + String.join(", ", exception.implementationNames())
                        + "; leave exactly one on the classpath or provide one explicitly",
                exception
        );
    }

    /**
     * Returns a new builder for assembling and resolving a resolved set of services.
     *
     * @return a new builder with no explicit overrides
     */
    static Builder builder() {
        return new Builder();
    }

    private static <F> FeatureExtractor<F> emptyFeatureExtractor() {
        return (sequence, position) -> Set.of();
    }

    /**
     * Returns the resolved full feature extractor, or {@code null} when none was supplied or
     * discovered.
     *
     * <p>
     * The full extractor is the model's extractor: it feeds the tagger (via {@link #loadTagger}) and
     * the runner's verbose view. There is no built-in default; a {@code null} extractor means the
     * tagger runs without features and callers should warn the user.
     *
     * @return the full feature extractor, or {@code null}
     */
    @Nullable
    FeatureExtractor<?> fullFeatureExtractor() {
        return fullFeatureExtractor;
    }

    /**
     * Returns the resolved key feature extractor, falling back to the full extractor when no key
     * extractor was supplied or discovered.
     *
     * <p>
     * The key extractor feeds the runner's key view. It is {@code null} only when neither a key nor a
     * full extractor is available.
     *
     * @return the key feature extractor, or {@code null}
     */
    @Nullable
    FeatureExtractor<?> keyFeatureExtractor() {
        return keyFeatureExtractor;
    }

    /**
     * Loads the tagger for {@code modelPath}, or returns {@code null} when no model was supplied.
     *
     * <p>
     * The tagger is built over the full feature extractor — the model's extractor — so its feature type
     * matches the verbose view's.
     *
     * @param modelPath the serialized model to load, or {@code null} to run without tag suggestions
     * @return the loaded tagger, or {@code null} if {@code modelPath} is {@code null}
     * @throws CrfStartupException if a model was supplied but no {@link CrfTaggerLoader} is available,
     *         or the model cannot be read or is not a valid model
     */
    @Nullable
    CrfTagger<?, ?> loadTagger(@Nullable Path modelPath) {
        if (modelPath == null) {
            return null;
        }
        if (taggerLoader == null) {
            throw new CrfStartupException(
                    "a model was supplied (--model " + modelPath
                            + ") but no TaggerLoader service is available; add the mallet module"
                            + " (or another TaggerLoader provider) to the classpath"
            );
        }
        FeatureExtractor<?> resolvedFeatureExtractor = fullFeatureExtractor != null ? fullFeatureExtractor
                : emptyFeatureExtractor();
        try {
            return taggerLoader.load(modelPath, resolvedFeatureExtractor, tagProvider, tokenizer);
        } catch (IOException | UncheckedCrfException | ClassCastException exception) {
            throw new CrfStartupException(
                    "failed to load the model from " + modelPath + ": " + exception.getMessage(),
                    exception
            );
        }
    }

    /**
     * Returns the resolved tag provider, which defines the label space.
     *
     * @return the tag provider
     */
    TagProvider<?> tagProvider() {
        return tagProvider;
    }

    /**
     * Returns the resolved model loader, or {@code null} if none was supplied or discovered.
     *
     * @return the tagger loader, or {@code null}
     */
    @Nullable
    CrfTaggerLoader taggerLoader() {
        return taggerLoader;
    }

    /**
     * Returns the resolved tokenizer.
     *
     * @return the tokenizer
     */
    Tokenizer tokenizer() {
        return tokenizer;
    }

    /**
     * Assembles a resolved set of services, applying the discovery chain and built-in defaults at
     * {@link #resolve()}.
     *
     * <p>
     * Explicit setters override discovery and are intended for tests and embedding callers; the
     * {@link CrfLauncher} leaves them unset so every slot is resolved from the classpath.
     */
    static final class Builder {
        private @Nullable FeatureExtractor<?> fullFeatureExtractor;
        private @Nullable FeatureExtractor<?> keyFeatureExtractor;
        private @Nullable TagProvider<?> tagProvider;
        private @Nullable CrfTaggerLoader taggerLoader;
        private @Nullable Tokenizer tokenizer;

        private Builder() {}

        /**
         * Sets an explicit full feature extractor, overriding service discovery.
         *
         * @param fullFeatureExtractor the full feature extractor
         * @return this builder
         */
        Builder fullFeatureExtractor(FeatureExtractor<?> fullFeatureExtractor) {
            this.fullFeatureExtractor = fullFeatureExtractor;
            return this;
        }

        /**
         * Sets an explicit key feature extractor, overriding service discovery.
         *
         * @param keyFeatureExtractor the key feature extractor
         * @return this builder
         */
        Builder keyFeatureExtractor(FeatureExtractor<?> keyFeatureExtractor) {
            this.keyFeatureExtractor = keyFeatureExtractor;
            return this;
        }

        /**
         * Resolves every slot and returns an immutable resolved set of services.
         *
         * <p>
         * The tokenizer defaults to {@link WhitespaceTokenizer}; the full feature extractor defaults to
         * absent (the tagger then runs without features); the key feature extractor defaults to the full
         * extractor; the tagger loader defaults to absent; the tag provider has no built-in default
         * (tag-set inference is not yet implemented) and must be supplied explicitly or by a single
         * registered service.
         *
         * @return the resolved set of services
         * @throws CrfStartupException if no tag provider can be resolved, or any slot has more than one
         *         registered implementation
         */
        ResolvedServices resolve() {
            Tokenizer resolvedTokenizer;
            FeatureExtractor<?> resolvedFullFeatureExtractor;
            FeatureExtractor<?> resolvedKeyFeatureExtractor;
            CrfTaggerLoader resolvedTaggerLoader;
            TagProvider<?> resolvedTagProvider;
            try {
                resolvedTokenizer = CrfServices.tokenizer(tokenizer);
                resolvedFullFeatureExtractor = CrfServices.fullFeatureExtractor(fullFeatureExtractor).orElse(null);
                // key view falls back to the full extractor; orElse cannot bridge the two wildcard captures
                resolvedKeyFeatureExtractor = CrfServices.keyFeatureExtractor(keyFeatureExtractor).orElse(null);
                if (resolvedKeyFeatureExtractor == null) {
                    resolvedKeyFeatureExtractor = resolvedFullFeatureExtractor;
                }
                resolvedTaggerLoader = CrfServices.taggerLoader(taggerLoader).orElse(null);
                resolvedTagProvider = CrfServices.tagProvider(tagProvider).orElseThrow(
                        () -> new CrfStartupException(
                                "no TagProvider is available: register a TagProvider service"
                                        + " (a META-INF/services/org.coordinatekit.crf.core.TagProvider entry)"
                                        + " so the launcher can build the label space"
                        )
                );
            } catch (AmbiguousServiceException exception) {
                throw ambiguityStartupException(exception);
            }
            return new ResolvedServices(
                    resolvedTagProvider,
                    resolvedTokenizer,
                    resolvedFullFeatureExtractor,
                    resolvedKeyFeatureExtractor,
                    resolvedTaggerLoader
            );
        }

        /**
         * Sets an explicit tag provider, overriding service discovery.
         *
         * @param tagProvider the tag provider
         * @return this builder
         */
        Builder tagProvider(TagProvider<?> tagProvider) {
            this.tagProvider = tagProvider;
            return this;
        }

        /**
         * Sets an explicit model loader, overriding service discovery.
         *
         * @param taggerLoader the tagger loader
         * @return this builder
         */
        Builder taggerLoader(CrfTaggerLoader taggerLoader) {
            this.taggerLoader = taggerLoader;
            return this;
        }

        /**
         * Sets an explicit tokenizer, overriding service discovery and the built-in default.
         *
         * @param tokenizer the tokenizer
         * @return this builder
         */
        Builder tokenizer(Tokenizer tokenizer) {
            this.tokenizer = tokenizer;
            return this;
        }
    }
}
