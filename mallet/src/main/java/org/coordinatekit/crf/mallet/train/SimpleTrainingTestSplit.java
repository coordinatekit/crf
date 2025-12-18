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
package org.coordinatekit.crf.mallet.train;

import cc.mallet.types.Alphabet;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelAlphabet;

/**
 * A container for training and test data splits.
 *
 * <p>
 * This record holds the results of splitting a dataset into training and test sets. Both
 * {@link InstanceList}s share the same {@link Alphabet} for features and {@link LabelAlphabet} for
 * labels, ensuring consistent encoding across the split.
 *
 * @param training the instances to use for model training
 * @param test the instances to use for model evaluation; may be empty if
 *        {@link MalletCrfTrainerConfiguration#trainingFraction()} is 1.0
 */
public record SimpleTrainingTestSplit(InstanceList training, InstanceList test) implements TrainingTestSplit {}
