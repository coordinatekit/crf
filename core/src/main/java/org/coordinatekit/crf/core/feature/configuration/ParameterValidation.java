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

import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Validates a node's raw string parameters against its factory's declared
 * {@link FeatureExtractorFactory#parameters() parameters} and coerces them into a
 * {@link FeatureExtractorParameters}.
 *
 * <p>
 * It rejects a name the factory never declared (offering a {@code did you mean} hint when a
 * declared name is close), requires the required parameters, coerces each value to its declared
 * {@link ParameterKind kind} (parsing integers within their declared
 * {@link ParameterDescriptor#minimumValue() minimum}/{@link ParameterDescriptor#maximumValue()
 * maximum}, checking an enumeration against its allowed values, resolving a path against
 * {@link AssemblyContext#baseDirectory()} and verifying it names a readable regular file), and
 * applies a default for an absent optional parameter that declares one. Every rejection is a
 * located {@link FeatureConfigurationException}.
 */
final class ParameterValidation {
    /**
     * The largest edit distance at which a mistyped parameter name still earns a {@code did you mean}
     * hint.
     */
    private static final int SUGGESTION_DISTANCE = 2;

    private ParameterValidation() {
        throw new UnsupportedOperationException("ParameterValidation is a utility class");
    }

    /**
     * Validates and coerces {@code rawParameters} against {@code parameters}.
     *
     * @param extractorType the factory type, named in located error messages
     * @param rawParameters the raw, unvalidated parameters keyed by name
     * @param parameters the declared parameters to validate against
     * @param context the assembly context, supplying the location and the base directory for paths
     * @return the validated, coerced parameters
     * @throws FeatureConfigurationException if a parameter is unknown, a required one is missing, or a
     *         value cannot be coerced to its declared kind
     */
    static FeatureExtractorParameters validate(
            String extractorType,
            Map<String, String> rawParameters,
            Set<ParameterDescriptor> parameters,
            AssemblyContext context
    ) {
        List<ParameterDescriptor> ordered = parameters.stream().sorted(Comparator.comparing(ParameterDescriptor::name))
                .toList();
        for (String name : rawParameters.keySet()) {
            if (lookup(parameters, name) == null) {
                throw new FeatureConfigurationException(
                        extractorType,
                        context.location(),
                        "unknown parameter '" + name + "'" + suggestion(ordered, name)
                );
            }
        }

        Map<String, Object> coerced = new LinkedHashMap<>();
        for (ParameterDescriptor parameter : ordered) {
            String raw = rawParameters.get(parameter.name());
            String defaultValue = parameter.defaultValue();
            if (raw != null) {
                coerced.put(parameter.name(), coerce(extractorType, context, parameter, raw));
            } else if (parameter.required()) {
                throw new FeatureConfigurationException(
                        extractorType,
                        context.location(),
                        "missing required parameter '" + parameter.name() + "'"
                );
            } else if (defaultValue != null) {
                coerced.put(parameter.name(), coerce(extractorType, context, parameter, defaultValue));
            }
        }
        return new DefaultFeatureExtractorParameters(parameters, Map.copyOf(coerced));
    }

    /**
     * Coerces one raw value to the Java type its declared kind maps to.
     *
     * @param extractorType the factory type, named in located error messages
     * @param context the assembly context, supplying the location and the base directory for paths
     * @param parameter the descriptor of the parameter being coerced
     * @param raw the raw value
     * @return the coerced value
     * @throws FeatureConfigurationException if the value cannot be coerced to the declared kind
     */
    private static Object coerce(
            String extractorType,
            AssemblyContext context,
            ParameterDescriptor parameter,
            String raw
    ) {
        return switch (parameter.kind()) {
            case BOOLEAN -> coerceBoolean(extractorType, context, parameter, raw);
            case ENUMERATION -> coerceEnumeration(extractorType, context, parameter, raw);
            case INTEGER -> coerceInteger(extractorType, context, parameter, raw);
            case PATH -> coercePath(extractorType, context, parameter, raw);
            case STRING -> raw;
        };
    }

    private static Boolean coerceBoolean(
            String extractorType,
            AssemblyContext context,
            ParameterDescriptor parameter,
            String raw
    ) {
        if (raw.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (raw.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        throw new FeatureConfigurationException(
                extractorType,
                context.location(),
                "parameter '" + parameter.name() + "' expects a boolean (true or false) but got '" + raw + "'"
        );
    }

    private static String coerceEnumeration(
            String extractorType,
            AssemblyContext context,
            ParameterDescriptor parameter,
            String raw
    ) {
        if (parameter.allowedValues().contains(raw)) {
            return raw;
        }
        throw new FeatureConfigurationException(
                extractorType,
                context.location(),
                "parameter '" + parameter.name() + "' expects one of " + new TreeSet<>(parameter.allowedValues())
                        + " but got '" + raw + "'"
        );
    }

    private static Integer coerceInteger(
            String extractorType,
            AssemblyContext context,
            ParameterDescriptor parameter,
            String raw
    ) {
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            throw new FeatureConfigurationException(
                    extractorType,
                    context.location(),
                    "parameter '" + parameter.name() + "' expects an integer but got '" + raw + "'"
            );
        }
        if (value < parameter.minimumValue() || value > parameter.maximumValue()) {
            throw new FeatureConfigurationException(
                    extractorType,
                    context.location(),
                    "parameter '" + parameter.name() + "' expects an integer "
                            + rangeDescription(parameter.minimumValue(), parameter.maximumValue()) + " but got '" + raw
                            + "'"
            );
        }
        return value;
    }

    /**
     * Coerces one raw {@link ParameterKind#PATH} value, resolving it against
     * {@link AssemblyContext#baseDirectory()} and verifying it names a readable regular file.
     *
     * @param extractorType the factory type, named in located error messages
     * @param context the assembly context, supplying the location and the base directory
     * @param parameter the descriptor of the parameter being coerced
     * @param raw the raw value
     * @return the resolved path
     * @throws FeatureConfigurationException if the resolved path does not exist, is not a regular file,
     *         or is not readable
     */
    private static Path coercePath(
            String extractorType,
            AssemblyContext context,
            ParameterDescriptor parameter,
            String raw
    ) {
        Path resolved = context.baseDirectory().resolve(raw);
        if (!Files.exists(resolved)) {
            throw new FeatureConfigurationException(
                    extractorType,
                    context.location(),
                    "parameter '" + parameter.name() + "' points at a file that does not exist: " + resolved
            );
        }
        if (!Files.isRegularFile(resolved)) {
            throw new FeatureConfigurationException(
                    extractorType,
                    context.location(),
                    "parameter '" + parameter.name() + "' points at a path that is not a regular file: " + resolved
            );
        }
        if (!Files.isReadable(resolved)) {
            throw new FeatureConfigurationException(
                    extractorType,
                    context.location(),
                    "parameter '" + parameter.name() + "' points at a file that is not readable: " + resolved
            );
        }
        return resolved;
    }

    /**
     * Returns the standard Levenshtein edit distance between two strings.
     *
     * @param left the first string
     * @param right the second string
     * @return the edit distance
     */
    private static int editDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int column = 0; column <= right.length(); column++) {
            previous[column] = column;
        }
        for (int row = 1; row <= left.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= right.length(); column++) {
                int substitutionCost = left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(
                        Math.min(current[column - 1] + 1, previous[column] + 1),
                        previous[column - 1] + substitutionCost
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    /**
     * Returns the descriptor of the parameter named {@code name} in {@code parameters}, or {@code null}
     * if none is declared.
     *
     * @param parameters the declared parameters
     * @param name the parameter name
     * @return the parameter descriptor, or {@code null}
     */
    private static @Nullable ParameterDescriptor lookup(Set<ParameterDescriptor> parameters, String name) {
        for (ParameterDescriptor parameter : parameters) {
            if (parameter.name().equals(name)) {
                return parameter;
            }
        }
        return null;
    }

    /**
     * Describes the inclusive bounds {@code [minimum, maximum]} in a form fit for an error message, for
     * example {@code between 1 and 5}, {@code >= 0}, {@code <= 10}, or {@code any value} when neither
     * bound is set.
     *
     * @param minimum the minimum value, or {@link Integer#MIN_VALUE} when unbounded below
     * @param maximum the maximum value, or {@link Integer#MAX_VALUE} when unbounded above
     * @return the human-readable range description
     */
    static String rangeDescription(int minimum, int maximum) {
        boolean hasMinimum = minimum != Integer.MIN_VALUE;
        boolean hasMaximum = maximum != Integer.MAX_VALUE;
        if (hasMinimum && hasMaximum) {
            return "between " + minimum + " and " + maximum;
        }
        if (hasMinimum) {
            return ">= " + minimum;
        }
        if (hasMaximum) {
            return "<= " + maximum;
        }
        return "any value";
    }

    /**
     * Returns a {@code did you mean} hint naming the declared parameter closest to a mistyped
     * {@code name}, or the empty string when none is within {@link #SUGGESTION_DISTANCE}.
     *
     * @param parameters the declared parameters
     * @param name the unknown parameter name
     * @return a hint such as {@code  (did you mean 'before'?)}, or the empty string
     */
    private static String suggestion(List<ParameterDescriptor> parameters, String name) {
        String lowerName = name.toLowerCase(Locale.ROOT);
        String closest = null;
        int closestDistance = Integer.MAX_VALUE;
        for (ParameterDescriptor parameter : parameters) {
            int distance = editDistance(lowerName, parameter.name().toLowerCase(Locale.ROOT));
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = parameter.name();
            }
        }
        if (closest == null || closestDistance > SUGGESTION_DISTANCE) {
            return "";
        }
        return " (did you mean '" + closest + "'?)";
    }
}
