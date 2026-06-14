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
package org.coordinatekit.crf.annotator;

/**
 * Which display-feature sources an {@link AnnotatorSequence} carries: the four reachable
 * combinations of key features and verbose features.
 *
 * <p>
 * This captures what a sequence <em>has</em>; it is distinct from the user-interface's notion of
 * what is currently <em>shown</em>. Key features back the key-feature view and verbose features
 * back the all-features view, so a sequence with {@link #NONE} offers no feature views at all.
 */
public enum FeatureAvailability {
    /** Neither key nor verbose display features are available. */
    NONE,

    /** Only key display features are available. */
    KEY_ONLY,

    /** Only verbose display features are available. */
    VERBOSE_ONLY,

    /** Both key and verbose display features are available. */
    BOTH;

    /**
     * Returns the availability matching the two feature sources.
     *
     * @param keyAvailable whether key display features are available
     * @param verboseAvailable whether verbose display features are available
     * @return the matching availability
     */
    static FeatureAvailability of(boolean keyAvailable, boolean verboseAvailable) {
        if (keyAvailable && verboseAvailable) {
            return BOTH;
        }
        if (keyAvailable) {
            return KEY_ONLY;
        }
        if (verboseAvailable) {
            return VERBOSE_ONLY;
        }
        return NONE;
    }

    /**
     * Returns whether key display features are available, enabling the key-feature view.
     *
     * @return {@code true} for {@link #KEY_ONLY} and {@link #BOTH}
     */
    public boolean keyAvailable() {
        return this == KEY_ONLY || this == BOTH;
    }

    /**
     * Returns whether verbose display features are available, enabling the all-features view.
     *
     * @return {@code true} for {@link #VERBOSE_ONLY} and {@link #BOTH}
     */
    public boolean verboseAvailable() {
        return this == VERBOSE_ONLY || this == BOTH;
    }
}
