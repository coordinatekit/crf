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
package org.coordinatekit.crf.core.feature;

import org.jspecify.annotations.NullMarked;

/**
 * Marks the feature extractor used for model training and tagging — the full feature set.
 *
 * <p>
 * This is typically the windowed extractor (see {@link WindowFeatureExtractor}) that inflates each
 * token's feature set with its neighbors', so it is the complete set the model sees. It feeds the
 * tagger and the annotator's verbose ("all features") view.
 *
 * <p>
 * It exists as a distinct subinterface so that {@link java.util.ServiceLoader} can tell it apart
 * from the {@link KeyFeatureExtractor} when both are registered: plain {@code ServiceLoader} keys
 * discovery on the interface's fully-qualified name and offers no other way to distinguish two
 * providers of the same interface.
 *
 * @see KeyFeatureExtractor
 */
@NullMarked
public interface FullFeatureExtractor extends FeatureExtractor {}
