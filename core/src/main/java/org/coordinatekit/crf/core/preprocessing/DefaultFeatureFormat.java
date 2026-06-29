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

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The built-in {@link FeatureFormat}, reproducing the historical feature-string grammar.
 *
 * <p>
 * A feature renders as an optional positional prefix, then its name, then its value:
 *
 * <ul>
 * <li>prefix {@code PREV_<n>__} for offset {@code -n}, {@code NEXT_<n>__} for offset {@code +n},
 * and nothing for offset {@code 0};
 * <li>the name verbatim;
 * <li>{@code =<value>} when the value is non-null. A {@code null} value renders as a bare name
 * ({@code NAME}), an empty value as a trailing {@code =} ({@code NAME=}).
 * </ul>
 *
 * <p>
 * {@link #parse(String)} is the inverse: it strips the optional prefix, then splits the remainder
 * on the <em>first</em> {@code =}, so a value may itself contain {@code =}. No casing or cleaning
 * is applied in either direction.
 *
 * @see FeatureFormat
 */
public class DefaultFeatureFormat implements FeatureFormat {
    private static final String NEXT_PREFIX = "NEXT_";
    private static final String PREV_PREFIX = "PREV_";
    private static final Pattern PREFIX_PATTERN = Pattern.compile("^(PREV|NEXT)_(\\d+)__");
    private static final String SUFFIX = "__";

    /** Creates a new default feature format. */
    public DefaultFeatureFormat() {}

    @Override
    public Feature parse(String rendered) {
        Objects.requireNonNull(rendered, "rendered must not be null");

        int offset = 0;
        String remainder = rendered;
        Matcher matcher = PREFIX_PATTERN.matcher(rendered);
        if (matcher.find()) {
            int magnitude = Integer.parseInt(matcher.group(2));
            offset = matcher.group(1).equals("PREV") ? -magnitude : magnitude;
            remainder = rendered.substring(matcher.end());
        }

        int separator = remainder.indexOf('=');
        if (separator < 0) {
            return Features.of(remainder).withOffset(offset);
        }
        return Features.of(remainder.substring(0, separator), remainder.substring(separator + 1)).withOffset(offset);
    }

    @Override
    public String render(Feature feature) {
        Objects.requireNonNull(feature, "feature must not be null");

        String name = feature.name();
        if (name.indexOf('=') >= 0) {
            throw new IllegalArgumentException("feature name must not contain '=', got: " + name);
        }
        if (PREFIX_PATTERN.matcher(name).find()) {
            throw new IllegalArgumentException(
                    "feature name must not match a positional prefix (PREV_<n>__/NEXT_<n>__), got: " + name
            );
        }

        StringBuilder rendered = new StringBuilder();
        int offset = feature.offset();
        if (offset < 0) {
            rendered.append(PREV_PREFIX).append(-offset).append(SUFFIX);
        } else if (offset > 0) {
            rendered.append(NEXT_PREFIX).append(offset).append(SUFFIX);
        }
        rendered.append(name);

        String value = feature.value();
        if (value != null) {
            rendered.append('=').append(value);
        }
        return rendered.toString();
    }
}
