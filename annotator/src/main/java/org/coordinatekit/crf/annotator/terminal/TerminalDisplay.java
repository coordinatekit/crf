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

/**
 * Display constants shared across the view-model builder and the screen renderers. Centralizing
 * them keeps values that must agree in one place: {@link #FEATURE_SEPARATOR} in particular is both
 * the string features are joined with and the boundary the features column is allowed to wrap on,
 * so the join and the wrap must use the same value.
 */
final class TerminalDisplay {
    static final String CONFIDENCE_COLUMN = "Confidence";
    static final String FEATURE_SEPARATOR = ", ";
    static final String NULL_VALUE_PLACEHOLDER = "—";
    static final String NUMBER_COLUMN = "##";
    static final String TAG_COLUMN = "Tag";
    static final String TOTAL_LIKELIHOOD_LABEL = "Total likelihood: ";

    private TerminalDisplay() {}
}
