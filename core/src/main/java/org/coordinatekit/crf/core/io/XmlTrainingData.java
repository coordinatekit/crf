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

import static org.coordinatekit.crf.core.preprocessing.TrainingSegments.excluded;
import static org.coordinatekit.crf.core.preprocessing.TrainingSegments.token;

import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.UncheckedCrfException;
import org.coordinatekit.crf.core.preprocessing.SegmentKind;
import org.coordinatekit.crf.core.preprocessing.TrainingSegment;
import org.coordinatekit.crf.core.preprocessing.TrainingSequence;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.*;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
 * <li>Validating documents against the fixed structural grammar and the generated tag
 * vocabulary</li>
 * </ul>
 *
 * <p>
 * Two namespaces are in play. The <em>structural grammar</em>
 * ({@code https://coordinatekit.org/crf/schema}) is fixed and library-owned: it defines the
 * {@code <crf:Collection>} / {@code <crf:Sequence>} / {@code <crf:Excluded>} shape and lives in the
 * static {@code crf-structure.xsd} resource. The <em>tag vocabulary</em> is dynamic and per-
 * {@link TagProvider}: {@link #generateSchema(OutputStream)} emits one element declaration per tag.
 * The {@link XmlTrainingDataConfiguration#targetNamespace() target namespace} is optional: with one
 * configured, the tags are declared in it and the writer declares it as the document's default
 * namespace; with none (the default), the tags are declared in no namespace, matching the bare tag
 * elements the writer emits. Either way the library's own output validates against the schema it
 * generates.
 *
 * <p>
 * When reading XML, {@code <crf:Excluded>} runs in the CRF schema namespace
 * ({@code https://coordinatekit.org/crf/schema}) are captured verbatim as excluded segments, so a
 * sequence's original surface text can be reconstructed. Other CRF-namespace structural elements,
 * such as {@code <crf:Sequence>} and {@code <crf:Collection>}, are skipped, leaving only the tag
 * elements to process.
 *
 * <p>
 * When writing XML, the root element is configured by
 * {@link XmlTrainingDataConfiguration#rootElementName()} in the CRF schema namespace (defaulting to
 * {@code <crf:Collection>}). Each training sequence is serialized as a single-line
 * {@code <crf:Sequence>} element with its tag elements inline.
 *
 * <p>
 * {@code XmlTrainingData} instances hold no mutable observable state (the compiled validation
 * schema is lazily computed but immutable and derived from the configuration) and may be safely
 * shared across threads to act as factories. Individual reader streams and writer instances are not
 * thread-safe.
 *
 * @param <T> the type of tag used in training sequences
 * @see TrainingDataAppender
 * @see TrainingDataSequencer
 * @see TrainingDataValidator
 * @see TrainingSchemaGenerator
 */
@NullMarked
public class XmlTrainingData<T extends Comparable<T>> implements TrainingDataAppender<T>, TrainingDataSequencer<T>,
        TrainingDataStreamer<T>, TrainingDataValidator, TrainingSchemaGenerator {

    /**
     * The namespace URI for CRF structural elements.
     *
     * <p>
     * Elements in this namespace (such as {@code <crf:Sequence>} and {@code <crf:Excluded>}) are used
     * for document structure and are excluded when reading training data.
     */
    public static final String CRF_SCHEMA_NAMESPACE_URI = "https://coordinatekit.org/crf/schema";

    /**
     * The local name of the element that wraps a run of excluded characters dropped by a tokenizer.
     *
     * <p>
     * Each {@code <crf:Excluded>} element in the CRF namespace preserves the characters between,
     * before, or after tokens so a sequence's original surface text can be reconstructed.
     */
    public static final String EXCLUDED_ELEMENT_NAME = "Excluded";

    /**
     * The local name of the element that contains a training sequence.
     *
     * <p>
     * Each {@code <Sequence>} element in the CRF namespace contains a single training example, with
     * child elements representing tagged tokens.
     */
    public static final String SEQUENCE_ELEMENT_NAME = "Sequence";

    /**
     * The classpath resource name of the fixed structural grammar, resolved relative to this class.
     */
    private static final String STRUCTURAL_SCHEMA_RESOURCE = "crf-structure.xsd";

    private static final String XML_SCHEMA_NAMESPACE_URI = "http://www.w3.org/2001/XMLSchema";

    private static final Logger logger = LoggerFactory.getLogger(XmlTrainingData.class);

    private final byte[] closeTagBytes;
    private final byte[] closeTagNeedle;
    private final XmlTrainingDataConfiguration configuration;
    private final byte[] openDocumentBytes;
    private final TagProvider<T> tagProvider;
    private volatile @Nullable Schema validationSchema;

    /**
     * Constructs an {@code XmlTrainingData} instance with default configuration.
     *
     * <p>
     * The default configuration uses {@link XmlTrainingDataConfiguration#DEFAULT_ROOT_ELEMENT_NAME} as
     * the root element local name and leaves the target namespace unset, so generated schemas and
     * written documents place their tag elements in no namespace.
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
        String targetNamespace = configuration.targetNamespace();
        String defaultNamespaceDeclaration = (targetNamespace == null || targetNamespace.isBlank()) ? ""
                : " xmlns=\"" + targetNamespace + "\"";
        this.openDocumentBytes = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<crf:" + rootElementName
                + " xmlns:crf=\"" + CRF_SCHEMA_NAMESPACE_URI + "\"" + defaultNamespaceDeclaration + ">\n")
                        .getBytes(StandardCharsets.UTF_8);
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
        if (tagProvider.tags().isEmpty()) {
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

        String targetNamespace = configuration.targetNamespace();
        writer.writeStartElement("xs", "schema", XML_SCHEMA_NAMESPACE_URI);
        writer.writeNamespace("xs", XML_SCHEMA_NAMESPACE_URI);
        if (targetNamespace != null && !targetNamespace.isBlank()) {
            // Declare the target namespace as the default so unprefixed references such as
            // type="TagType" resolve into it; without this the generated schema cannot be compiled.
            // With no target namespace the unprefixed references resolve into no namespace, where
            // TagType is likewise declared.
            writer.writeDefaultNamespace(targetNamespace);
            writer.writeAttribute("targetNamespace", targetNamespace);
            writer.writeAttribute("elementFormDefault", "qualified");
        }
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
     * Wraps an untrusted input stream in a {@link SAXSource} backed by a parser that disables DTDs and
     * external entities.
     *
     * <p>
     * The validator parses through this reader, so {@code <!DOCTYPE>} declarations and external-entity
     * references in the document are blocked before they can resolve. This is the same
     * {@code disallow-doctype-decl} hardening {@code XPathFeatureExtractor.getXPathValues} applies; it
     * defends against DTD-based XXE and entity-expansion ("billion laughs") attacks. Setting the
     * feature on the {@link Validator} alone is not sufficient, because the JDK does not enforce it for
     * a {@link StreamSource}.
     *
     * @param input the untrusted input stream to read the document from
     * @return a SAX source backed by a hardened reader
     * @throws UncheckedCrfException if the hardened parser cannot be configured
     */
    private static SAXSource hardenedSource(InputStream input) {
        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        parserFactory.setNamespaceAware(true);
        try {
            parserFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            parserFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            parserFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            parserFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            XMLReader reader = parserFactory.newSAXParser().getXMLReader();
            return new SAXSource(reader, new InputSource(input));
        } catch (ParserConfigurationException | SAXException e) {
            throw new UncheckedCrfException(e);
        }
    }

    /**
     * Loads the fixed structural grammar from the classpath, substituting the root element name when a
     * non-default root is configured.
     *
     * <p>
     * The static {@code crf-structure.xsd} declares the document root as
     * {@link XmlTrainingDataConfiguration#DEFAULT_ROOT_ELEMENT_NAME}. A custom root local name is
     * applied by a single in-memory substitution of that element declaration's {@code name} attribute,
     * so the resource stays the one source of truth for the grammar. The substitution requires the
     * default root declaration to appear exactly once; if the resource is reformatted so it no longer
     * matches, the substitution fails loudly rather than silently producing an invalid grammar.
     *
     * @param rootElementName the configured root element local name
     * @return the structural schema bytes, with the root declaration renamed if necessary
     * @throws UncheckedCrfException if the resource is missing, cannot be read, or does not contain
     *         exactly one default root element declaration to substitute
     */
    private static byte[] loadStructuralSchema(String rootElementName) {
        byte[] schemaBytes;
        try (InputStream resource = XmlTrainingData.class.getResourceAsStream(STRUCTURAL_SCHEMA_RESOURCE)) {
            if (resource == null) {
                throw new UncheckedCrfException(
                        "The structural schema resource '" + STRUCTURAL_SCHEMA_RESOURCE
                                + "' was not found on the classpath."
                );
            }
            schemaBytes = resource.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedCrfException(e);
        }

        if (XmlTrainingDataConfiguration.DEFAULT_ROOT_ELEMENT_NAME.equals(rootElementName)) {
            return schemaBytes;
        }
        String original = new String(schemaBytes, StandardCharsets.UTF_8);
        String target = "<xs:element name=\"" + XmlTrainingDataConfiguration.DEFAULT_ROOT_ELEMENT_NAME + "\">";
        int index = original.indexOf(target);
        if (index < 0 || index != original.lastIndexOf(target)) {
            throw new UncheckedCrfException(
                    "Expected exactly one root element declaration (" + target + ") in the structural schema "
                            + "resource '" + STRUCTURAL_SCHEMA_RESOURCE + "' but found "
                            + (index < 0 ? "none" : "more than one") + "; the resource may have been reformatted."
            );
        }
        String schema = original.substring(0, index) + "<xs:element name=\"" + rootElementName + "\">"
                + original.substring(index + target.length());
        return schema.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Creates an {@link XMLInputFactory} hardened against DTD-based XXE and entity-expansion attacks.
     *
     * <p>
     * The reference StAX factory enables {@link XMLInputFactory#SUPPORT_DTD} and
     * {@link XMLInputFactory#IS_SUPPORTING_EXTERNAL_ENTITIES} by default, which lets a
     * {@code <!DOCTYPE>} with nested internal entities expand ("billion laughs") and external entities
     * resolve (XXE) while parsing untrusted training data. Setting {@code SUPPORT_DTD} to {@code false}
     * stops the parser from processing internal entity declarations — an entity reference then fails as
     * undeclared rather than expanding — and disabling external entities blocks XXE.
     *
     * <p>
     * The exact handling of a {@code <!DOCTYPE>} under {@code SUPPORT_DTD=false} is implementation-
     * specific: stricter StAX implementations reject it at reader creation, while the JDK reference
     * implementation surfaces it as a {@link XMLStreamConstants#DTD} event. To match the
     * {@code disallow-doctype-decl} posture {@link #hardenedSource(InputStream)} applies on the
     * validation path — rejecting <em>any</em> document carrying a DOCTYPE outright, regardless of
     * implementation — callers reject the {@code DTD} event explicitly while reading.
     *
     * @return a hardened StAX input factory
     */
    private static XMLInputFactory newHardenedInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory;
    }

    /**
     * Compiles the structural grammar and the generated tag vocabulary into a single validating schema.
     *
     * <p>
     * The tag schema is generated in memory by {@link #generateSchema(OutputStream)}, which enforces
     * the non-empty-tag precondition. Compiling both schemas together resolves the structural grammar's
     * strict namespaced-tag wildcard against the tag schema's global element declarations without an
     * {@code xs:import}. The no-namespace tag wildcard is lax, so undeclared no-namespace tags are
     * permitted (an open vocabulary) rather than rejected.
     *
     * @return a schema that validates a document against both the structure and the tag vocabulary
     * @throws IllegalStateException if the tag set is empty
     * @throws UncheckedCrfException if either schema cannot be compiled
     */
    private Schema newValidationSchema() {
        ByteArrayOutputStream tagSchemaBuffer = new ByteArrayOutputStream();
        generateSchema(tagSchemaBuffer);
        byte[] structuralSchema = loadStructuralSchema(configuration.rootElementName());

        SchemaFactory schemaFactory = SchemaFactory.newInstance(XML_SCHEMA_NAMESPACE_URI);
        try {
            schemaFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (SAXException e) {
            throw new UncheckedCrfException(e);
        }
        Source[] sources = {new StreamSource(new ByteArrayInputStream(structuralSchema)),
                        new StreamSource(new ByteArrayInputStream(tagSchemaBuffer.toByteArray()))};
        try {
            return schemaFactory.newSchema(sources);
        } catch (SAXException e) {
            throw new UncheckedCrfException(e);
        }
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

    /**
     * {@inheritDoc}
     *
     * <p>
     * The document is parsed with DTDs and external entities disabled, so a {@code <!DOCTYPE>}
     * declaration is rejected and DTD-based XXE and entity expansion ("billion laughs") attacks in
     * untrusted input cannot resolve.
     */
    @Override
    public Stream<TrainingSequence<T>> read(InputStream input) {
        XMLInputFactory factory = newHardenedInputFactory();

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
     * {@inheritDoc}
     *
     * <p>
     * The document is parsed with DTDs and external entities disabled, so DTD-based XXE and entity
     * expansion ("billion laughs") attacks in untrusted input cannot resolve.
     *
     * <p>
     * Tag elements validate against the generated tag vocabulary, which follows the configured
     * {@link XmlTrainingDataConfiguration#targetNamespace() target namespace}: with a target namespace
     * the tags are declared in it (and the writer declares it as the document default); with none they
     * are declared in no namespace, matching the bare tag elements the writer emits by default. A
     * document whose tags sit in a different namespace than the instance is configured for does not
     * validate.
     */
    @Override
    public void validate(InputStream input) {
        Schema schema = validationSchema();
        CollectingErrorHandler errorHandler = new CollectingErrorHandler();
        Validator validator = schema.newValidator();
        try {
            validator.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (SAXException e) {
            throw new UncheckedCrfException(e);
        }
        validator.setErrorHandler(errorHandler);
        try {
            validator.validate(hardenedSource(input));
        } catch (SAXException e) {
            errorHandler.recordFatal(e);
        } catch (IOException e) {
            throw new UncheckedCrfException(e);
        }
        errorHandler.throwIfInvalid();
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
     * <p>
     * The file is parsed with DTDs and external entities disabled, so a {@code <!DOCTYPE>} declaration
     * is rejected before any DTD-based XXE or entity-expansion ("billion laughs") attack can resolve.
     *
     * @param path the file path to validate, also used in error messages
     * @param rootElementName the expected root element local name
     * @throws IOException if the file is malformed before reaching a start element, carries a DOCTYPE
     *         declaration, contains no start element, or has an unexpected root element
     */
    private static void validateAppendable(Path path, String rootElementName) throws IOException {
        XMLInputFactory factory = newHardenedInputFactory();
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
                    if (event == XMLStreamConstants.DTD) {
                        throw new IOException("Cannot append to '" + path + "': DOCTYPE declarations are not allowed.");
                    }
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
     * Returns the compiled validation schema, computing and caching it on first use.
     *
     * <p>
     * The compiled {@link Schema} is immutable, thread-safe, and depends only on the {@code final}
     * configuration and tag provider, so it is computed once via {@link #newValidationSchema()} and
     * reused across calls. Double-checked locking on the {@code volatile} field guards concurrent first
     * access.
     *
     * @return the schema that validates documents against the structure and the tag vocabulary
     * @throws IllegalStateException if the tag set is empty
     * @throws UncheckedCrfException if either schema cannot be compiled
     */
    private Schema validationSchema() {
        Schema schema = validationSchema;
        if (schema == null) {
            synchronized (this) {
                schema = validationSchema;
                if (schema == null) {
                    schema = newValidationSchema();
                    validationSchema = schema;
                }
            }
        }
        return schema;
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
     * Collects validation problems instead of aborting on the first one, so a single
     * {@link #validate(InputStream)} call can report every error in a document.
     *
     * <p>
     * Warnings are ignored because they do not render a document invalid. Recoverable errors are
     * reported through {@link #error(SAXParseException)} as the validator continues; a fatal error is
     * reported through {@link #fatalError(SAXParseException)} and then rethrown by the validator, which
     * {@link #recordFatal(SAXException)} reconciles so it is not counted twice.
     */
    private static final class CollectingErrorHandler implements ErrorHandler {
        private final List<String> errors = new ArrayList<>();

        @Override
        public void error(SAXParseException exception) {
            errors.add(format(exception));
        }

        @Override
        public void fatalError(SAXParseException exception) {
            errors.add(format(exception));
        }

        /**
         * Formats a parse exception with its location, when available.
         *
         * @param exception the parse exception to format
         * @return a human-readable message, prefixed with line and column when the parser supplied them
         */
        private static String format(SAXParseException exception) {
            String message = Objects.requireNonNullElse(exception.getMessage(), "unknown error");
            int line = exception.getLineNumber();
            if (line < 0) {
                return message;
            }
            return "line " + line + ", column " + exception.getColumnNumber() + ": " + message;
        }

        /**
         * Records a fatal exception thrown out of {@code validate}, unless one was already collected.
         *
         * <p>
         * The validator calls {@link #fatalError(SAXParseException)} before rethrowing, so in the common
         * case the problem is already recorded and this is a no-op. A non-parse {@link SAXException} that
         * never reached the handler is recorded by its message.
         *
         * @param exception the exception thrown by the validator
         */
        void recordFatal(SAXException exception) {
            if (errors.isEmpty()) {
                errors.add(exception.getMessage());
            }
        }

        /**
         * Throws an {@link UncheckedCrfException} aggregating every collected problem, if any.
         *
         * @throws UncheckedCrfException if one or more validation problems were collected
         */
        void throwIfInvalid() {
            if (!errors.isEmpty()) {
                throw new UncheckedCrfException("The training data document is invalid:\n" + String.join("\n", errors));
            }
        }

        @Override
        public void warning(SAXParseException exception) {
            // Warnings do not render a document invalid and are intentionally ignored.
        }
    }

    /**
     * An iterator that lazily parses training sequences from an XML stream.
     *
     * <p>
     * This iterator reads {@code <Sequence>} elements from the XML and converts each one into a
     * {@link TrainingSequence}. {@code <crf:Excluded>} elements are captured as excluded segments;
     * other elements in the CRF schema namespace are skipped during parsing.
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
            TrainingSequence<T> result = Objects.requireNonNull(next);
            next = null;
            return result;
        }

        /**
         * Parses a single sequence element and its child elements.
         *
         * <p>
         * The reader should be positioned just after the opening {@code <Sequence>} tag. This method reads
         * all child elements until the closing {@code </Sequence>} tag is reached, assembling segments in
         * document order. {@code <crf:Excluded>} elements in the CRF schema namespace are captured verbatim
         * as {@link SegmentKind#EXCLUDED} segments (preserving whitespace, never trimmed), but an empty
         * (zero-length) excluded run is dropped; other CRF-namespace elements are skipped; non-CRF elements
         * become trimmed token segments, dropped when empty.
         *
         * @return the parsed training sequence
         * @throws XMLStreamException if an error occurs while reading
         */
        private TrainingSequence<T> parseSequence() throws XMLStreamException {
            List<TrainingSegment<T>> segments = new ArrayList<>();
            int depth = 1;

            while (reader.hasNext() && depth > 0) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    if (CRF_SCHEMA_NAMESPACE_URI.equals(reader.getNamespaceURI())) {
                        if (EXCLUDED_ELEMENT_NAME.equals(reader.getLocalName())) {
                            String excludedText = readExcludedText();
                            if (excludedText != null && !excludedText.isEmpty()) {
                                segments.add(excluded(excludedText));
                            }
                        } else {
                            skipElement();
                        }
                    } else {
                        String localName = reader.getLocalName();
                        String token = reader.getElementText().trim();
                        if (!token.isEmpty()) {
                            segments.add(token(tagProvider.decode(localName), token));
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                }
            }

            return TrainingSequence.ofSegments(segments);
        }

        /**
         * Reads the verbatim text content of a {@code <crf:Excluded>} element.
         *
         * <p>
         * The reader should be positioned on the {@code <crf:Excluded>} start element. This method
         * accumulates {@code CHARACTERS} and {@code CDATA} content until the matching end element, never
         * trimming. If a nested start element is encountered (which the schema forbids but a hand-authored
         * document may contain), the element is skipped and ignored: this method consumes through the
         * matching end element and returns {@code null} so the run is not captured.
         *
         * @return the verbatim excluded text, or {@code null} if the element contained a child element
         * @throws XMLStreamException if an error occurs while reading
         */
        private @Nullable String readExcludedText() throws XMLStreamException {
            StringBuilder text = new StringBuilder();
            boolean hasChildElement = false;
            int depth = 1;

            while (reader.hasNext() && depth > 0) {
                int event = reader.next();
                switch (event) {
                    case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                        if (!hasChildElement) {
                            text.append(reader.getText());
                        }
                    }
                    case XMLStreamConstants.START_ELEMENT -> {
                        hasChildElement = true;
                        depth++;
                    }
                    case XMLStreamConstants.END_ELEMENT -> depth--;
                    default -> {
                        // Ignore comments, processing instructions, and other event types.
                    }
                }
            }

            if (hasChildElement) {
                logger.debug(
                        "Skipped a <crf:Excluded> run because it contained a child element, which the schema "
                                + "forbids."
                );
                return null;
            }
            return text.toString();
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
                    if (event == XMLStreamConstants.DTD) {
                        throw new UncheckedCrfException(
                                "Training data must not contain a DOCTYPE declaration; DTDs are disabled to "
                                        + "prevent XXE and entity-expansion attacks."
                        );
                    }
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
                for (TrainingSegment<T> segment : sequence.segments()) {
                    if (segment.kind() == SegmentKind.EXCLUDED) {
                        xmlWriter.writeStartElement(CRF_SCHEMA_NAMESPACE_URI, EXCLUDED_ELEMENT_NAME);
                        xmlWriter.writeCharacters(segment.text());
                        xmlWriter.writeEndElement();
                    } else {
                        T tag = Objects.requireNonNull(segment.tag());
                        String tagName = tagProvider.encode(tag);
                        if (tagName == null) {
                            throw new IllegalArgumentException(
                                    "Tag '" + tag + "' encodes to null and cannot be serialized."
                            );
                        }
                        xmlWriter.writeStartElement(tagName);
                        xmlWriter.writeCharacters(segment.text());
                        xmlWriter.writeEndElement();
                    }
                }
                xmlWriter.writeEndElement();
                xmlWriter.writeCharacters("\n");
            } catch (XMLStreamException e) {
                throw new IOException("Failed to write training sequence.", e);
            }
        }
    }
}
