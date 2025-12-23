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
package org.coordinatekit.crf.core.io;

import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.UncheckedCrfException;
import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import javax.xml.stream.*;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Reads and writes CRF training data in XML format, and generates XSD schemas for tag validation.
 *
 * <p>
 * This class provides functionality for:
 * <ul>
 * <li>Reading training sequences from XML input streams</li>
 * <li>Generating XSD schemas that define valid tag elements</li>
 * </ul>
 *
 * <p>
 * When reading XML, elements in the CRF schema namespace
 * ({@code https://coordinatekit.org/crf/schema}) are excluded from the training data. This allows
 * structural elements like {@code <crf:Sequence>} and {@code <crf:Excluded>} to be ignored while
 * processing only the tag elements.
 *
 * @param <T> the type of tag used in training sequences
 * @see TrainingSchemaGenerator
 * @see TrainingDataSequencer
 */
@NullMarked
public class XmlTrainingData<T extends Comparable<T>> implements TrainingSchemaGenerator, TrainingDataSequencer<T> {

    /**
     * The namespace URI for CRF structural elements.
     *
     * <p>
     * Elements in this namespace (such as {@code <crf:Sequence>} and {@code <crf:Excluded>}) are used
     * for document structure and are excluded when reading training data.
     */
    public static final String CRF_SCHEMA_NAMESPACE_URI = "https://coordinatekit.org/crf/schema";

    /**
     * The local name of the element that contains a training sequence.
     *
     * <p>
     * Each {@code <Sequence>} element in the CRF namespace contains a single training example, with
     * child elements representing tagged tokens.
     */
    public static final String SEQUENCE_ELEMENT_NAME = "Sequence";

    private static final String XML_SCHEMA_NAMESPACE_URI = "http://www.w3.org/2001/XMLSchema";

    private final TagProvider<T> tagProvider;
    private final @Nullable String targetNamespace;

    /**
     * Constructs an {@code XmlTrainingData} instance for reading training data.
     *
     * <p>
     * This constructor creates an instance that can read training sequences from XML but cannot
     * generate schemas (since no target namespace is specified).
     *
     * @param tagProvider the provider for encoding and decoding tags
     */
    public XmlTrainingData(TagProvider<T> tagProvider) {
        this(tagProvider, null);
    }

    /**
     * Constructs an {@code XmlTrainingData} instance with a target namespace for schema generation.
     *
     * <p>
     * The target namespace is used when generating XSD schemas and defines the namespace for tag
     * elements in the schema.
     *
     * @param tagProvider the provider for encoding and decoding tags
     * @param targetNamespace the target namespace URI for generated schemas, or {@code null} if schema
     *        generation is not needed
     */
    public XmlTrainingData(TagProvider<T> tagProvider, @Nullable String targetNamespace) {
        this.tagProvider = tagProvider;
        this.targetNamespace = targetNamespace;
    }

    @Override
    public void generateSchema(OutputStream output) {
        if (targetNamespace == null) {
            throw new IllegalStateException(String.format("""
                    A target namespace must be specified to generate a schema. \
                    This can be accomplished by setting the `targetNamespace` parameter on `%s`.\
                    """, getClass().getName()));
        } else if (targetNamespace.isBlank()) {
            throw new IllegalStateException(String.format("""
                    A non-blank target namespace (`"%s"`) must be specified to generate a schema. \
                    This can be accomplished by setting the `targetNamespace` parameter on `%s`.\
                    """, targetNamespace, getClass().getName()));
        } else if (tagProvider.tags().isEmpty()) {
            throw new IllegalStateException(String.format("""
                    The tag provider must contain at least one tag. \
                    This can be accomplished by ensuring `tags()` returns a value on `%s`.\
                    """, tagProvider.getClass().getName()));
        }

        XMLOutputFactory factory = XMLOutputFactory.newInstance();

        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(output, "UTF-8");
            try {
                generateSchema(writer);
            } finally {
                writer.close();
            }
        } catch (XMLStreamException e) {
            throw new UncheckedCrfException(e);
        }
    }

    /**
     * Writes the complete XSD schema document to the given writer.
     *
     * @param writer the XML stream writer to write the schema to
     * @throws XMLStreamException if an error occurs while writing
     */
    private void generateSchema(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartDocument("UTF-8", "1.0");
        writer.writeCharacters("\n");

        writer.writeStartElement("xs", "schema", XML_SCHEMA_NAMESPACE_URI);
        writer.writeNamespace("xs", XML_SCHEMA_NAMESPACE_URI);
        writer.writeAttribute("targetNamespace", targetNamespace);
        writer.writeAttribute("elementFormDefault", "qualified");
        writer.writeCharacters("\n");

        generateSchemaTagType(writer);
        generateSchemaTags(writer);

        writer.writeEndElement();
        writer.writeCharacters("\n");
        writer.writeEndDocument();
    }

    /**
     * Writes the {@code TagType} complex type definition to the schema.
     *
     * <p>
     * The generated type is a mixed content type extending {@code xs:string}, allowing tag elements to
     * contain text content.
     *
     * @param writer the XML stream writer to write the type definition to
     * @throws XMLStreamException if an error occurs while writing
     */
    private void generateSchemaTagType(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeCharacters("    ");
        writer.writeStartElement(XML_SCHEMA_NAMESPACE_URI, "complexType");
        writer.writeAttribute("name", "TagType");
        writer.writeAttribute("mixed", "true");
        writer.writeCharacters("\n");

        writer.writeCharacters("        ");
        writer.writeStartElement(XML_SCHEMA_NAMESPACE_URI, "simpleContent");
        writer.writeCharacters("\n");

        writer.writeCharacters("            ");
        writer.writeEmptyElement(XML_SCHEMA_NAMESPACE_URI, "extension");
        writer.writeAttribute("base", "xs:string");
        writer.writeCharacters("\n");

        writer.writeCharacters("        ");
        writer.writeEndElement();
        writer.writeCharacters("\n");

        writer.writeCharacters("    ");
        writer.writeEndElement();
        writer.writeCharacters("\n\n");
    }

    /**
     * Writes element declarations for all tags from the tag provider.
     *
     * @param writer the XML stream writer to write the element declarations to
     * @throws XMLStreamException if an error occurs while writing
     */
    private void generateSchemaTags(XMLStreamWriter writer) throws XMLStreamException {
        for (T tag : tagProvider.tags()) {
            String tagName = tagProvider.encode(tag);
            if (tagName != null) {
                generateSchemaTagElement(writer, tagName);
            }
        }
    }

    /**
     * Writes a single element declaration for a tag.
     *
     * @param writer the XML stream writer to write the element declaration to
     * @param tagName the name of the tag element to declare
     * @throws XMLStreamException if an error occurs while writing
     */
    private void generateSchemaTagElement(XMLStreamWriter writer, String tagName) throws XMLStreamException {
        writer.writeCharacters("    ");
        writer.writeEmptyElement(XML_SCHEMA_NAMESPACE_URI, "element");
        writer.writeAttribute("name", tagName);
        writer.writeAttribute("type", "TagType");
        writer.writeCharacters("\n");
    }

    @Override
    public Stream<TrainingSequence<T>> read(InputStream input) {
        XMLInputFactory factory = XMLInputFactory.newInstance();

        try {
            XMLStreamReader reader = factory.createXMLStreamReader(input);
            Iterator<TrainingSequence<T>> iterator = new SequenceIterator<>(tagProvider, reader);
            Spliterator<TrainingSequence<T>> spliterator = Spliterators
                    .spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL);
            return StreamSupport.stream(spliterator, false).onClose(() -> closeReader(reader));
        } catch (XMLStreamException e) {
            throw new UncheckedCrfException(e);
        }
    }

    /**
     * Closes the XML stream reader, wrapping any exception in an unchecked exception.
     *
     * @param reader the reader to close
     */
    private static void closeReader(XMLStreamReader reader) {
        try {
            reader.close();
        } catch (XMLStreamException e) {
            throw new UncheckedCrfException(e);
        }
    }

    /**
     * An iterator that lazily parses training sequences from an XML stream.
     *
     * <p>
     * This iterator reads {@code <Sequence>} elements from the XML and converts each one into a
     * {@link TrainingSequence}. Elements in the CRF schema namespace are skipped during parsing.
     *
     * @param <T> the type of tag used in training sequences
     */
    private static class SequenceIterator<T extends Comparable<T>> implements Iterator<TrainingSequence<T>> {
        private boolean finished = false;
        private @Nullable TrainingSequence<T> next;
        private final XMLStreamReader reader;
        private final TagProvider<T> tagProvider;

        /**
         * Constructs a new sequence iterator.
         *
         * @param tagProvider the provider for decoding tag names
         * @param reader the XML stream reader positioned at the start of the document
         */
        SequenceIterator(TagProvider<T> tagProvider, XMLStreamReader reader) {
            this.reader = reader;
            this.tagProvider = tagProvider;
        }

        @Override
        public boolean hasNext() {
            if (finished) {
                return false;
            }
            if (next != null) {
                return true;
            }
            next = readNextSequence();
            return next != null;
        }

        @Override
        public TrainingSequence<T> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            TrainingSequence<T> result = next;
            next = null;
            // noinspection DataFlowIssue
            return result;
        }

        /**
         * Parses a single sequence element and its child tag elements.
         *
         * <p>
         * The reader should be positioned just after the opening {@code <Sequence>} tag. This method reads
         * all child elements, extracting tokens and tags from non-CRF-namespace elements, until the closing
         * {@code </Sequence>} tag is reached.
         *
         * @return the parsed training sequence
         * @throws XMLStreamException if an error occurs while reading
         */
        private TrainingSequence<T> parseSequence() throws XMLStreamException {
            List<T> tags = new ArrayList<>();
            List<String> tokens = new ArrayList<>();
            int depth = 1;

            while (reader.hasNext() && depth > 0) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    if (CRF_SCHEMA_NAMESPACE_URI.equals(reader.getNamespaceURI())) {
                        skipElement();
                    } else {
                        String localName = reader.getLocalName();
                        String token = reader.getElementText().trim();
                        if (!token.isEmpty()) {
                            tags.add(tagProvider.decode(localName));
                            tokens.add(token);
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                }
            }

            return new TrainingSequence<>(tokens, tags);
        }

        /**
         * Skips the current element and all of its nested content.
         *
         * <p>
         * The reader should be positioned on a start element. This method advances the reader past the
         * matching end element, effectively skipping the entire element tree.
         *
         * @throws XMLStreamException if an error occurs while reading
         */
        private void skipElement() throws XMLStreamException {
            int depth = 1;
            while (reader.hasNext() && depth > 0) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    depth++;
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                }
            }
        }

        /**
         * Advances the reader to the next {@code <Sequence>} element and parses it.
         *
         * @return the next training sequence, or {@code null} if no more sequences exist
         */
        private @Nullable TrainingSequence<T> readNextSequence() {
            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT
                            && SEQUENCE_ELEMENT_NAME.equals(reader.getLocalName())) {
                        return parseSequence();
                    }
                }
                finished = true;
                return null;
            } catch (XMLStreamException e) {
                throw new UncheckedCrfException(e);
            }
        }
    }
}
