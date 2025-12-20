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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Utility methods for serializing and deserializing objects to and from files.
 *
 * <p>
 * This class provides a simple API for Java object serialization using {@link ObjectOutputStream}
 * and {@link ObjectInputStream}. Objects must implement {@link java.io.Serializable} to be
 * serialized.
 *
 * <p>
 * Example usage:
 *
 * <pre>
 * {@code
 * // Serialize an object to a file
 * Serializables.serialize(myObject, Path.of("data.ser"));
 *
 * // Deserialize an object from a file
 * MyClass restored = Serializables.deserialize(MyClass.class, Path.of("data.ser"));
 * }
 * </pre>
 */
@NullMarked
public class Serializables {
    private Serializables() {}

    /**
     * Deserializes an object from a file.
     *
     * @param <T> the type of the object to deserialize
     * @param clazz the class of the object to deserialize
     * @param file the path to the file containing the serialized object
     * @return the deserialized object
     * @throws IOException if an I/O error occurs while reading from the file
     * @throws UncheckedCrfException if the class of the serialized object cannot be found
     */
    @SuppressWarnings({"unchecked", "unused"})
    public static <T> T deserialize(Class<T> clazz, Path file) throws IOException {
        Objects.requireNonNull(clazz, "The clazz parameter may not be null.");
        Objects.requireNonNull(file, "The file parameter may not be null.");

        try (ObjectInputStream s = new ObjectInputStream(Files.newInputStream(file))) {
            return (T) s.readObject();
        } catch (ClassNotFoundException e) {
            throw new UncheckedCrfException(e);
        }
    }

    /**
     * Serializes an object to a file.
     *
     * @param <T> the type of the object to serialize
     * @param object the object to serialize
     * @param file the path to the file where the object will be written
     * @throws IOException if an I/O error occurs while writing to the file
     */
    public static <T> void serialize(T object, Path file) throws IOException {
        Objects.requireNonNull(object, "The object parameter may not be null.");
        Objects.requireNonNull(file, "The file parameter may not be null.");

        try (ObjectOutputStream s = new ObjectOutputStream(Files.newOutputStream(file))) {
            s.writeObject(object);
        }
    }
}
