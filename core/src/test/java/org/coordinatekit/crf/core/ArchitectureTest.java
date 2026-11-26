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
package org.coordinatekit.crf.core;

import org.coordinatekit.crf.core.feature.configuration.FeatureConfiguration;
import org.coordinatekit.foundation.architecture.FoundationArchitecture;
import org.coordinatekit.foundation.architecture.Rule;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
class ArchitectureTest {
    @Test
    void architecture__followsConventions() {
        FoundationArchitecture.builder(ArchitectureTest.class)
                .enable(Rule.PACKAGE_CYCLES)
                .exclude(Rule.PACKAGE_CYCLES, FeatureConfiguration.class)
                .build()
                .check();
    }
}
