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

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * An immutable, terminal-free description of one sequence screen: the header line, one row per
 * token, an optional feature section, an optional total-likelihood line, and the footer prompt.
 * Cell contents are pre-formatted strings, so a renderer only has to lay them out and a test can
 * assert on them without scraping a terminal.
 *
 * @param headerLine the sequence header shown above the table
 * @param tokenRows the per-token rows, in token order
 * @param featureRows the per-token feature rows, or {@code null} when no feature view is active
 * @param totalLikelihoodText the formatted total-likelihood line shown between the table and the
 *        footer, or {@code null} when no scorer is available (no model)
 * @param footerPrompt the action prompt shown below the table
 */
record TaggingViewModel(
        String headerLine,
        List<TokenRow> tokenRows,
        @Nullable List<FeatureRow> featureRows,
        @Nullable String totalLikelihoodText,
        String footerPrompt
) {
    TaggingViewModel {
        tokenRows = List.copyOf(tokenRows);
        featureRows = featureRows == null ? null : List.copyOf(featureRows);
    }

    /**
     * A feature row: the row number, the token, and its formatted feature list.
     *
     * @param number the one-based row number, as displayed
     * @param token the token string
     * @param featuresText the formatted feature list, or a placeholder when empty
     */
    record FeatureRow(String number, String token, String featuresText) {}

    /**
     * A token row: the row number, the token, its current tag, its confidence, and whether the
     * confidence is below the highlight threshold.
     *
     * @param number the one-based row number, as displayed
     * @param token the token string
     * @param tagText the current tag, as displayed
     * @param confidenceText the formatted confidence, or a placeholder when absent
     * @param lowConfidence whether the row should be highlighted as low-confidence
     */
    record TokenRow(String number, String token, String tagText, String confidenceText, boolean lowConfidence) {}
}
