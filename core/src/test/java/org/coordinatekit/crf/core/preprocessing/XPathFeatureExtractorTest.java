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

import org.coordinatekit.crf.core.InputSequence;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@NullMarked
class XPathFeatureExtractorTest {
    static final String EMPTY_TEXT_XML = """
            <root>
                <item>   </item>
                <item>valid</item>
                <item></item>
            </root>
            """;
    static final String NESTED_XML = """
            <root>
                <parent>
                    <child>nested</child>
                </parent>
                <child>direct</child>
            </root>
            """;

    static Supplier<InputStream> classpathResource(String resourceName) {
        InputStream inputStream = XPathFeatureExtractorTest.class.getResourceAsStream(resourceName);
        assertNotNull(inputStream);
        return () -> inputStream;
    }

    static Supplier<InputStream> stringResource(String string) {
        return () -> new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
    }

    record ExtractAtParameters(
            String name,
            boolean caseSensitive,
            @Nullable String notPresentFeature,
            @Nullable String presentFeature,
            List<String> tokens,
            int position,
            Set<String> expectedResult
    ) {
    }

    static Stream<ExtractAtParameters> extractAt() {
        return Stream.of(
                new ExtractAtParameters(
                        "caseSensitive_returnsNotPresentFeatureWhenNotFound",
                        true,
                        "NOT_CONJUNCTION",
                        "CONJUNCTION",
                        List.of("hello", "world"),
                        0,
                        Set.of("NOT_CONJUNCTION")
                ),
                new ExtractAtParameters(
                        "caseSensitive_returnsPresentFeatureWhenFound",
                        true,
                        "NOT_CONJUNCTION",
                        "CONJUNCTION",
                        List.of("hello", "and", "world"),
                        1,
                        Set.of("CONJUNCTION")
                ),
                new ExtractAtParameters(
                        "caseSensitive_doesNotMatchDifferentCase",
                        true,
                        "NOT_CONJUNCTION",
                        "CONJUNCTION",
                        List.of("AND"),
                        0,
                        Set.of("NOT_CONJUNCTION")
                ),
                new ExtractAtParameters(
                        "caseInsensitive_matchesDifferentCase",
                        false,
                        "NOT_CONJUNCTION",
                        "CONJUNCTION",
                        List.of("AND"),
                        0,
                        Set.of("CONJUNCTION")
                ),
                new ExtractAtParameters(
                        "caseInsensitive_matchesMixedCase",
                        false,
                        "NOT_CONJUNCTION",
                        "CONJUNCTION",
                        List.of("AnD"),
                        0,
                        Set.of("CONJUNCTION")
                ),
                new ExtractAtParameters(
                        "caseInsensitive_returnsNotPresentFeatureWhenNotFound",
                        false,
                        "NOT_CONJUNCTION",
                        "CONJUNCTION",
                        List.of("hello"),
                        0,
                        Set.of("NOT_CONJUNCTION")
                ),
                new ExtractAtParameters(
                        "nullNotPresentFeature_returnsEmptySetWhenNotFound",
                        true,
                        null,
                        "CONJUNCTION",
                        List.of("hello"),
                        0,
                        Set.of()
                ),
                new ExtractAtParameters(
                        "nullPresentFeature_returnsEmptySetWhenFound",
                        true,
                        "NOT_CONJUNCTION",
                        null,
                        List.of("and"),
                        0,
                        Set.of()
                ),
                new ExtractAtParameters(
                        "returnsCorrectFeatureAtDifferentPositions",
                        true,
                        "NOT_CONJUNCTION",
                        "CONJUNCTION",
                        List.of("hello", "but", "world", "or", "foo"),
                        3,
                        Set.of("CONJUNCTION")
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void extractAt(ExtractAtParameters parameters) {
        // ARRANGE //
        XPathFeatureExtractor<String> extractor = XPathFeatureExtractor
                .<String>builder(classpathResource("english_conjunctions.xml").get(), "/conjunctions/conjunction")
                .caseSensitive(parameters.caseSensitive()).notPresentFeature(parameters.notPresentFeature())
                .presentFeature(parameters.presentFeature()).build();
        InputSequence sequence = new InputSequence(parameters.tokens());

        // ACT //
        Set<String> actual = extractor.extractAt(sequence, parameters.position());

        // ASSERT //
        assertEquals(parameters.expectedResult(), actual);
    }

    record GetXPathValuesParameters(
            String name,
            Supplier<InputStream> resource,
            String xpath,
            List<String> expectedResult
    ) {
    }

    static Stream<GetXPathValuesParameters> getXPathValues() {
        return Stream.of(
                new GetXPathValuesParameters(
                        "attributeOnRootElement",
                        classpathResource("english_parts_of_speech.xml"),
                        "/partsOfSpeech[@lang='en']//word[@degree='superlative']",
                        List.of("quickest")
                ),
                new GetXPathValuesParameters(
                        "attributePredicate_filtersByDegree",
                        classpathResource("english_parts_of_speech.xml"),
                        "//word[@degree='comparative']",
                        List.of("quicker")
                ),
                new GetXPathValuesParameters(
                        "attributePredicate_filtersByType",
                        classpathResource("english_parts_of_speech.xml"),
                        "//word[@type='proper']",
                        List.of("London", "Earth")
                ),
                new GetXPathValuesParameters(
                        "attributePredicate_filtersOnNestedElements",
                        classpathResource("english_parts_of_speech.xml"),
                        "//word[@tense='past']",
                        List.of("ran", "walked")
                ),
                new GetXPathValuesParameters(
                        "combinedDescendantAndAttribute",
                        classpathResource("english_parts_of_speech.xml"),
                        "//category[@abbrev='NN']/word",
                        List.of("house", "tree", "London", "Earth")
                ),
                new GetXPathValuesParameters(
                        "descendantAxis_matchesAtAnyDepth",
                        stringResource(NESTED_XML),
                        "//child",
                        List.of("nested", "direct")
                ),
                new GetXPathValuesParameters(
                        "descendantAxis_withPartsOfSpeechResource",
                        classpathResource("english_parts_of_speech.xml"),
                        "//word",
                        List.of(
                                "run",
                                "ran",
                                "running",
                                "walk",
                                "walked",
                                "house",
                                "tree",
                                "London",
                                "Earth",
                                "quick",
                                "quicker",
                                "quickest",
                                "bright"
                        )
                ),
                new GetXPathValuesParameters(
                        "doesNotMatchPartialPath",
                        stringResource(NESTED_XML),
                        "/root/child",
                        List.of("direct")
                ),
                new GetXPathValuesParameters(
                        "extractsConjunctionsFromXmlResource",
                        classpathResource("english_conjunctions.xml"),
                        "/conjunctions/conjunction",
                        List.of("and", "but", "or", "so", "yet")
                ),
                new GetXPathValuesParameters(
                        "filtersElementsByAttribute",
                        classpathResource("english_parts_of_speech.xml"),
                        "//category[@name='verb']/word",
                        List.of("run", "ran", "running", "walk", "walked")
                ),
                new GetXPathValuesParameters(
                        "handlesCdataContent",
                        stringResource("<root><item><![CDATA[cdata content]]></item></root>"),
                        "/root/item",
                        List.of("cdata content")
                ),
                new GetXPathValuesParameters(
                        "handlesNestedElements",
                        classpathResource("english_parts_of_speech.xml"),
                        "//partsOfSpeech/category/word",
                        List.of(
                                "run",
                                "ran",
                                "running",
                                "walk",
                                "walked",
                                "house",
                                "tree",
                                "London",
                                "Earth",
                                "quick",
                                "quicker",
                                "quickest",
                                "bright"
                        )
                ),
                new GetXPathValuesParameters(
                        "handlesXpathWithoutLeadingSlash",
                        stringResource("<root><item>value</item></root>"),
                        "root/item",
                        List.of("value")
                ),
                new GetXPathValuesParameters(
                        "ignoresEmptyTextContent",
                        stringResource(EMPTY_TEXT_XML),
                        "/root/item",
                        List.of("valid")
                ),
                new GetXPathValuesParameters(
                        "returnsEmptyStreamForEmptyXml",
                        stringResource("<root></root>"),
                        "/root/item",
                        List.of()
                ),
                new GetXPathValuesParameters(
                        "returnsEmptyStreamForNonMatchingXpath",
                        classpathResource("english_conjunctions.xml"),
                        "/conjunctions/nonexistent",
                        List.of()
                ),
                new GetXPathValuesParameters(
                        "trimsWhitespace",
                        stringResource("<root><item>  trimmed  </item></root>"),
                        "/root/item",
                        List.of("trimmed")
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void getXPathValues(GetXPathValuesParameters parameters)
            throws XPathExpressionException, ParserConfigurationException, IOException, SAXException {
        // ACT //
        List<String> actual = XPathFeatureExtractor.getXPathValues(parameters.resource().get(), parameters.xpath())
                .toList();

        // ASSERT //
        assertIterableEquals(parameters.expectedResult(), actual);
    }
}
