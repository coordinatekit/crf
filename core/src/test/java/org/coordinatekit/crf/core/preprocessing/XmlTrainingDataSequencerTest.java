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
package org.coordinatekit.crf.core.preprocessing;

import org.coordinatekit.crf.core.StringTagProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("SequencedCollectionMethodCanBeUsed")
class XmlTrainingDataSequencerTest {
    private static final XmlTrainingDataSequencer<String> SEQUENCER = new XmlTrainingDataSequencer<>(
            new StringTagProvider("0")
    );

    private static final String MULTIPLE_RECORDS_XML = """
            <Collection>
                <Sequence><Adjective>Brown</Adjective> <Noun>Fox!</Noun></Sequence>
                <Sequence><Adjective>Lazy</Adjective> <Adjective>Sleeping</Adjective> <Noun>Dog!</Noun></Sequence>
            </Collection>
            """;
    private static final String NO_RECORDS_XML = "<Collection />";
    private static final String SINGLE_RECORD_XML = """
            <Collection>
                <Sequence><Adjective>Brown</Adjective> <Noun>Fox!</Noun></Sequence>
            </Collection>
            """;

    @Test
    void read__multipleRecord() throws IOException {
        List<TrainingSequence<String>> actual = SEQUENCER
                .read(new ByteArrayInputStream(MULTIPLE_RECORDS_XML.getBytes(StandardCharsets.UTF_8))).toList();

        assertEquals(2, actual.size());
        assertEquals(2, actual.get(0).size());
        assertEquals("Adjective", actual.get(0).get(0).tag());
        assertEquals("Brown", actual.get(0).get(0).token());
        assertEquals("Noun", actual.get(0).get(1).tag());
        assertEquals("Fox!", actual.get(0).get(1).token());
        assertEquals(3, actual.get(1).size());
        assertEquals("Adjective", actual.get(1).get(0).tag());
        assertEquals("Lazy", actual.get(1).get(0).token());
        assertEquals("Adjective", actual.get(1).get(1).tag());
        assertEquals("Sleeping", actual.get(1).get(1).token());
        assertEquals("Noun", actual.get(1).get(2).tag());
        assertEquals("Dog!", actual.get(1).get(2).token());
    }

    @Test
    void read__noRecords() throws IOException {
        assertTrue(
                SEQUENCER.read(new ByteArrayInputStream(NO_RECORDS_XML.getBytes(StandardCharsets.UTF_8))).findAny()
                        .isEmpty()
        );
    }

    @Test
    void read__singleRecord() throws IOException {
        List<TrainingSequence<String>> actual = SEQUENCER
                .read(new ByteArrayInputStream(SINGLE_RECORD_XML.getBytes(StandardCharsets.UTF_8))).toList();

        assertEquals(1, actual.size());
        assertEquals(2, actual.get(0).size());
        assertEquals("Adjective", actual.get(0).get(0).tag());
        assertEquals("Brown", actual.get(0).get(0).token());
        assertEquals("Noun", actual.get(0).get(1).tag());
        assertEquals("Fox!", actual.get(0).get(1).token());
    }

    @Test
    void read__fromPath(@TempDir Path tempDir) throws IOException {
        Path xmlFile = tempDir.resolve("training.xml");
        Files.writeString(xmlFile, SINGLE_RECORD_XML);

        List<TrainingSequence<String>> actual = SEQUENCER.read(xmlFile).toList();

        assertEquals(1, actual.size());
        assertEquals(2, actual.get(0).size());
        assertEquals("Adjective", actual.get(0).get(0).tag());
        assertEquals("Brown", actual.get(0).get(0).token());
        assertEquals("Noun", actual.get(0).get(1).tag());
        assertEquals("Fox!", actual.get(0).get(1).token());
    }
}
