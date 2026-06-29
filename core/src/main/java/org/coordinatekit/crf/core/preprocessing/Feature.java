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

import org.jspecify.annotations.Nullable;

/**
 * A single extracted feature as structured data.
 *
 * <p>
 * A feature is a {@link #name()}, an optional {@link #value()}, and an {@link #offset()} describing
 * the window position it was extracted from relative to the current token: {@code 0} for the
 * current token, a negative offset for a preceding token, a positive offset for a following token.
 * The grammar that turns a feature into the model's flat feature string (positional prefixes,
 * {@code name=value} pairs) lives in {@link FeatureFormat}, not here; this type carries the parts
 * as data so the pipeline never touches strings.
 *
 * @see Features
 * @see FeatureFormat
 */
public interface Feature {
    /**
     * Returns the window offset this feature was extracted from, relative to the current token.
     *
     * <p>
     * {@code 0} is the current token, a negative offset a preceding token, a positive offset a
     * following token.
     *
     * @return the window offset
     */
    int offset();

    /**
     * Returns the feature name.
     *
     * @return the feature name, never {@code null}
     */
    String name();

    /**
     * Returns the feature value, or {@code null} when the feature is a bare name with no value.
     *
     * @return the feature value, or {@code null}
     */
    @Nullable
    String value();

    /**
     * Returns a copy of this feature with its offset replaced by {@code offset}.
     *
     * <p>
     * The offset is replaced, not accumulated: applying this to a feature that already carries an
     * offset discards the old offset.
     *
     * @param offset the new window offset
     * @return a feature equal to this one but with the given offset
     */
    Feature withOffset(int offset);
}
