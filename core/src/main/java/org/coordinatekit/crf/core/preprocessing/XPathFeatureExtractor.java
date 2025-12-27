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

import org.coordinatekit.crf.core.PositionedToken;
import org.coordinatekit.crf.core.Sequence;
import org.coordinatekit.crf.core.UncheckedCrfException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A feature extractor that checks whether tokens are present in a set of values loaded from an XML
 * file using XPath expressions.
 *
 * <p>
 * This extractor loads a set of string values from an XML document at construction time, then
 * checks each token against this set during feature extraction. It supports both case-sensitive and
 * case-insensitive matching.
 *
 * <p>
 * Use the {@link #builder(InputStream, String)} factory method to create instances:
 *
 * <pre>
 * <code>
 * XPathFeatureExtractor&lt;String&gt; extractor = XPathFeatureExtractor
 *         .&lt;String&gt;builder(getClass().getResourceAsStream("conjunctions.xml"), "/conjunctions/conjunction")
 *         .caseSensitive(false).presentFeature("CONJUNCTION").notPresentFeature("NOT_CONJUNCTION").build();
 * </code>
 * </pre>
 *
 * @param <F> the type of features produced by this extractor
 */
@NullMarked
public class XPathFeatureExtractor<F> implements FeatureExtractor<F> {
    private final Function<String, String> normalizer;
    private final @Nullable F notPresentFeature;
    private final @Nullable F presentFeature;
    private final Set<String> values;

    private XPathFeatureExtractor(Builder<F> builder)
            throws XPathExpressionException, ParserConfigurationException, SAXException, IOException {
        this.normalizer = builder.caseSensitive ? Function.identity() : s -> s.toLowerCase(Locale.ROOT);
        this.notPresentFeature = builder.notPresentFeature;
        this.presentFeature = builder.presentFeature;
        this.values = getXPathValues(builder.inputStream, builder.xpath).map(normalizer).collect(Collectors.toSet());
    }

    @Override
    public Set<F> extractAt(Sequence<? extends PositionedToken> sequence, int position) {
        String normalizedToken = normalizer.apply(sequence.get(position).token());
        if (values.contains(normalizedToken)) {
            return presentFeature != null ? Set.of(presentFeature) : Set.of();
        } else {
            return notPresentFeature != null ? Set.of(notPresentFeature) : Set.of();
        }
    }

    /**
     * Creates a new builder with the specified XML input stream and XPath expression.
     *
     * @param inputStream the XML input stream containing the values to match against
     * @param xpath the XPath expression to select values from the XML
     * @param <F> the type of feature produced by the extractor
     * @return a new builder instance
     */
    public static <F> Builder<F> builder(InputStream inputStream, String xpath) {
        return new Builder<>(inputStream, xpath);
    }

    /**
     * Extracts text content from XML nodes matching an XPath expression. Uses DOM parsing with the
     * standard {@link javax.xml.xpath} API.
     *
     * <p>
     * Supports full XPath 1.0 expressions including:
     * <ul>
     * <li>Element paths: {@code /root/parent/child}</li>
     * <li>Attribute predicates: {@code //element[@attr='value']}</li>
     * <li>Descendant axis: {@code //element}</li>
     * </ul>
     *
     * @param inputStream the XML input stream to parse
     * @param xpath the XPath expression (e.g., "/states/state/nameTokens/nameToken")
     * @return a stream of text content from matching nodes
     * @throws XPathExpressionException if the XPath expression is invalid
     * @throws ParserConfigurationException if the XML parser cannot be configured
     * @throws SAXException if an error occurs during XML parsing
     * @throws IOException if an I/O error occurs reading the input stream
     */
    static Stream<String> getXPathValues(InputStream inputStream, String xpath)
            throws XPathExpressionException, ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(inputStream);

        XPathFactory xPathFactory = XPathFactory.newInstance();
        XPath xPath = xPathFactory.newXPath();

        NodeList nodeList = (NodeList) xPath.evaluate(xpath, document, XPathConstants.NODESET);

        return IntStream.range(0, nodeList.getLength()).mapToObj(nodeList::item).map(Node::getTextContent)
                .map(String::trim).filter(s -> !s.isEmpty());
    }

    /**
     * Builder for {@link XPathFeatureExtractor}.
     *
     * @param <F> the type of feature produced by the extractor
     */
    public static final class Builder<F> {
        private final InputStream inputStream;
        private final String xpath;
        private boolean caseSensitive = true;
        private @Nullable F presentFeature;
        private @Nullable F notPresentFeature;

        private Builder(InputStream inputStream, String xpath) {
            this.inputStream = inputStream;
            this.xpath = xpath;
        }

        /**
         * Sets whether token matching should be case-sensitive.
         *
         * <p>
         * Defaults to {@code true} (case-sensitive).
         *
         * @param caseSensitive {@code true} for case-sensitive matching, {@code false} for case-insensitive
         * @return this builder
         */
        public Builder<F> caseSensitive(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
            return this;
        }

        /**
         * Sets the feature to emit when a token is present in the value set.
         *
         * @param presentFeature the feature to emit when present, or {@code null} for no feature
         * @return this builder
         */
        public Builder<F> presentFeature(@Nullable F presentFeature) {
            this.presentFeature = presentFeature;
            return this;
        }

        /**
         * Sets the feature to emit when a token is not present in the value set.
         *
         * @param notPresentFeature the feature to emit when not present, or {@code null} for no feature
         * @return this builder
         */
        public Builder<F> notPresentFeature(@Nullable F notPresentFeature) {
            this.notPresentFeature = notPresentFeature;
            return this;
        }

        /**
         * Builds the feature extractor.
         *
         * @return a new {@link XPathFeatureExtractor} instance
         * @throws UncheckedCrfException if the XPath expression is invalid, the XML parser cannot be
         *         configured, or an error occurs during XML parsing
         * @throws UncheckedIOException if an I/O error occurs reading the input stream
         */
        public XPathFeatureExtractor<F> build() {
            try {
                return new XPathFeatureExtractor<>(this);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (Exception e) {
                throw new UncheckedCrfException(e);
            }
        }
    }
}
