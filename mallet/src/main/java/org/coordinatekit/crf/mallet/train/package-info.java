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
/**
 * MALLET-based CRF training implementation.
 *
 * <p>
 * This package provides the MALLET-based implementation for training CRF models using the MALLET
 * (MAchine Learning for LanguagE Toolkit) library.
 *
 * <h2>Example Usage</h2>
 *
 * <pre>
 * <code>
 * // Configure model checkpoint output
 * ModelOutputConfiguration modelOutput = ModelOutputConfiguration.builder()
 *         .outputDirectory(Path.of("checkpoints"))
 *         .iterationInterval(50)
 *         .build();
 *
 * // Configure CoNLL evaluation output
 * ConllOutputConfiguration conllOutput = ConllOutputConfiguration.builder()
 *         .outputDirectory(Path.of("evaluations"))
 *         .iterationInterval(50)
 *         .build();
 *
 * // Configure training parameters
 * MalletCrfTrainerConfiguration config = MalletCrfTrainerConfiguration.builder()
 *         .iterations(500)
 *         .gaussianVariance(10.0)
 *         .threads(8)
 *         .trainingFraction(0.8)
 *         .modelOutputConfiguration(modelOutput)
 *         .conllOutputConfiguration(conllOutput)
 *         .build();
 *
 * // Create the trainer
 * MalletCrfTrainer&lt;String, MyTag&gt; trainer = new MalletCrfTrainer&lt;&gt;(
 *         featureExtractor,
 *         tagProvider,
 *         trainingDataSequencer,
 *         config);
 *
 * // Train and save the model
 * trainer.train(Path.of("training-data.xml"), Path.of("model.ser"));
 * </code>
 * </pre>
 *
 * <h2>Configuration Options</h2>
 *
 * <p>
 * Key parameters in {@link MalletCrfTrainerConfiguration}:
 *
 * <ul>
 * <li>{@code iterations} - Maximum training iterations (default: 500)
 * <li>{@code gaussianVariance} - L2 regularization strength; higher values = less regularization
 * (default: 10.0)
 * <li>{@code threads} - Number of threads for parallel training (default: 6)
 * <li>{@code trainingFraction} - Fraction of data for training vs. testing (default: 0.5)
 * <li>{@code weightsType} - Memory/speed trade-off for weight storage (default: SOME_DENSE)
 * </ul>
 *
 * <p>
 * Parameters in {@link ModelOutputConfiguration} for saving model checkpoints:
 *
 * <ul>
 * <li>{@code outputDirectory} - Directory for checkpoint files
 * <li>{@code iterationInterval} - Save model every N iterations (default: 10)
 * <li>{@code filePrefix} - Prefix for checkpoint filenames (default: "model_")
 * <li>{@code fileSuffix} - Suffix/extension for checkpoint files (default: ".ser")
 * </ul>
 *
 * <p>
 * Parameters in {@link ConllOutputConfiguration} for CoNLL-format evaluation output:
 *
 * <ul>
 * <li>{@code outputDirectory} - Directory for CoNLL output files
 * <li>{@code iterationInterval} - Write predictions every N iterations (default: 10)
 * <li>{@code filePrefix} - Prefix for output filenames (default: "output_iter")
 * <li>{@code fileSuffix} - Suffix/extension for output files (default: ".conll")
 * </ul>
 *
 * @see org.coordinatekit.crf.mallet.train.MalletCrfTrainer
 * @see org.coordinatekit.crf.mallet.train.MalletCrfTrainerConfiguration
 * @see org.coordinatekit.crf.core.train.CrfTrainer
 */
@NullMarked
package org.coordinatekit.crf.mallet.train;

import org.jspecify.annotations.NullMarked;
