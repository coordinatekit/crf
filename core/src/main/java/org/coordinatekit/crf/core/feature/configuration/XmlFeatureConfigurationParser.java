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
package org.coordinatekit.crf.core.feature.configuration;

import org.coordinatekit.crf.core.UncheckedCrfException;
import org.jspecify.annotations.Nullable;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;

/**
 * The built-in {@link FeatureConfigurationParser} for XML.
 *
 * <p>
 * A document is a single root {@code <featureExtractors>}, in the CRF schema namespace
 * {@code https://coordinatekit.org/schema/crf/feature-configuration}, wrapping exactly one
 * {@code <extractor>}:
 *
 * <pre>
 * {@code
 * <featureExtractors xmlns="https://coordinatekit.org/schema/crf/feature-configuration">
 *     <extractor type="window">
 *         <parameter name="before" value="3"/>
 *         <parameter name="after" value="3"/>
 *         <extractor type="composite">
 *             <extractor type="length"/>
 *             <extractor type="prefix">
 *                 <parameter name="name" value="PREFIX2"/>
 *                 <parameter name="length" value="2"/>
 *             </extractor>
 *         </extractor>
 *     </extractor>
 * </featureExtractors>
 * }
 * </pre>
 *
 * <p>
 * Each {@code <extractor>} names its factory {@link FeatureExtractorNode#type() type} in the
 * required {@code type} attribute and may carry the optional boolean {@code key} attribute; each
 * {@code <parameter>} carries a required {@code name} and {@code value} and no content of its own.
 * {@code <parameter>} and nested {@code <extractor>} children may appear in any order, and a
 * {@code <parameter>} name must be unique among its siblings.
 *
 * <p>
 * The full grammar — the namespaced root and element shapes, required attributes, the
 * no-duplicate-{@code name} rule, and the empty-content rule on {@code <parameter>} — is owned by
 * the {@code feature-configuration.xsd} schema bundled alongside this class. Every document is
 * validated against it before the node tree is built, and every rejection the validator reports
 * surfaces as a located {@link FeatureConfigurationParseException} whose detail text comes from the
 * validator.
 *
 * <p>
 * Reading is hardened the same way {@code XmlTrainingData} hardens its own reads: DTDs and external
 * entities are disabled for both the validation pass and the tree-building pass, so DTD-based XXE
 * and entity-expansion ("billion laughs") attacks in an untrusted configuration cannot resolve. The
 * document is also read under a fixed byte ceiling, {@link #MAXIMUM_DOCUMENT_BYTES}, so an
 * oversized configuration is rejected before it is buffered or validated.
 *
 * <p>
 * Every malformed-input problem — unparsable XML, a rejected {@code DOCTYPE}, a schema violation, a
 * document larger than {@link #MAXIMUM_DOCUMENT_BYTES} bytes, or nesting deeper than
 * {@link FeatureExtractorNode#MAXIMUM_NESTING_DEPTH} (a depth cap the schema cannot express, so
 * this parser enforces it directly while building the tree) — surfaces as a located
 * {@link FeatureConfigurationParseException}.
 */
public final class XmlFeatureConfigurationParser implements FeatureConfigurationParser {
    private static final String KEY_ATTRIBUTE = "key";

    /**
     * The maximum size, in bytes, of a feature-configuration document this parser will read. A guard
     * against a pathologically or maliciously large configuration: the read stops at this ceiling
     * before the document is buffered or validated, so an unbounded source fails fast rather than being
     * slurped into memory. Hand-authored configurations are a few kilobytes; this leaves ample
     * headroom.
     */
    static final int MAXIMUM_DOCUMENT_BYTES = 1_048_576;

    private static final String NAME_ATTRIBUTE = "name";
    private static final String PARAMETER_ELEMENT = "parameter";
    private static final String SCHEMA_RESOURCE = "feature-configuration.xsd";
    private static final String TYPE_ATTRIBUTE = "type";
    private static final String VALUE_ATTRIBUTE = "value";

    /** Creates the parser, for {@link java.util.ServiceLoader}. */
    public XmlFeatureConfigurationParser() {}

    /**
     * Advances {@code reader} to the next {@code START_ELEMENT} event.
     *
     * @param reader the reader
     * @return {@code true} if a start element was reached, {@code false} if the document was exhausted
     *         first
     * @throws XMLStreamException if reading fails
     */
    private static boolean advanceToNextStartElement(XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the value of the unqualified attribute named {@code name} on the reader's current start
     * element, or {@code null} if it is absent.
     *
     * @param reader the reader, positioned on a start element
     * @param name the attribute name
     * @return the attribute value, or {@code null}
     */
    private static @Nullable String attributeValue(XMLStreamReader reader, String name) {
        return reader.getAttributeValue(null, name);
    }

    /**
     * Closes {@code reader}, swallowing a failure since it carries no information actionable after a
     * parse has already succeeded or failed.
     *
     * @param reader the reader to close
     */
    private static void closeQuietly(XMLStreamReader reader) {
        try {
            reader.close();
        } catch (XMLStreamException ignored) {
            // Not actionable once parsing has finished, one way or the other.
        }
    }

    /**
     * Wraps an untrusted input stream in a {@link SAXSource} backed by a parser that disables DTDs and
     * external entities.
     *
     * <p>
     * The validator parses through this reader, so a {@code <!DOCTYPE>} declaration and external-entity
     * references in the document are blocked before they can resolve. Setting the feature on the
     * {@link Validator} alone is not sufficient, because the JDK does not enforce it for a
     * {@link StreamSource}.
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
        } catch (ParserConfigurationException | SAXException exception) {
            throw new UncheckedCrfException(exception);
        }
    }

    /**
     * Returns the {@link SourceLocation} for {@code location}, carrying {@code source}.
     *
     * @param source the source URI
     * @param location the StAX location, or {@code null} if unavailable
     * @return the source location
     */
    private static SourceLocation locationOf(URI source, @Nullable Location location) {
        if (location == null) {
            return SourceLocation.of(source, -1, -1);
        }
        return SourceLocation.of(source, location.getLineNumber(), location.getColumnNumber());
    }

    /**
     * Creates an {@link XMLInputFactory} hardened against DTD-based XXE and entity-expansion attacks,
     * the same posture {@code XmlTrainingData} applies to training data.
     *
     * @return a hardened StAX input factory
     */
    private static XMLInputFactory newHardenedInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory;
    }

    @Override
    public FeatureExtractorNode parse(URL url) {
        try (InputStream input = url.openStream()) {
            return parse(input, url.toURI());
        } catch (IOException exception) {
            throw new UncheckedIOException("could not read feature configuration: " + url, exception);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("configuration URL is not a valid URI: " + url, exception);
        }
    }

    /**
     * Parses the tree from a stream, carrying {@code source} in the resulting tree's
     * {@link SourceLocation}. Package-private so this parser's tests can feed inline strings without
     * going through a real {@link URL}; the public entry point is {@link #parse(URL)}.
     *
     * <p>
     * The stream is read up to {@link #MAXIMUM_DOCUMENT_BYTES} and buffered once so it can be validated
     * against the schema and then, if valid, built into a tree without reopening the underlying source.
     *
     * @param input the stream to parse
     * @param source the URI of the source to carry into each node's {@link SourceLocation}
     * @return the root of the parsed node tree
     * @throws FeatureConfigurationParseException if the input is not well-formed in this parser's
     *         format, or is larger than {@link #MAXIMUM_DOCUMENT_BYTES}
     */
    FeatureExtractorNode parse(InputStream input, URI source) {
        byte[] bytes;
        try {
            bytes = input.readNBytes(MAXIMUM_DOCUMENT_BYTES + 1);
        } catch (IOException exception) {
            throw new UncheckedIOException("could not read feature configuration: " + source, exception);
        }
        if (bytes.length > MAXIMUM_DOCUMENT_BYTES) {
            throw new FeatureConfigurationParseException(
                    SourceLocation.of(source, -1, -1),
                    "the configuration is larger than the maximum of " + MAXIMUM_DOCUMENT_BYTES + " bytes"
            );
        }

        validateAgainstSchema(bytes, source);

        XMLInputFactory factory = newHardenedInputFactory();
        XMLStreamReader reader = null;
        try {
            reader = factory.createXMLStreamReader(new ByteArrayInputStream(bytes));
            return parseDocument(reader, source);
        } catch (XMLStreamException exception) {
            // Defensive: validateAgainstSchema above already parsed these bytes and rejected any
            // non-well-formed document at FailFastErrorHandler.fatalError, so this is not expected to
            // fire; map it to a located error rather than let a StAX failure escape unlocated.
            throw new FeatureConfigurationParseException(
                    locationOf(source, exception.getLocation()),
                    "malformed XML: " + exception.getMessage(),
                    exception
            );
        } finally {
            if (reader != null) {
                closeQuietly(reader);
            }
        }
    }

    /**
     * Builds the tree from a document already known to be schema-valid: advances to the root
     * {@code <featureExtractors>} element, then to its one {@code <extractor>} child.
     *
     * @param reader the reader, positioned before the document's first event
     * @param source the source URI to carry into each node's {@link SourceLocation}
     * @return the root of the parsed node tree
     * @throws XMLStreamException if reading fails
     * @throws FeatureConfigurationParseException if the document ends before its root
     *         {@code <extractor>} element
     */
    private static FeatureExtractorNode parseDocument(XMLStreamReader reader, URI source) throws XMLStreamException {
        // Advance past the root <featureExtractors> to its single <extractor> child. Schema validation
        // guarantees both are present; the guard is defensive against early end-of-document.
        advanceToNextStartElement(reader);
        if (!advanceToNextStartElement(reader)) {
            throw new FeatureConfigurationParseException(
                    locationOf(source, reader.getLocation()),
                    "a schema-valid feature configuration has no root <extractor> element"
            );
        }
        return parseExtractor(reader, source, 1);
    }

    /**
     * Parses one {@code <extractor>} element, recursing into nested {@code <extractor>} children and
     * collecting {@code <parameter>} children, until reaching its matching end element.
     *
     * <p>
     * The {@code type} and {@code key} attributes and the shape of the children are already guaranteed
     * by schema validation; the one rule this parser still enforces itself is the maximum nesting
     * depth, which XSD cannot express.
     *
     * @param reader the reader, positioned on the extractor's start element
     * @param source the source URI to carry into the node's {@link SourceLocation}
     * @param depth the one-based nesting depth of this element, the root's depth being {@code 1}
     * @return the parsed node
     * @throws XMLStreamException if reading fails
     * @throws FeatureConfigurationParseException if {@code depth} exceeds
     *         {@link FeatureExtractorNode#MAXIMUM_NESTING_DEPTH}
     */
    private static FeatureExtractorNode parseExtractor(XMLStreamReader reader, URI source, int depth)
            throws XMLStreamException {
        SourceLocation location = locationOf(source, reader.getLocation());
        if (depth > FeatureExtractorNode.MAXIMUM_NESTING_DEPTH) {
            throw new FeatureConfigurationParseException(
                    location,
                    "nesting is deeper than the maximum of " + FeatureExtractorNode.MAXIMUM_NESTING_DEPTH
            );
        }
        String type = Objects.requireNonNull(attributeValue(reader, TYPE_ATTRIBUTE), "type is required by the schema");

        FeatureExtractorNodes.Builder builder = FeatureExtractorNodes.builder(type).sourceLocation(location);
        String keyAttribute = attributeValue(reader, KEY_ATTRIBUTE);
        if (keyAttribute != null) {
            builder.key(parseKeyAttribute(keyAttribute));
        }

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (PARAMETER_ELEMENT.equals(reader.getLocalName())) {
                    parseParameter(reader, builder);
                } else {
                    // Dispatch is by local name: the XSD permits no element here but <parameter> or
                    // <extractor>, so anything not named "parameter" is a nested extractor — a foreign
                    // element is already rejected by schema validation before this code runs.
                    builder.child(parseExtractor(reader, source, depth + 1));
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
        }
        return builder.build();
    }

    /**
     * Parses the lexical value of the {@code key} attribute, whose form ({@code true}/{@code false}/
     * {@code 1}/{@code 0}, optionally padded with whitespace) is already guaranteed by schema
     * validation.
     *
     * @param raw the raw attribute value
     * @return the parsed boolean
     */
    private static boolean parseKeyAttribute(String raw) {
        String trimmed = raw.trim();
        return "true".equals(trimmed) || "1".equals(trimmed);
    }

    /**
     * Parses one {@code <parameter>} element into an entry on {@code builder}, then consumes through
     * its matching end element.
     *
     * @param reader the reader, positioned on the parameter's start element
     * @param builder the node builder to add the parameter to
     * @throws XMLStreamException if reading fails
     */
    private static void parseParameter(XMLStreamReader reader, FeatureExtractorNodes.Builder builder)
            throws XMLStreamException {
        String name = Objects.requireNonNull(attributeValue(reader, NAME_ATTRIBUTE), "name is required by the schema");
        String value = Objects
                .requireNonNull(attributeValue(reader, VALUE_ATTRIBUTE), "value is required by the schema");
        builder.parameter(name, value);
        skipToElementEnd(reader);
    }

    /**
     * Advances {@code reader} through the remaining content of the current element to its matching end
     * element.
     *
     * @param reader the reader, positioned inside an element whose start element has already been
     *        consumed
     * @throws XMLStreamException if reading fails
     */
    private static void skipToElementEnd(XMLStreamReader reader) throws XMLStreamException {
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
     * Validates {@code bytes} against the compiled {@link SchemaHolder#SCHEMA feature-configuration
     * schema}, throwing on the first problem reported.
     *
     * @param bytes the buffered document bytes
     * @param source the source URI to carry into a rejection's {@link SourceLocation}
     * @throws FeatureConfigurationParseException if the document is not schema-valid, carries a
     *         {@code DOCTYPE}, or is not well-formed XML
     */
    private static void validateAgainstSchema(byte[] bytes, URI source) {
        Validator validator = SchemaHolder.SCHEMA.newValidator();
        try {
            validator.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (SAXException exception) {
            throw new UncheckedCrfException(exception);
        }
        validator.setErrorHandler(new FailFastErrorHandler(source));
        try {
            validator.validate(hardenedSource(new ByteArrayInputStream(bytes)));
        } catch (SAXParseException exception) {
            throw new FeatureConfigurationParseException(
                    SourceLocation.of(source, exception.getLineNumber(), exception.getColumnNumber()),
                    Objects.requireNonNullElse(exception.getMessage(), "the document is invalid"),
                    exception
            );
        } catch (SAXException exception) {
            throw new FeatureConfigurationParseException(
                    SourceLocation.of(source, -1, -1),
                    Objects.requireNonNullElse(exception.getMessage(), "the document is invalid"),
                    exception
            );
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * A {@link ErrorHandler} that rejects a document at the first {@code error} or {@code fatalError}
     * it is reported, translating it into a located {@link FeatureConfigurationParseException}, rather
     * than collecting every problem in the document.
     *
     * <p>
     * Unlike {@code XmlTrainingData}'s collect-all error handler, this parser's located-error contract
     * calls for a single located rejection, not an aggregate report.
     */
    private static final class FailFastErrorHandler implements ErrorHandler {
        private final URI source;

        FailFastErrorHandler(URI source) {
            this.source = source;
        }

        @Override
        public void error(SAXParseException exception) {
            throw toParseException(exception);
        }

        @Override
        public void fatalError(SAXParseException exception) {
            throw toParseException(exception);
        }

        private FeatureConfigurationParseException toParseException(SAXParseException exception) {
            return new FeatureConfigurationParseException(
                    SourceLocation.of(source, exception.getLineNumber(), exception.getColumnNumber()),
                    Objects.requireNonNullElse(exception.getMessage(), "the document is invalid"),
                    exception
            );
        }

        @Override
        public void warning(SAXParseException exception) {
            // Warnings do not render a document invalid and are intentionally ignored.
        }
    }

    /**
     * Lazily compiles and caches the fixed {@code feature-configuration.xsd} schema.
     *
     * <p>
     * The schema is fixed — unlike {@code XmlTrainingData}, there is no per-instance tag vocabulary to
     * compile in alongside it — so a {@code static final} field initialized by the class-loading JVM
     * guarantee (the initialization-on-demand holder idiom) is enough; there is no need for
     * {@code XmlTrainingData}'s per-instance double-checked locking.
     */
    private static final class SchemaHolder {
        private static final Schema SCHEMA = loadSchema();

        private static Schema loadSchema() {
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            try {
                schemaFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            } catch (SAXException exception) {
                throw new UncheckedCrfException(exception);
            }
            try (InputStream resource = XmlFeatureConfigurationParser.class.getResourceAsStream(SCHEMA_RESOURCE)) {
                if (resource == null) {
                    throw new UncheckedCrfException(
                            "The feature-configuration schema resource '" + SCHEMA_RESOURCE
                                    + "' was not found on the classpath."
                    );
                }
                return schemaFactory.newSchema(new StreamSource(resource));
            } catch (IOException | SAXException e) {
                throw new UncheckedCrfException(e);
            }
        }
    }
}
