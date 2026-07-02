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

import cc.mallet.fst.CRF;
import cc.mallet.fst.SumLattice;
import cc.mallet.types.ArraySequence;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import org.coordinatekit.crf.core.Sequence;
import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.UncheckedCrfException;
import org.coordinatekit.crf.core.feature.Feature;
import org.coordinatekit.crf.core.feature.FeatureExtractor;
import org.coordinatekit.crf.core.feature.FeatureFormat;
import org.coordinatekit.crf.core.feature.FeaturePositionedToken;
import org.coordinatekit.crf.core.preprocessing.Tokenization;
import org.coordinatekit.crf.core.preprocessing.Tokenizer;
import org.coordinatekit.crf.core.tag.CrfTagger;
import org.coordinatekit.crf.core.tag.TaggedSequence;
import org.coordinatekit.crf.core.tag.TaggedTokenization;
import org.coordinatekit.crf.core.tag.TaggedTokenizations;
import org.coordinatekit.crf.core.util.Serializables;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.io.ObjectInputFilter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * A MALLET-based implementation of {@link CrfTagger} that uses a pre-trained CRF model to tag input
 * sequences.
 *
 * <p>
 * This tagger loads a serialized MALLET {@link CRF} model from disk and uses it to assign tags to
 * tokens in input text. The tagging process involves tokenizing the input, extracting features, and
 * running inference on the CRF model to compute tag probabilities for each token.
 *
 * @param <T> the type of tags assigned to tokens, must be comparable for ordering
 */
@NullMarked
public final class MalletCrfTagger<T extends Comparable<T>> implements CrfTagger<T> {
    private static final ObjectInputFilter MODEL_DESERIALIZATION_FILTER = ObjectInputFilter.Config
            .createFilter("cc.mallet.**;gnu.trove.**;java.**;!*");

    private final FeatureExtractor featureExtractor;
    private final FeatureFormat featureFormat;
    private final CRF model;
    private final TagProvider<T> tagProvider;
    private final Tokenizer tokenizer;

    /**
     * Creates a new tagger by loading a serialized CRF model from the specified path.
     *
     * @param featureExtractor the feature extractor used to generate features from tokens
     * @param featureFormat the format rendering each feature to the alphabet entry the model stores
     * @param modelPath the path to the serialized MALLET CRF model file
     * @param tagProvider the provider that decodes tag names from the model into typed tag values
     * @param tokenizer the tokenizer used to split input text into tokens
     * @throws IOException if the model file cannot be read
     * @throws UncheckedCrfException if the model class cannot be found during deserialization
     */
    public MalletCrfTagger(
            FeatureExtractor featureExtractor,
            FeatureFormat featureFormat,
            Path modelPath,
            TagProvider<T> tagProvider,
            Tokenizer tokenizer
    ) throws IOException {
        this.featureExtractor = featureExtractor;
        this.featureFormat = featureFormat;
        this.model = Serializables.deserialize(CRF.class, modelPath, MODEL_DESERIALIZATION_FILTER);
        this.tagProvider = tagProvider;
        this.tokenizer = tokenizer;
    }

    /**
     * Converts a sequence of feature-positioned tokens into a MALLET feature vector sequence.
     *
     * <p>
     * For each token in the sequence, this method maps features to indices in the CRF's input alphabet
     * and creates corresponding feature vectors. Features not present in the alphabet are filtered out.
     *
     * @param crf the CRF model whose input alphabet is used for the feature index lookup
     * @param featureSequence a sequence of feature-positioned tokens to convert
     * @return a MALLET feature vector sequence corresponding to the input sequence
     */
    private FeatureVectorSequence createMalletSequences(CRF crf, Sequence<FeaturePositionedToken> featureSequence) {
        int sequenceLength = featureSequence.size();
        FeatureVector[] featureVectors = new FeatureVector[sequenceLength];

        for (int i = 0; i < sequenceLength; i++) {
            var tokenFeatures = featureSequence.get(i).features();
            int[] featureIndices = tokenFeatures.stream()
                    .mapToInt(feature -> crf.getInputAlphabet().lookupIndex(featureFormat.render(feature), false))
                    .filter(index -> index >= 0).toArray();
            featureVectors[i] = new FeatureVector(crf.getInputAlphabet(), featureIndices);
        }

        return new FeatureVectorSequence(featureVectors);
    }

    @Override
    public TaggedTokenization<T> tag(String input) {
        Tokenization tokenization = tokenizer.tokenize(input);
        Sequence<FeaturePositionedToken> featureSequence = featureExtractor.extract(tokenization.sequence());
        FeatureVectorSequence malletSequence = createMalletSequences(model, featureSequence);

        var lattice = model.getSumLatticeFactory().newSumLattice(model, malletSequence);
        double logPartition = lattice.getTotalWeight();

        List<String> tokens = new ArrayList<>(featureSequence.size());
        List<Set<Feature>> features = new ArrayList<>(featureSequence.size());
        List<Map<T, Double>> tagScoresByToken = new ArrayList<>();
        for (int i = 0; i < featureSequence.size(); i++) {
            tokens.add(featureSequence.get(i).token());
            features.add(featureSequence.get(i).features());
            Map<T, Double> tagScores = new HashMap<>();
            for (int j = 0; j < model.numStates(); j++) {
                tagScores.put(tagProvider.decode(model.getState(j).getName()), Math.exp(lattice.getGammas()[i + 1][j]));
            }
            tagScoresByToken.add(tagScores);
        }

        return TaggedTokenizations.of(
                new TaggedSequence<>(tokens, features, tagScoresByToken),
                tokenization,
                probabilityFunction(malletSequence, logPartition)
        );
    }

    /**
     * Builds a function for the exact conditional probability {@code P(tags | input)} of an arbitrary
     * tagging of {@code malletSequence}.
     *
     * <p>
     * The function constrains a {@link SumLattice} to the requested tag sequence and divides the
     * resulting log-partition by the unconstrained one: with both in log space,
     * {@code P(y | x) = exp(constrained.getTotalWeight() − logPartition)}. The unconstrained
     * {@code logPartition} is reused from the lattice the surrounding {@link #tag(String)} already
     * built, so the only per-call work is the constrained forward pass. The constrained output is an
     * {@link ArraySequence} of label names because MALLET's CRF transition iterator compares each
     * position's constraint to a state's label as a {@code String}.
     *
     * @param malletSequence the feature-vector sequence the function is bound to
     * @param logPartition the unconstrained log-partition {@code logZ} for that input
     * @return a function over arbitrary taggings of the bound input
     */
    private ToDoubleFunction<List<T>> probabilityFunction(FeatureVectorSequence malletSequence, double logPartition) {
        int length = malletSequence.size();
        return tags -> {
            String[] labelNames = new String[length];
            for (int i = 0; i < length; i++) {
                T tag = tags.get(i);
                labelNames[i] = Objects.requireNonNull(
                        tagProvider.encode(tag),
                        () -> "tag must encode to a non-null label name: " + tag
                );
            }
            SumLattice constrained = model.getSumLatticeFactory()
                    .newSumLattice(model, malletSequence, new ArraySequence<>(labelNames));
            return Math.exp(constrained.getTotalWeight() - logPartition);
        };
    }
}
