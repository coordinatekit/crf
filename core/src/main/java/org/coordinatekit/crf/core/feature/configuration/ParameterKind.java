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
package org.coordinatekit.crf.core.feature.configuration;

/**
 * The type a {@link ParameterDescriptor declared parameter} coerces its raw string value to.
 *
 * <p>
 * A configuration carries every value as a string; the kind tells {@link FeatureExtractorParameters
 * validated access} how to interpret it and which accessor pair a factory must use.
 */
public enum ParameterKind {
    /**
     * A boolean, spelled {@code true} or {@code false} (case-insensitive), read with
     * {@link FeatureExtractorParameters#getBoolean(String)} /
     * {@link FeatureExtractorParameters#findBoolean(String)}.
     */
    BOOLEAN,

    /**
     * One of a fixed set of {@link ParameterDescriptor#allowedValues() allowed} strings, read with
     * {@link FeatureExtractorParameters#getEnumeration(String)} /
     * {@link FeatureExtractorParameters#findEnumeration(String)}.
     */
    ENUMERATION,

    /**
     * An integer, read with {@link FeatureExtractorParameters#getInteger(String)} /
     * {@link FeatureExtractorParameters#findInteger(String)}.
     */
    INTEGER,

    /**
     * A filesystem path, resolved against {@link AssemblyContext#baseDirectory()} and read with
     * {@link FeatureExtractorParameters#getPath(String)} /
     * {@link FeatureExtractorParameters#findPath(String)}.
     */
    PATH,

    /**
     * An uninterpreted string, read with {@link FeatureExtractorParameters#getString(String)} /
     * {@link FeatureExtractorParameters#findString(String)}.
     */
    STRING
}
