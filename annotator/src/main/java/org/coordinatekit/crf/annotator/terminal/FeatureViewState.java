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
package org.coordinatekit.crf.annotator.terminal;

import java.util.Objects;

/**
 * The sticky {@link FeatureView} carried across the sequences a single tagging interface presents.
 *
 * <p>
 * Feature availability is fixed per batch, but the chosen view persists from one sequence to the
 * next, so this state outlives any single {@code present} call. It is deliberately mutable and not
 * thread-safe: a tagging interface presents sequences one at a time on a single thread, and one
 * instance must not be shared across concurrent sessions while a view is live.
 */
final class FeatureViewState {
    private FeatureView featureView = FeatureView.NONE;

    /**
     * Returns the currently selected view.
     *
     * @return the current view
     */
    FeatureView featureView() {
        return featureView;
    }

    /**
     * Selects {@code featureView} as the current view.
     *
     * @param featureView the view to select, must not be null
     */
    void featureView(FeatureView featureView) {
        this.featureView = Objects.requireNonNull(featureView, "featureView must not be null");
    }
}
