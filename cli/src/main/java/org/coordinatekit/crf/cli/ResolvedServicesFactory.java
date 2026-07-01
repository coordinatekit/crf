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

import org.coordinatekit.crf.annotator.Annotator;
import org.coordinatekit.crf.annotator.AnnotatorRunner;
import org.coordinatekit.crf.annotator.RetokenizeReviewer;
import org.coordinatekit.crf.annotator.RetokenizeRunner;
import org.coordinatekit.crf.annotator.TaggingInterface;
import org.coordinatekit.crf.annotator.terminal.TerminalTaggingInterface;
import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.tag.CrfTagger;
import org.jline.terminal.Terminal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/**
 * Builds the runner factories that turn a resolved {@link ResolvedServices} set into an
 * {@link Annotator} or {@link RetokenizeReviewer}.
 *
 * <p>
 * Resolution and model loading happen eagerly when a factory is requested, so a missing tag
 * provider or an unreadable model fails with a {@link CrfStartupException} before the runner opens
 * a terminal. The returned factory only assembles the terminal-bound objects when the runner
 * invokes it.
 *
 * <p>
 * The resolved services are untyped (see {@link ResolvedServices}); assembly therefore uses raw
 * builders and unchecked casts. There is no separate coherence check because none is needed: the
 * resolved full feature extractor and tag provider are shared into both the tagger and the
 * annotator/reviewer's verbose view, so their feature and tag types agree by construction. The key
 * feature extractor feeds the key view only, where its feature type is display-only — a mismatch
 * could at worst mis-render features, never throw. The only residual risk is a non-conformant
 * {@link org.coordinatekit.crf.core.tag.CrfTaggerLoader} that ignores those arguments instead of
 * building the tagger over them.
 */
@NullMarked
final class ResolvedServicesFactory {
    private static final String MISSING_FEATURE_EXTRACTOR_WARNING = "Warning: no FullFeatureExtractor is registered; the model will tag without features. Register a"
            + " FullFeatureExtractor for real features — a loaded model's suggestions are only"
            + " meaningful if it was trained with the same extractor.";

    private ResolvedServicesFactory() {
        throw new UnsupportedOperationException(
                "ResolvedServicesFactory is a utility class and cannot be instantiated"
        );
    }

    // The production path: builds the terminal-bound tagging interface, then delegates the extractor
    // routing to the package-private seam below.
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Annotator<?> annotator(
            ResolvedServices resolvedServices,
            @Nullable CrfTagger<?> tagger,
            Terminal terminal,
            double threshold
    ) {
        warnIfMissingFeatureExtractor(resolvedServices, tagger, terminal);
        TagProvider tagProvider = resolvedServices.tagProvider();
        TerminalTaggingInterface taggingInterface = TerminalTaggingInterface.builder().tagProvider(tagProvider)
                .terminal(terminal).threshold(threshold).featureFormat(resolvedServices.featureFormat()).build();
        return annotator(resolvedServices, tagger, terminal, taggingInterface);
    }

    /**
     * Assembles an annotator over a supplied tagging interface, routing the key feature extractor to
     * the key view and the full feature extractor to the verbose view.
     *
     * <p>
     * This is the wiring seam exercised by tests: the production overload builds a
     * {@link TerminalTaggingInterface} and delegates here, while a test can supply a capturing
     * interface to observe which extractor reaches which view.
     */
    // The services are discovered independently, so their feature and tag types cannot be proven to
    // agree at compile time; the builders are driven raw and the result is returned as Annotator<?>.
    @SuppressWarnings({"rawtypes", "unchecked"})
    static Annotator<?> annotator(
            ResolvedServices resolvedServices,
            @Nullable CrfTagger<?> tagger,
            Terminal terminal,
            TaggingInterface taggingInterface
    ) {
        TagProvider tagProvider = resolvedServices.tagProvider();
        return Annotator.builder().tagProvider(tagProvider).taggingInterface(taggingInterface).terminal(terminal)
                .tokenizer(resolvedServices.tokenizer()).featureExtractor(resolvedServices.keyFeatureExtractor())
                .verboseFeatureExtractor(resolvedServices.fullFeatureExtractor()).tagger(tagger).build();
    }

    /**
     * Resolves the services, loads the optional model, and returns a factory that assembles an
     * annotator on the runner's terminal.
     *
     * @param servicesBuilder the builder whose {@link ResolvedServices.Builder#resolve() resolve}
     *        supplies the services
     * @param modelPath the model to load, or {@code null} to run without tag suggestions
     * @return a factory for {@link AnnotatorRunner}
     * @throws CrfStartupException if the services cannot be resolved or the model cannot be loaded
     */
    static AnnotatorRunner.AnnotatorFactory annotatorFactory(
            ResolvedServices.Builder servicesBuilder,
            @Nullable Path modelPath
    ) {
        ResolvedServices resolvedServices = servicesBuilder.resolve();
        CrfTagger<?> tagger = resolvedServices.loadTagger(modelPath);
        return (configuration, terminal) -> annotator(resolvedServices, tagger, terminal, configuration.threshold());
    }

    // See annotator(...): the production path builds the tagging interface, then delegates the
    // extractor routing to the package-private seam below.
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static RetokenizeReviewer<?> reviewer(
            ResolvedServices resolvedServices,
            @Nullable CrfTagger<?> tagger,
            Terminal terminal,
            double threshold
    ) {
        warnIfMissingFeatureExtractor(resolvedServices, tagger, terminal);
        TagProvider tagProvider = resolvedServices.tagProvider();
        TerminalTaggingInterface taggingInterface = TerminalTaggingInterface.builder().tagProvider(tagProvider)
                .terminal(terminal).threshold(threshold).featureFormat(resolvedServices.featureFormat()).build();
        return reviewer(resolvedServices, tagger, terminal, taggingInterface);
    }

    /**
     * Assembles a reviewer over a supplied tagging interface, routing the key feature extractor to the
     * key view and the full feature extractor to the verbose view.
     *
     * <p>
     * The reviewer counterpart to
     * {@link #annotator(ResolvedServices, CrfTagger, Terminal, TaggingInterface)}; the two seams stay
     * symmetric so a wiring swap is caught on either path.
     */
    // See annotator(...): the same erasure gap applies to the reviewer's services.
    @SuppressWarnings({"rawtypes", "unchecked"})
    static RetokenizeReviewer<?> reviewer(
            ResolvedServices resolvedServices,
            @Nullable CrfTagger<?> tagger,
            Terminal terminal,
            TaggingInterface taggingInterface
    ) {
        TagProvider tagProvider = resolvedServices.tagProvider();
        return RetokenizeReviewer.builder().tagProvider(tagProvider).taggingInterface(taggingInterface)
                .terminal(terminal).tokenizer(resolvedServices.tokenizer())
                .featureExtractor(resolvedServices.keyFeatureExtractor())
                .verboseFeatureExtractor(resolvedServices.fullFeatureExtractor()).tagger(tagger).build();
    }

    /**
     * Resolves the services, loads the optional model, and returns a factory that assembles a reviewer
     * on the runner's terminal.
     *
     * @param servicesBuilder the builder whose {@link ResolvedServices.Builder#resolve() resolve}
     *        supplies the services
     * @param modelPath the model to load, or {@code null} to re-tag without tag suggestions
     * @return a factory for {@link RetokenizeRunner}
     * @throws CrfStartupException if the services cannot be resolved or the model cannot be loaded
     */
    static RetokenizeRunner.ReviewerFactory reviewerFactory(
            ResolvedServices.Builder servicesBuilder,
            @Nullable Path modelPath
    ) {
        ResolvedServices resolvedServices = servicesBuilder.resolve();
        CrfTagger<?> tagger = resolvedServices.loadTagger(modelPath);
        return (configuration, terminal) -> reviewer(resolvedServices, tagger, terminal, configuration.threshold());
    }

    /**
     * Writes a warning to {@code terminal} when a model was loaded but {@code resolvedServices}
     * resolved without a full feature extractor, and flushes it.
     *
     * <p>
     * The warning is model-centric — a missing extractor only undermines a loaded model's suggestions —
     * so it stays silent when no model was loaded ({@code tagger == null}). It keys on the full
     * extractor because that is the one the model is built over.
     *
     * @param resolvedServices the resolved set of services
     * @param tagger the loaded tagger, or {@code null} when no model was supplied
     * @param terminal the terminal to warn on
     */
    private static void warnIfMissingFeatureExtractor(
            ResolvedServices resolvedServices,
            @Nullable CrfTagger<?> tagger,
            Terminal terminal
    ) {
        if (tagger != null && resolvedServices.fullFeatureExtractor() == null) {
            terminal.writer().write(MISSING_FEATURE_EXTRACTOR_WARNING + System.lineSeparator());
            terminal.writer().flush();
        }
    }
}
