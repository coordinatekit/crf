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
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import org.coordinatekit.crf.core.Sequence;
import org.coordinatekit.crf.core.TagProvider;
import org.coordinatekit.crf.core.UncheckedCrfException;
import org.coordinatekit.crf.core.preprocessing.FeatureExtractor;
import org.coordinatekit.crf.core.preprocessing.FeaturePositionedToken;
import org.coordinatekit.crf.core.preprocessing.Tokenizer;
import org.coordinatekit.crf.core.tag.CrfTagger;
import org.coordinatekit.crf.core.tag.TaggedPositionedToken;
import org.coordinatekit.crf.core.tag.TaggedSequence;
import org.coordinatekit.crf.core.util.Serializables;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A MALLET-based implementation of {@link CrfTagger} that uses a pre-trained CRF model to tag input
 * sequences.
 *
 * <p>
 * This tagger loads a serialized MALLET {@link CRF} model from disk and uses it to assign tags to
 * tokens in input text. The tagging process involves tokenizing the input, extracting features, and
 * running inference on the CRF model to compute tag probabilities for each token.
 *
 * @param <F> the type of features extracted from tokens
 * @param <T> the type of tags assigned to tokens, must be comparable for ordering
 */
@NullMarked
public class MalletCrfTagger<F, T extends Comparable<T>> implements CrfTagger<F, T> {
    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(MalletCrfTagger.class);

    private final FeatureExtractor<F> featureExtractor;
    private final CRF model;
    private final TagProvider<T> tagProvider;
    private final Tokenizer tokenizer;

    /**
     * Creates a new tagger by loading a serialized CRF model from the specified path.
     *
     * @param featureExtractor the feature extractor used to generate features from tokens
     * @param modelPath the path to the serialized MALLET CRF model file
     * @param tagProvider the provider that decodes tag names from the model into typed tag values
     * @param tokenizer the tokenizer used to split input text into tokens
     * @throws IOException if the model file cannot be read
     * @throws UncheckedCrfException if the model class cannot be found during deserialization
     */
    public MalletCrfTagger(
            FeatureExtractor<F> featureExtractor,
            Path modelPath,
            TagProvider<T> tagProvider,
            Tokenizer tokenizer
    ) throws IOException {
        this.featureExtractor = featureExtractor;
        this.model = Serializables.deserialize(CRF.class, modelPath);
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
    protected FeatureVectorSequence createMalletSequences(
            CRF crf,
            Sequence<FeaturePositionedToken<F>> featureSequence
    ) {
        int sequenceLength = featureSequence.size();
        FeatureVector[] featureVectors = new FeatureVector[sequenceLength];

        for (int i = 0; i < sequenceLength; i++) {
            var tokenFeatures = featureSequence.get(i).features();
            int[] featureIndices = tokenFeatures.stream().mapToInt(f -> crf.getInputAlphabet().lookupIndex(f, false))
                    .filter(index -> index >= 0).toArray();
            featureVectors[i] = new FeatureVector(crf.getInputAlphabet(), featureIndices);
        }

        return new FeatureVectorSequence(featureVectors);
    }

    @Override
    public Sequence<TaggedPositionedToken<F, T>> tag(String input) {
        Sequence<FeaturePositionedToken<F>> featureSequence = featureExtractor.extract(tokenizer.tokenize(input));
        FeatureVectorSequence malletSequence = createMalletSequences(model, featureSequence);

        var lattice = model.getSumLatticeFactory().newSumLattice(model, malletSequence);

        List<String> tokens = new ArrayList<>(featureSequence.size());
        List<Set<F>> features = new ArrayList<>(featureSequence.size());
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

        return new TaggedSequence<>(tokens, features, tagScoresByToken);
    }
}
