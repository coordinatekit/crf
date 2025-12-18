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

import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.UncheckedCrfException;
import org.jspecify.annotations.NullMarked;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@NullMarked
public class XmlTrainingDataSequencer<T> implements TrainingDataSequencer<T> {
    private final TagProvider<T> tagProvider;

    /**
     * Constructs a new XML training data sequencer with the specified tag converter.
     *
     * @param tagProvider the converter used to transform tag strings from XML elements into tag objects
     */
    public XmlTrainingDataSequencer(TagProvider<T> tagProvider) {
        this.tagProvider = tagProvider;
    }

    @Override
    public Stream<TrainingSequence<T>> read(InputStream input) throws IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(input);

            List<TrainingSequence<T>> addressStrings = new ArrayList<>();

            NodeList addressStringNodes = doc.getElementsByTagName("Sequence");
            for (int i = 0; i < addressStringNodes.getLength(); i++) {
                Node addressStringNode = addressStringNodes.item(i);
                List<T> tags = new ArrayList<>();
                List<String> tokens = new ArrayList<>();

                NodeList children = addressStringNode.getChildNodes();
                for (int j = 0; j < children.getLength(); j++) {
                    Node child = children.item(j);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        String label = child.getNodeName();
                        String text = child.getTextContent().trim();
                        if (!text.isEmpty()) {
                            tags.add(tagProvider.decode(label));
                            tokens.add(text);
                        }
                    }
                }

                if (!tokens.isEmpty()) {
                    addressStrings.add(new TrainingSequence<>(tokens, tags));
                }
            }

            return addressStrings.stream();
        } catch (IOException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new UncheckedCrfException(e);
        }
    }
}
