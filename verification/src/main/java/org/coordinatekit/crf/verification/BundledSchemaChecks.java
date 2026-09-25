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
package org.coordinatekit.crf.verification;

import org.coordinatekit.crf.core.StringTagProvider;
import org.coordinatekit.crf.core.feature.configuration.FeatureConfiguration;
import org.coordinatekit.crf.core.io.XmlTrainingData;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Exercises the XSDs bundled in the {@code core} jar, each read through
 * {@code Class.getResourceAsStream}, which returns {@code null} in a native image unless
 * {@code core}'s {@code resource-config.json} names the file.
 *
 * <p>
 * The documents are string constants rather than module resources, so the checks prove
 * {@code core}'s metadata and do not also depend on any of this module's own.
 */
final class BundledSchemaChecks {
    // language=XML
    private static final String FEATURE_CONFIGURATION = """
            <?xml version="1.0" encoding="UTF-8"?>
            <featureExtractors xmlns="https://coordinatekit.org/schema/crf/feature-configuration">
                <extractor type="length"/>
            </featureExtractors>
            """;

    // language=XML
    private static final String TRAINING_DATA = """
            <?xml version="1.0" encoding="UTF-8"?>
            <crf:Collection xmlns:crf="https://coordinatekit.org/schema/crf/training-data">
                <crf:Sequence><Adjective>Brown</Adjective><crf:Excluded> </crf:Excluded><Noun>Fox</Noun></crf:Sequence>
            </crf:Collection>
            """;

    private BundledSchemaChecks() {}

    /**
     * Loads a feature configuration and validates a training document, printing a line per check.
     *
     * @throws IOException if the temporary configuration file cannot be written
     */
    static void run() throws IOException {
        Path configuration = Files.createTempFile("feature-configuration", ".xml");
        try {
            Files.writeString(configuration, FEATURE_CONFIGURATION, StandardCharsets.UTF_8);
            FeatureConfiguration.load(configuration);
        } finally {
            Files.deleteIfExists(configuration);
        }
        System.out.println("feature-configuration.xsd -> ok");

        new XmlTrainingData<>(new StringTagProvider(List.of("Adjective", "Noun"), "Adjective"))
                .validate(new ByteArrayInputStream(TRAINING_DATA.getBytes(StandardCharsets.UTF_8)));
        System.out.println("training-data.xsd -> ok");
    }
}
