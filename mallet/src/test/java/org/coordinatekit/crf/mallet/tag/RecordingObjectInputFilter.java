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
package org.coordinatekit.crf.mallet.tag;

import org.jspecify.annotations.NullMarked;

import java.io.ObjectInputFilter;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * An {@link ObjectInputFilter} that records every class a deserialization touches while deferring
 * each verdict to {@link MalletCrfTagger#MODEL_DESERIALIZATION_FILTER}.
 *
 * <p>
 * The JDK consults the filter once per class descriptor, superclasses included, and once per array
 * allocation. The recorded names are the classes a native image must list in its serialization
 * configuration for the same models to load. Arrays are named the way the native image agent writes
 * them, {@code int[]} rather than {@code [I}.
 */
@NullMarked
final class RecordingObjectInputFilter implements ObjectInputFilter {
    private final ObjectInputFilter delegate = MalletCrfTagger.MODEL_DESERIALIZATION_FILTER;
    private final SortedSet<String> recorded = new TreeSet<>();

    @Override
    public Status checkInput(FilterInfo filterInfo) {
        Class<?> serialClass = filterInfo.serialClass();
        if (serialClass != null) {
            recorded.add(configurationName(serialClass));
        }
        return delegate.checkInput(filterInfo);
    }

    private static String configurationName(Class<?> serialClass) {
        int dimensions = 0;
        Class<?> base = serialClass;
        while (base.isArray()) {
            base = base.componentType();
            dimensions++;
        }
        return base.getName() + "[]".repeat(dimensions);
    }

    /**
     * Returns the names of the classes recorded so far.
     *
     * @return an unmodifiable, sorted view of the recorded class names
     */
    SortedSet<String> recordedClassNames() {
        return Collections.unmodifiableSortedSet(recorded);
    }
}
