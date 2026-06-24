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
package org.coordinatekit.crf.mallet.tag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.coordinatekit.crf.core.preprocessing.WhitespaceTokenizer;
import org.coordinatekit.crf.core.tag.CrfTagger;
import org.coordinatekit.crf.core.tag.CrfTaggerLoader;
import org.coordinatekit.crf.core.tag.TaggedTokenization;
import org.coordinatekit.crf.mallet.model.PartsOfSpeechModel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Tests {@link MalletCrfTaggerLoader}: its registration as the sole {@link CrfTaggerLoader}
 * service, that {@link MalletCrfTaggerLoader#load} returns a working tagger when handed the
 * parts-of-speech model's components, and that an unreadable model path surfaces as an
 * {@link IOException}.
 */
class MalletCrfTaggerLoaderTest {
    @Test
    void load__missingModelThrowsIoException() {
        // ARRANGE //
        MalletCrfTaggerLoader loader = new MalletCrfTaggerLoader();

        // ACT & ASSERT //
        assertThrows(
                IOException.class,
                () -> loader.load(
                        Path.of("does-not-exist.crf"),
                        PartsOfSpeechModel.INSTANCE.featureExtractor(),
                        PartsOfSpeechModel.INSTANCE.tagProvider(),
                        new WhitespaceTokenizer()
                )
        );
    }

    @Test
    void load__returnsWorkingTagger() throws IOException {
        // ARRANGE //
        MalletCrfTaggerLoader loader = new MalletCrfTaggerLoader();

        // ACT //
        CrfTagger<?, ?> tagger = loader.load(
                PartsOfSpeechModel.INSTANCE.modelPath(),
                PartsOfSpeechModel.INSTANCE.featureExtractor(),
                PartsOfSpeechModel.INSTANCE.tagProvider(),
                new WhitespaceTokenizer()
        );

        // ASSERT //
        assertNotNull(tagger);
        TaggedTokenization<?, ?> tagged = tagger.tag("They quickly opened the door");
        assertTrue(tagged.taggedSequence().size() > 0, "the loaded tagger should label the input");
    }

    @Test
    void serviceLoader__discoversExactlyMalletLoader() {
        // ACT //
        List<CrfTaggerLoader> loaders = ServiceLoader.load(CrfTaggerLoader.class).stream()
                .map(ServiceLoader.Provider::get).toList();

        // ASSERT //
        assertEquals(1, loaders.size(), "exactly one CrfTaggerLoader should be registered; was: " + loaders);
        assertInstanceOf(MalletCrfTaggerLoader.class, loaders.getFirst());
    }
}
