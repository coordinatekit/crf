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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
 * <li>Writing training sequences to output streams or files, with optional append to existing
 * files</li>
 * <li>Generating XSD schemas that define valid tag elements</li>
 * </ul>
 *
 * <p>
 * When reading XML, elements in the CRF schema namespace
 * ({@code https://coordinatekit.org/crf/schema}) are excluded from the training data. This allows
 * structural elements like {@code <crf:Sequence>} and {@code <crf:Excluded>} to be ignored while
 * processing only the tag elements.
 *
 * <p>
 * When writing XML, the root element is configured by
 * {@link XmlTrainingDataConfiguration#rootElementName()} in the CRF schema namespace (defaulting to
 * {@code <crf:Collection>}). Each training sequence is serialized as a single-line
 * {@code <crf:Sequence>} element with its tag elements inline.
 *
 * <p>
 * {@code XmlTrainingData} instances are stateless and may be safely shared across threads to act as
 * factories. Individual reader streams and writer instances are not thread-safe.
 *
 * @param <T> the type of tag used in training sequences
 * @see TrainingDataAppender
 * @see TrainingDataSequencer
 * @see TrainingSchemaGenerator
 */
@NullMarked
public class XmlTrainingData<T extends Comparable<T>>
        implements TrainingDataAppender<T>, TrainingDataSequencer<T>, TrainingDataStreamer<T>, TrainingSchemaGenerator {

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

    private final byte[] closeTagBytes;
    private final byte[] closeTagNeedle;
    private final XmlTrainingDataConfiguration configuration;
    private final byte[] openDocumentBytes;
    private final TagProvider<T> tagProvider;

    /**
     * Constructs an {@code XmlTrainingData} instance with default configuration.
     *
     * <p>
     * The default configuration uses {@link XmlTrainingDataConfiguration#DEFAULT_ROOT_ELEMENT_NAME} as
     * the root element local name and leaves the target namespace unset, so schema generation is not
     * available without supplying a configuration.
     *
     * @param tagProvider the provider for encoding and decoding tags
     */
    public XmlTrainingData(TagProvider<T> tagProvider) {
        this(tagProvider, XmlTrainingDataConfiguration.defaults());
    }

    /**
     * Constructs an {@code XmlTrainingData} instance with the given configuration.
     *
     * @param tagProvider the provider for encoding and decoding tags
     * @param configuration the configuration controlling root element name and schema target namespace
     */
    public XmlTrainingData(TagProvider<T> tagProvider, XmlTrainingDataConfiguration configuration) {
        this.tagProvider = tagProvider;
        this.configuration = configuration;
        String rootElementName = configuration.rootElementName();
        this.openDocumentBytes = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<crf:" + rootElementName
                + " xmlns:crf=\"" + CRF_SCHEMA_NAMESPACE_URI + "\">\n").getBytes(StandardCharsets.UTF_8);
        this.closeTagBytes = ("</crf:" + rootElementName + ">\n").getBytes(StandardCharsets.UTF_8);
        this.closeTagNeedle = ("</crf:" + rootElementName + ">").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Constructs an {@code XmlTrainingData} instance with a target namespace for schema generation.
     *
     * @param tagProvider the provider for encoding and decoding tags
     * @param targetNamespace the target namespace URI for generated schemas, or {@code null} if schema
     *        generation is not needed
     * @deprecated since 0.2.0 — pass an {@link XmlTrainingDataConfiguration} via
     *             {@link #XmlTrainingData(TagProvider, XmlTrainingDataConfiguration)} instead. The
     *             configuration object also lets callers customize the root element name.
     */
    @Deprecated(since = "0.2.0")
    public XmlTrainingData(TagProvider<T> tagProvider, @Nullable String targetNamespace) {
        this(tagProvider, XmlTrainingDataConfiguration.builder().targetNamespace(targetNamespace).build());
    }

    /**
     * Opens a training sequence writer that appends to the given file.
     *
     * <p>
     * If the file is missing or empty, a fresh document with the configured root element is written. If
     * the file already exists, its first start element is validated against the configured
     * {@link XmlTrainingDataConfiguration#rootElementName() root local name} in the CRF schema
     * namespace; an {@link IOException} is thrown without modifying the file if validation fails. A
     * matching root close tag, if present, is dropped so subsequent writes append in the right
     * position. Files that lack a close tag — for example, because a previous session crashed before
     * {@link TrainingSequenceWriter#close()} ran — are tolerated and appended to in place.
     *
     * <p>
     * The file is a valid XML document only after {@link TrainingSequenceWriter#close()} returns. If
     * the JVM dies before {@code close} runs, the file will be missing a root close tag; passing it
     * back to this method on the next session resumes appending.
     *
     * @param output the path of the training data file to append to
     * @return a writer positioned to append new sequences
     * @throws IOException if the file cannot be opened, validated, or prepared for append
     */
    @Override
    public TrainingSequenceWriter<T> appendingWriter(Path output) throws IOException {
        boolean fresh = !Files.exists(output) || Files.size(output) == 0;
        if (fresh) {
            OutputStream stream = Files.newOutputStream(
                    output,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            return openWriterClosingOnFailure(stream, openDocumentBytes);
        }

        validateAppendable(output, configuration.rootElementName());
        try (FileChannel channel = FileChannel.open(output, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            long closeTagOffset = findLastCloseTagOffset(channel, closeTagNeedle);
            if (closeTagOffset >= 0) {
                channel.truncate(closeTagOffset);
            }
        }
        OutputStream stream = Files.newOutputStream(output, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        return openWriterClosingOnFailure(stream, null);
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
     * Locates the byte offset of the last configured root close tag occurrence in the file.
     *
     * <p>
     * Scans the file from the tail in fixed-size chunks so memory usage is bounded regardless of file
     * size. Chunks overlap by {@code needle.length - 1} bytes so a close tag straddling a chunk
     * boundary is still detected.
     *
     * @param channel the readable file channel to scan
     * @param needle the UTF-8 bytes of the close tag to search for
     * @return the byte offset where the close tag begins, or {@code -1} if no close tag is found
     * @throws IOException if reading from the channel fails
     */
    private static long findLastCloseTagOffset(FileChannel channel, byte[] needle) throws IOException {
        long fileSize = channel.size();
        if (fileSize < needle.length) {
            return -1;
        }
        final int chunkSize = 8192;
        final int overlap = needle.length - 1;
        long chunkEnd = fileSize;

        while (chunkEnd >= needle.length) {
            long chunkStart = Math.max(0L, chunkEnd - chunkSize);
            int length = (int) (chunkEnd - chunkStart);
            ByteBuffer buffer = ByteBuffer.allocate(length);
            long readPosition = chunkStart;
            while (buffer.hasRemaining()) {
                int read = channel.read(buffer, readPosition);
                if (read < 0) {
                    break;
                }
                readPosition += read;
            }
            byte[] bytes = buffer.array();
            for (int i = length - needle.length; i >= 0; i--) {
                boolean matched = true;
                for (int j = 0; j < needle.length; j++) {
                    if (bytes[i + j] != needle[j]) {
                        matched = false;
                        break;
                    }
                }
                if (matched) {
                    return chunkStart + i;
                }
            }
            if (chunkStart == 0) {
                break;
            }
            chunkEnd = chunkStart + overlap;
        }
        return -1;
    }

    @Override
    public void generateSchema(OutputStream output) {
        String targetNamespace = configuration.targetNamespace();
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
        writer.writeAttribute("targetNamespace", Objects.requireNonNull(configuration.targetNamespace()));
        writer.writeAttribute("elementFormDefault", "qualified");
        writer.writeCharacters("\n");

        generateSchemaTagType(writer);
        generateSchemaTags(writer);

        writer.writeEndElement();
        writer.writeCharacters("\n");
        writer.writeEndDocument();
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
     * Wraps a freshly opened stream in a {@link StreamSequenceWriter}, optionally writing a prolog
     * first, and closes the stream if either step fails.
     *
     * <p>
     * The returned writer takes ownership of the stream, so try-with-resources cannot be used at the
     * call site — closing the stream would defeat the purpose of returning it. If writing the prolog or
     * constructing the writer throws, this method closes the stream itself (suppressing any close error
     * onto the original failure) so the file handle is not leaked.
     *
     * @param stream the freshly opened stream that the writer will own
     * @param prolog bytes to write before constructing the writer, or {@code null} to skip
     * @return a writer that owns the stream
     * @throws IOException if writing the prolog or constructing the writer fails
     */
    private TrainingSequenceWriter<T> openWriterClosingOnFailure(OutputStream stream, byte @Nullable [] prolog)
            throws IOException {
        try {
            if (prolog != null) {
                stream.write(prolog);
            }
            return new StreamSequenceWriter<>(tagProvider, stream, closeTagBytes);
        } catch (Throwable failure) {
            try {
                stream.close();
            } catch (IOException suppressed) {
                failure.addSuppressed(suppressed);
            }
            throw failure;
        }
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
     * Validates that the file's first start element matches the configured root local name in the CRF
     * schema namespace.
     *
     * <p>
     * Content past the first start element is not inspected; a file whose tail is missing or truncated
     * — for example, a previous session that crashed before writing the root close tag — is accepted so
     * the writer can resume appending.
     *
     * @param path the file path to validate, also used in error messages
     * @param rootElementName the expected root element local name
     * @throws IOException if the file is malformed before reaching a start element, contains no start
     *         element, or has an unexpected root element
     */
    private static void validateAppendable(Path path, String rootElementName) throws IOException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        try (InputStream input = Files.newInputStream(path)) {
            XMLStreamReader reader;
            try {
                reader = factory.createXMLStreamReader(input);
            } catch (XMLStreamException e) {
                throw new IOException("Cannot append to '" + path + "': malformed XML.", e);
            }
            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        String namespace = reader.getNamespaceURI();
                        String localName = reader.getLocalName();
                        if (!CRF_SCHEMA_NAMESPACE_URI.equals(namespace) || !rootElementName.equals(localName)) {
                            String namespacePart = (namespace == null || namespace.isEmpty()) ? ""
                                    : "{" + namespace + "}";
                            throw new IOException(
                                    "Cannot append to '" + path + "': expected root <crf:" + rootElementName
                                            + "> in namespace '" + CRF_SCHEMA_NAMESPACE_URI + "' but found <"
                                            + namespacePart + localName + ">."
                            );
                        }
                        return;
                    }
                }
                throw new IOException("Cannot append to '" + path + "': no root element found.");
            } catch (XMLStreamException e) {
                throw new IOException("Cannot append to '" + path + "': malformed XML.", e);
            } finally {
                try {
                    reader.close();
                } catch (XMLStreamException ignored) {
                    // Close failures during validation are not actionable.
                }
            }
        }
    }

    /**
     * Opens a single-pass training sequence writer to the given output stream.
     *
     * <p>
     * The XML prolog and the configured root opening element are written immediately; each call to
     * {@link TrainingSequenceWriter#write(TrainingSequence)} appends a single-line
     * {@code <crf:Sequence>} element. The matching root close tag is written when
     * {@link TrainingSequenceWriter#close()} is invoked.
     *
     * <p>
     * The output is a valid XML document only after {@code close()} returns.
     *
     * @param output the output stream to write training data to
     * @return a single-pass training sequence writer
     * @throws IOException if writing the document prolog or root element fails
     */
    @Override
    public TrainingSequenceWriter<T> writer(OutputStream output) throws IOException {
        output.write(openDocumentBytes);
        return new StreamSequenceWriter<>(tagProvider, output, closeTagBytes);
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
    }

    /**
     * A {@link TrainingSequenceWriter} that writes training sequences to an {@link OutputStream}.
     *
     * <p>
     * Sequence content is emitted through a {@link XMLStreamWriter} so escaping and element structure
     * follow the standard XML rules. The root close tag is written as a raw byte fragment on
     * {@link #close()} because StAX has no entry point for emitting a close tag for a parent that was
     * never opened through the writer.
     *
     * <p>
     * The output is a valid XML document only after {@link #close()} returns.
     *
     * @param <T> the type of tag used in the sequences
     */
    private static class StreamSequenceWriter<T extends Comparable<T>> implements TrainingSequenceWriter<T> {
        private final byte[] closeTagBytes;
        private boolean closed;
        private final OutputStream output;
        private final TagProvider<T> tagProvider;
        private final XMLStreamWriter xmlWriter;

        StreamSequenceWriter(TagProvider<T> tagProvider, OutputStream output, byte[] closeTagBytes) throws IOException {
            this.closeTagBytes = closeTagBytes;
            this.output = output;
            this.tagProvider = tagProvider;
            try {
                this.xmlWriter = XMLOutputFactory.newInstance().createXMLStreamWriter(output, "UTF-8");
                this.xmlWriter.setPrefix("crf", CRF_SCHEMA_NAMESPACE_URI);
            } catch (XMLStreamException e) {
                throw new IOException("Failed to initialize XML stream writer.", e);
            }
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            try {
                try {
                    xmlWriter.flush();
                    xmlWriter.close();
                } catch (XMLStreamException e) {
                    throw new IOException("Failed to close XML stream writer.", e);
                }
                output.write(closeTagBytes);
                output.flush();
            } finally {
                output.close();
            }
        }

        @Override
        public void flush() throws IOException {
            if (closed) {
                throw new IOException("Cannot flush after close.");
            }
            try {
                xmlWriter.flush();
            } catch (XMLStreamException e) {
                throw new IOException("Failed to flush XML stream writer.", e);
            }
            output.flush();
        }

        @Override
        public void write(TrainingSequence<T> sequence) throws IOException {
            if (closed) {
                throw new IOException("Cannot write after close.");
            }
            try {
                xmlWriter.writeCharacters("    ");
                xmlWriter.writeStartElement(CRF_SCHEMA_NAMESPACE_URI, SEQUENCE_ELEMENT_NAME);
                for (int position = 0; position < sequence.size(); position++) {
                    var token = sequence.get(position);
                    String tagName = tagProvider.encode(token.tag());
                    if (tagName == null) {
                        throw new IllegalArgumentException(
                                "Tag '" + token.tag() + "' encodes to null and cannot be serialized."
                        );
                    }
                    xmlWriter.writeStartElement(tagName);
                    xmlWriter.writeCharacters(token.token());
                    xmlWriter.writeEndElement();
                }
                xmlWriter.writeEndElement();
                xmlWriter.writeCharacters("\n");
            } catch (XMLStreamException e) {
                throw new IOException("Failed to write training sequence.", e);
            }
        }
    }
}
