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
package org.coordinatekit.crf.core.util;

import org.coordinatekit.crf.core.UncheckedCrfException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@NullMarked
class SerializablesTest {
    private static final byte[] CLASS_NOT_FOUND_SERIALIZATION = {(byte) 0xAC, (byte) 0xED, // STREAM_MAGIC
                    0x00, 0x05, // STREAM_VERSION
                    0x73, // TC_OBJECT
                    0x72, // TC_CLASSDESC
                    0x00, 0x10, // class name length (16)
                    'c', 'o', 'm', '.', 'f', 'a', 'k', 'e', '.', 'M', 'i', 's', 's', 'i', 'n', 'g', 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, // serialVersionUID
                    0x02, // flags: SC_SERIALIZABLE
                    0x00, 0x00, // field count
                    0x78, // TC_ENDBLOCKDATA
                    0x70 // TC_NULL (no superclass)
    };

    @TempDir
    static Path temporaryDirectory;

    @BeforeAll
    static void setup() throws IOException {
        Files.createDirectories(temporaryDirectory.resolve("empty"));
        Files.write(temporaryDirectory.resolve("classNotFound.ser"), CLASS_NOT_FOUND_SERIALIZATION);
        Files.writeString(temporaryDirectory.resolve("file.txt"), "The quick brown fox jumps over the lazy dog.");
        Serializables.serialize(new TestObject("hello", 42), temporaryDirectory.resolve("testObject.ser"));
    }

    record DeserializeExceptionParameters(
            @Nullable Class<?> clazz,
            @Nullable Path file,
            Class<? extends Exception> expectedClass,
            @Nullable String expectedMessage
    ) {}

    static Stream<DeserializeExceptionParameters> deserialize_exception() {
        return Stream.of(
                new DeserializeExceptionParameters(
                        null,
                        Path.of("testObject.ser"),
                        NullPointerException.class,
                        "The clazz parameter may not be null."
                ),
                new DeserializeExceptionParameters(
                        Object.class,
                        null,
                        NullPointerException.class,
                        "The file parameter may not be null."
                ),
                new DeserializeExceptionParameters(Object.class, Path.of("nonexistent.ser"), IOException.class, null),
                new DeserializeExceptionParameters(Object.class, Path.of("empty"), IOException.class, null),
                new DeserializeExceptionParameters(
                        Object.class,
                        Path.of("classNotFound.ser"),
                        UncheckedCrfException.class,
                        null
                ),
                new DeserializeExceptionParameters(Object.class, Path.of("file.txt"), ObjectStreamException.class, null)
        );
    }

    @MethodSource
    @ParameterizedTest
    void deserialize_exception(DeserializeExceptionParameters parameters) {
        var file = parameters.file() != null ? temporaryDirectory.resolve(parameters.file()) : null;

        @SuppressWarnings("DataFlowIssue")
        Exception exception = assertThrows(
                parameters.expectedClass(),
                () -> Serializables.deserialize(parameters.clazz(), file)
        );

        if (parameters.expectedMessage() != null) {
            assertEquals(parameters.expectedMessage(), exception.getMessage());
        }
    }

    record SerializeExceptionParameters(
            @Nullable Object object,
            @Nullable Path file,
            Class<? extends Exception> expectedClass,
            @Nullable String expectedMessage
    ) {}

    static Stream<SerializeExceptionParameters> serialize_exception() {
        return Stream.of(
                new SerializeExceptionParameters(
                        null,
                        Path.of("testObject.ser"),
                        NullPointerException.class,
                        "The object parameter may not be null."
                ),
                new SerializeExceptionParameters(
                        Object.class,
                        null,
                        NullPointerException.class,
                        "The file parameter may not be null."
                ),
                new SerializeExceptionParameters(
                        new Object(),
                        Path.of("nonexistent.ser"),
                        ObjectStreamException.class,
                        null
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void serialize_exception(SerializeExceptionParameters parameters) {
        var file = parameters.file() != null ? temporaryDirectory.resolve(parameters.file()) : null;

        @SuppressWarnings("DataFlowIssue")
        Exception exception = assertThrows(
                parameters.expectedClass(),
                () -> Serializables.serialize(parameters.object(), file)
        );

        if (parameters.expectedMessage() != null) {
            assertEquals(parameters.expectedMessage(), exception.getMessage());
        }
    }

    @Test
    void serialize_overwritesExistingFile() throws Exception {
        var file = temporaryDirectory.resolve("overwrite.ser");

        Serializables.serialize(new TestObject("first", 1), file);
        Serializables.serialize(new TestObject("second", 2), file);

        var restored = Serializables.deserialize(TestObject.class, file);

        assertEquals(new TestObject("second", 2), restored);
    }

    @Test
    void serialize_roundTrip() throws Exception {
        var original = new TestObject("hello", 42);
        var file = temporaryDirectory.resolve("test.ser");

        Serializables.serialize(original, file);
        var restored = Serializables.deserialize(TestObject.class, file);

        assertEquals(original, restored);
    }

    static class TestObject implements Serializable {
        private final String name;
        private final int value;

        TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof TestObject that))
                return false;
            return value == that.value && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }
    }
}
