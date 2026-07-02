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

import org.coordinatekit.crf.core.Sequence;
import org.coordinatekit.crf.core.preprocessing.DefaultFeatureFormat;
import org.coordinatekit.crf.core.preprocessing.Feature;
import org.coordinatekit.crf.core.preprocessing.FeatureFormat;
import org.coordinatekit.crf.core.preprocessing.WhitespaceTokenizer;
import org.coordinatekit.crf.core.tag.TagScore;
import org.coordinatekit.crf.core.tag.TaggedPositionedToken;
import org.coordinatekit.crf.core.tag.TaggedTokenization;
import org.coordinatekit.crf.core.util.Serializables;
import org.coordinatekit.crf.mallet.model.PartsOfSpeechModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MalletCrfTaggerTest {
    private MalletCrfTagger<String> tagger;

    @BeforeEach
    void setup() throws IOException {
        tagger = new MalletCrfTagger<>(
                PartsOfSpeechModel.INSTANCE.featureExtractor(),
                PartsOfSpeechModel.INSTANCE.featureFormat(),
                PartsOfSpeechModel.INSTANCE.modelPath(),
                PartsOfSpeechModel.INSTANCE.tagProvider(),
                new WhitespaceTokenizer()
        );
    }

    @Test
    void constructorRejectsDisallowedClassInModelFile() throws IOException {
        // A serialized object whose class lives in a package outside the model deserialization allowlist
        // (cc.mallet.**;gnu.trove.**;java.**) must be rejected before its graph is materialized.
        Path maliciousPath = Files.createTempFile("malicious_model", ".crf");
        try {
            Serializables.serialize(new DisallowedPayload(), maliciousPath);

            assertThrows(
                    IOException.class,
                    () -> new MalletCrfTagger<>(
                            PartsOfSpeechModel.INSTANCE.featureExtractor(),
                            PartsOfSpeechModel.INSTANCE.featureFormat(),
                            maliciousPath,
                            PartsOfSpeechModel.INSTANCE.tagProvider(),
                            new WhitespaceTokenizer()
                    )
            );
        } finally {
            Files.deleteIfExists(maliciousPath);
        }
    }

    @Test
    void constructorThrowsIOExceptionForNonExistentPath() {
        Path nonExistentPath = Path.of("/non/existent/model.crf");

        assertThrows(
                IOException.class,
                () -> new MalletCrfTagger<>(
                        PartsOfSpeechModel.INSTANCE.featureExtractor(),
                        PartsOfSpeechModel.INSTANCE.featureFormat(),
                        nonExistentPath,
                        PartsOfSpeechModel.INSTANCE.tagProvider(),
                        new WhitespaceTokenizer()
                )
        );
    }

    @Test
    void constructorThrowsExceptionForCorruptModelFile() throws IOException {
        Path tempFile = Files.createTempFile("corrupt_model", ".crf");
        try {
            Files.writeString(tempFile, "not a valid serialized CRF model");

            assertThrows(
                    Exception.class,
                    () -> new MalletCrfTagger<>(
                            PartsOfSpeechModel.INSTANCE.featureExtractor(),
                            PartsOfSpeechModel.INSTANCE.featureFormat(),
                            tempFile,
                            PartsOfSpeechModel.INSTANCE.tagProvider(),
                            new WhitespaceTokenizer()
                    )
            );
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void probabilityOf__bestTaggingIsProbableAndDominatesWorstTagging() {
        // ARRANGE //
        TaggedTokenization<String> tagged = tagger.tag("They quickly opened the door");
        // The model's per-token best tags, and the per-token worst tags (the lowest-marginal tag at each
        // position), which together make an obviously-wrong tagging.
        List<String> bestTags = tagged.taggedSequence().stream().map(TaggedPositionedToken::tag).toList();
        List<String> worstTags = tagged.taggedSequence().stream().map(token -> token.tagScores().last().tag()).toList();

        // ACT //
        double best = tagged.probabilityOf(bestTags);
        double worst = tagged.probabilityOf(worstTags);

        // ASSERT //
        assertTrue(best > 0.0 && best <= 1.0, "the best tagging's probability must lie in (0, 1], got: " + best);
        assertTrue(worst >= 0.0 && worst <= 1.0, "the worst tagging's probability must lie in [0, 1], got: " + worst);
        assertTrue(worst < best, "an obviously-wrong tagging must score strictly lower than the best, got: " + worst);
    }

    @Test
    void tag() {
        TaggedTokenization<String> tagged = tagger.tag("They quickly opened the door");
        Sequence<TaggedPositionedToken<String>> actual = tagged.taggedSequence();

        assertEquals(
                "They quickly opened the door",
                tagged.tokenization().surface(),
                "the tokenization round-trips the input surface"
        );
        assertEquals(5, actual.size());

        assertTaggedToken(actual.get(0), 0, "DET", "They");
        assertTaggedToken(actual.get(1), 1, "ADJ", "quickly");
        assertTaggedToken(actual.get(2), 2, "NOUN", "opened");
        assertTaggedToken(actual.get(3), 3, "VERB", "the");
        assertTaggedToken(actual.get(4), 4, "ADV", "door");
    }

    private static void assertTaggedToken(
            TaggedPositionedToken<String> token,
            int expectedPosition,
            String expectedTag,
            String expectedToken
    ) {
        assertEquals(expectedPosition, token.position());
        assertEquals(expectedTag, token.tag());
        assertEquals(expectedToken, token.token());
        assertIterableEquals(
                PartsOfSpeechModel.INSTANCE.validTags(),
                token.tagScores().stream().map(TagScore::tag).collect(Collectors.toCollection(TreeSet::new))
        );
        assertTrue(token.tagScores().stream().mapToDouble(TagScore::score).allMatch(s -> s >= 0 && s <= 1));
        assertEquals(1.0, token.tagScores().stream().mapToDouble(TagScore::score).sum(), 0.001);
    }

    @Test
    void tag__routesFeaturesThroughFeatureFormat() throws IOException {
        // ARRANGE //
        // A recording format wrapping the default proves the tagger renders every feature at the alphabet
        // edge rather than touching feature strings itself.
        AtomicInteger renderCount = new AtomicInteger();
        FeatureFormat recordingFormat = new FeatureFormat() {
            private final FeatureFormat delegate = new DefaultFeatureFormat();

            @Override
            public Feature parse(String rendered) {
                return delegate.parse(rendered);
            }

            @Override
            public String render(Feature feature) {
                renderCount.incrementAndGet();
                return delegate.render(feature);
            }
        };
        MalletCrfTagger<String> recordingTagger = new MalletCrfTagger<>(
                PartsOfSpeechModel.INSTANCE.featureExtractor(),
                recordingFormat,
                PartsOfSpeechModel.INSTANCE.modelPath(),
                PartsOfSpeechModel.INSTANCE.tagProvider(),
                new WhitespaceTokenizer()
        );

        // ACT //
        recordingTagger.tag("They quickly opened the door");

        // ASSERT //
        assertTrue(renderCount.get() > 0, "the tagger must render every feature through the FeatureFormat");
    }

    @Test
    void tag_emptyInput() {
        assertThrows(RuntimeException.class, () -> tagger.tag(""));
    }

    @Test
    void tag_singleToken() {
        Sequence<TaggedPositionedToken<String>> actual = tagger.tag("Hello").taggedSequence();

        assertEquals(1, actual.size());
        assertEquals(0, actual.get(0).position());
        assertEquals("Hello", actual.get(0).token());
        assertIterableEquals(
                PartsOfSpeechModel.INSTANCE.validTags(),
                actual.get(0).tagScores().stream().map(TagScore::tag).collect(Collectors.toCollection(TreeSet::new))
        );
    }

    static class DisallowedPayload implements Serializable {}
}
