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

/**
 * Renders a {@link Feature} to the flat string the model sees, and parses it back.
 *
 * <p>
 * This is the one place in the pipeline that knows the feature-string grammar: positional prefixes,
 * {@code name=value} pairs. Everything upstream works with structured {@link Feature}s; the trainer
 * and tagger call {@link #render(Feature)} at the single edge where features become alphabet
 * entries. It is an SPI resolved through {@code CrfServices} by the precedence
 * {@code explicit > single discovered provider > built-in default}, the default being
 * {@link DefaultFeatureFormat}.
 *
 * @see DefaultFeatureFormat
 * @see Feature
 */
public interface FeatureFormat {
    /**
     * Parses a rendered feature string back into a {@link Feature}.
     *
     * <p>
     * This is the inverse of {@link #render(Feature)}: {@code parse(render(feature)).equals(feature)}.
     *
     * @param rendered the rendered feature string
     * @return the parsed feature
     * @throws NullPointerException if {@code rendered} is null
     */
    Feature parse(String rendered);

    /**
     * Renders a feature to the flat string the model sees.
     *
     * @param feature the feature to render
     * @return the rendered feature string
     * @throws NullPointerException if {@code feature} is null
     */
    String render(Feature feature);
}
