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
package org.coordinatekit.crf.core.train;

import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Interface for training Conditional Random Field (CRF) models.
 *
 * <p>
 * Implementations of this interface handle the training of CRF models using various underlying
 * libraries. The training process reads annotated training data, extracts features, and produces a
 * serialized model that can be used for sequence labeling tasks.
 */
@NullMarked
public interface CrfTrainer {
    /**
     * Trains a CRF model using the training data at the specified path and saves the model to the
     * specified output path.
     *
     * @param trainingPath the path to the training data file
     * @param modelPath the path where the trained model should be saved
     * @throws IOException if an error occurs during training or model serialization
     */
    void train(Path trainingPath, Path modelPath) throws IOException;
}
