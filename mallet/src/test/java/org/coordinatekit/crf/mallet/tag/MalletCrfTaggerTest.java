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
import org.coordinatekit.crf.core.preprocessing.WhitespaceTokenizer;
import org.coordinatekit.crf.core.tag.TagScore;
import org.coordinatekit.crf.core.tag.TaggedPositionedToken;
import org.coordinatekit.crf.mallet.model.PartsOfSpeechModel;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NullMarked
class MalletCrfTaggerTest {
    private MalletCrfTagger<String, String> tagger;

    @BeforeEach
    void setup() throws IOException {
        tagger = new MalletCrfTagger<>(
                PartsOfSpeechModel.INSTANCE.featureExtractor(),
                PartsOfSpeechModel.INSTANCE.modelPath(),
                PartsOfSpeechModel.INSTANCE.tagProvider(),
                new WhitespaceTokenizer()
        );
    }

    @Test
    void constructorThrowsIOExceptionForNonExistentPath() {
        Path nonExistentPath = Path.of("/non/existent/model.crf");

        assertThrows(
                IOException.class,
                () -> new MalletCrfTagger<>(
                        PartsOfSpeechModel.INSTANCE.featureExtractor(),
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
    void tag() {
        Sequence<TaggedPositionedToken<String, String>> actual = tagger.tag("They quickly opened the door");

        assertEquals(5, actual.size());

        assertEquals(0, actual.get(0).position());
        assertEquals("DET", actual.get(0).tag());
        assertEquals("They", actual.get(0).token());
        assertIterableEquals(
                PartsOfSpeechModel.INSTANCE.validTags(),
                actual.get(0).tagScores().stream().map(TagScore::tag).collect(Collectors.toCollection(TreeSet::new))
        );
        assertTrue(actual.get(0).tagScores().stream().mapToDouble(TagScore::score).allMatch(s -> s >= 0 && s <= 1));
        assertEquals(1.0, actual.get(0).tagScores().stream().mapToDouble(TagScore::score).sum(), 0.001);

        assertEquals(1, actual.get(1).position());
        assertEquals("ADJ", actual.get(1).tag());
        assertEquals("quickly", actual.get(1).token());
        assertIterableEquals(
                PartsOfSpeechModel.INSTANCE.validTags(),
                actual.get(1).tagScores().stream().map(TagScore::tag).collect(Collectors.toCollection(TreeSet::new))
        );
        assertTrue(actual.get(1).tagScores().stream().mapToDouble(TagScore::score).allMatch(s -> s >= 0 && s <= 1));
        assertEquals(1.0, actual.get(1).tagScores().stream().mapToDouble(TagScore::score).sum(), 0.001);

        assertEquals(2, actual.get(2).position());
        assertEquals("NOUN", actual.get(2).tag());
        assertEquals("opened", actual.get(2).token());
        assertIterableEquals(
                PartsOfSpeechModel.INSTANCE.validTags(),
                actual.get(2).tagScores().stream().map(TagScore::tag).collect(Collectors.toCollection(TreeSet::new))
        );
        assertTrue(actual.get(2).tagScores().stream().mapToDouble(TagScore::score).allMatch(s -> s >= 0 && s <= 1));
        assertEquals(1.0, actual.get(2).tagScores().stream().mapToDouble(TagScore::score).sum(), 0.001);

        assertEquals(3, actual.get(3).position());
        assertEquals("VERB", actual.get(3).tag());
        assertEquals("the", actual.get(3).token());
        assertIterableEquals(
                PartsOfSpeechModel.INSTANCE.validTags(),
                actual.get(3).tagScores().stream().map(TagScore::tag).collect(Collectors.toCollection(TreeSet::new))
        );
        assertTrue(actual.get(3).tagScores().stream().mapToDouble(TagScore::score).allMatch(s -> s >= 0 && s <= 1));
        assertEquals(1.0, actual.get(3).tagScores().stream().mapToDouble(TagScore::score).sum(), 0.001);

        assertEquals(4, actual.get(4).position());
        assertEquals("ADV", actual.get(4).tag());
        assertEquals("door", actual.get(4).token());
        assertIterableEquals(
                PartsOfSpeechModel.INSTANCE.validTags(),
                actual.get(4).tagScores().stream().map(TagScore::tag).collect(Collectors.toCollection(TreeSet::new))
        );
        assertTrue(actual.get(4).tagScores().stream().mapToDouble(TagScore::score).allMatch(s -> s >= 0 && s <= 1));
        assertEquals(1.0, actual.get(4).tagScores().stream().mapToDouble(TagScore::score).sum(), 0.001);
    }

    @Test
    void tag_emptyInput() {
        assertThrows(RuntimeException.class, () -> tagger.tag(""));
    }

    @Test
    void tag_singleToken() {
        Sequence<TaggedPositionedToken<String, String>> actual = tagger.tag("Hello");

        assertEquals(1, actual.size());
        assertEquals(0, actual.get(0).position());
        assertEquals("Hello", actual.get(0).token());
        assertIterableEquals(
                PartsOfSpeechModel.INSTANCE.validTags(),
                actual.get(0).tagScores().stream().map(TagScore::tag).collect(Collectors.toCollection(TreeSet::new))
        );
    }
}
