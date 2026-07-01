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
package org.coordinatekit.crf.core.tag;

import static org.coordinatekit.crf.core.preprocessing.Feature.createFeature;
import org.coordinatekit.crf.core.Sequence;
import org.coordinatekit.crf.core.preprocessing.Segment;
import org.coordinatekit.crf.core.preprocessing.Tokenization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

import static org.coordinatekit.crf.core.preprocessing.Segments.excluded;
import static org.coordinatekit.crf.core.preprocessing.Segments.token;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaggedTokenizationTest {
    private static TaggedSequence<String> taggedSequence(List<String> tokens) {
        return new TaggedSequence<>(
                tokens,
                tokens.stream().map(t -> Set.of(createFeature("f_" + t))).toList(),
                tokens.stream().map(t -> Map.of("TAG", 1.0)).toList()
        );
    }

    private static Tokenization tokenization(Segment... segments) {
        return new Tokenization(List.of(segments));
    }

    record ExceptionParameters(
            String name,
            Class<? extends Throwable> expectedException,
            String expectedMessage,
            Executable action
    ) {}

    @Test
    void accessors__returnConstructorArguments() {
        // ARRANGE //
        Sequence<TaggedPositionedToken<String>> tagged = taggedSequence(List.of("Brown", "Fox"));
        Tokenization tokenization = tokenization(token("Brown"), excluded(" "), token("Fox"), excluded("!"));

        ToDoubleFunction<List<String>> probabilityFunction = tags -> tags.size() / 2.0;

        // ACT //
        TaggedTokenization<String> result = TaggedTokenizations.of(tagged, tokenization, probabilityFunction);

        // ASSERT //
        assertSame(tagged, result.taggedSequence());
        assertSame(tokenization, result.tokenization());
    }

    @SuppressWarnings({"DataFlowIssue", "NullAway"})
    static Stream<ExceptionParameters> of__exception() {
        return Stream.of(
                new ExceptionParameters(
                        "fewer_tagged_tokens_than_token_segments",
                        IllegalArgumentException.class,
                        "The tagged sequence must have one entry per token segment: "
                                + "got 1 tagged tokens for 2 token segments.",
                        () -> TaggedTokenizations.of(
                                taggedSequence(List.of("Brown")),
                                tokenization(token("Brown"), excluded(" "), token("Fox")),
                                tags -> 1.0
                        )
                ),
                new ExceptionParameters(
                        "more_tagged_tokens_than_token_segments",
                        IllegalArgumentException.class,
                        "The tagged sequence must have one entry per token segment: "
                                + "got 2 tagged tokens for 1 token segments.",
                        () -> TaggedTokenizations.of(
                                taggedSequence(List.of("Brown", "Fox")),
                                tokenization(token("Brown"), excluded("!")),
                                tags -> 1.0
                        )
                ),
                new ExceptionParameters(
                        "null_probability_function",
                        NullPointerException.class,
                        "probabilityFunction must not be null",
                        () -> TaggedTokenizations
                                .of(taggedSequence(List.of("Brown")), tokenization(token("Brown")), null)
                ),
                new ExceptionParameters(
                        "null_tagged_sequence",
                        NullPointerException.class,
                        "taggedSequence must not be null",
                        () -> TaggedTokenizations.of(null, tokenization(token("Brown")), tags -> 1.0)
                ),
                new ExceptionParameters(
                        "null_tokenization",
                        NullPointerException.class,
                        "tokenization must not be null",
                        () -> TaggedTokenizations.of(taggedSequence(List.of("Brown")), null, tags -> 1.0)
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void of__exception(ExceptionParameters parameters) {
        // ACT //
        Throwable throwable = assertThrows(parameters.expectedException(), parameters.action());

        // ASSERT //
        assertEquals(parameters.expectedMessage(), throwable.getMessage());
    }

    @Test
    void probabilityOf__delegatesToFunction() {
        // ARRANGE //
        Sequence<TaggedPositionedToken<String>> tagged = taggedSequence(List.of("Brown", "Fox"));
        Tokenization tokenization = tokenization(token("Brown"), excluded(" "), token("Fox"), excluded("!"));
        ToDoubleFunction<List<String>> probabilityFunction = tags -> tags.size() / 2.0;
        TaggedTokenization<String> result = TaggedTokenizations.of(tagged, tokenization, probabilityFunction);

        // ACT + ASSERT //
        assertEquals(1.0, result.probabilityOf(List.of("DET", "NOUN")), "probabilityOf delegates to the function");
    }

    @SuppressWarnings({"DataFlowIssue", "NullAway"})
    static Stream<ExceptionParameters> probabilityOf__exception() {
        return Stream.of(
                new ExceptionParameters(
                        "null_tags",
                        NullPointerException.class,
                        "tags must not be null",
                        () -> TaggedTokenizations
                                .of(taggedSequence(List.of("Brown")), tokenization(token("Brown")), tags -> 1.0)
                                .probabilityOf(null)
                ),
                new ExceptionParameters(
                        "wrong_sized_tags",
                        IllegalArgumentException.class,
                        "tags must have one entry per token, got: tags=1, tokens=2",
                        () -> TaggedTokenizations.of(
                                taggedSequence(List.of("Brown", "Fox")),
                                tokenization(token("Brown"), excluded(" "), token("Fox")),
                                tags -> 1.0
                        ).probabilityOf(List.of("DET"))
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void probabilityOf__exception(ExceptionParameters parameters) {
        // ACT //
        Throwable throwable = assertThrows(parameters.expectedException(), parameters.action());

        // ASSERT //
        assertEquals(parameters.expectedMessage(), throwable.getMessage());
    }
}
