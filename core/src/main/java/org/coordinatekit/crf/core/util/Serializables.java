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

import java.io.IOException;
import java.io.ObjectInputFilter;
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
 * <code>
 * // Serialize an object to a file
 * Serializables.serialize(myObject, Path.of("data.ser"));
 *
 * // Deserialize an object from a file
 * MyClass restored = Serializables.deserialize(MyClass.class, Path.of("data.ser"));
 * </code>
 * </pre>
 */
@NullMarked
public class Serializables {
    private Serializables() {}

    /**
     * Deserializes an object from a file without an {@link ObjectInputFilter}.
     *
     * <p>
     * No deserialization allowlist is applied beyond any JVM-wide filter configured through
     * {@code -Djdk.serialFilter}. Prefer {@link #deserialize(Class, Path, ObjectInputFilter)} when the
     * file may originate from an untrusted source.
     *
     * @param <T> the type of the object to deserialize
     * @param clazz the class of the object to deserialize
     * @param file the path to the file containing the serialized object
     * @return the deserialized object
     * @throws IOException if an I/O error occurs while reading from the file
     * @throws UncheckedCrfException if the class of the serialized object cannot be found, or if the
     *         deserialized object is not assignable to {@code clazz}
     */
    public static <T> T deserialize(Class<T> clazz, Path file) throws IOException {
        return read(clazz, file, null);
    }

    /**
     * Deserializes an object from a file, applying the supplied JEP-290 {@link ObjectInputFilter}.
     *
     * <p>
     * The filter is installed on the stream before any object is read, so a rejected class fails fast
     * with an {@link java.io.InvalidClassException} (a subtype of {@link IOException}) before its
     * deserialization side effects run. The filter is required so the hardened path cannot silently
     * degrade to an unfiltered read.
     *
     * @param <T> the type of the object to deserialize
     * @param clazz the class of the object to deserialize
     * @param file the path to the file containing the serialized object
     * @param filter the deserialization allowlist to apply to the stream
     * @return the deserialized object
     * @throws IOException if an I/O error occurs while reading from the file, or if the stream contains
     *         a class rejected by {@code filter}
     * @throws UncheckedCrfException if the class of the serialized object cannot be found, or if the
     *         deserialized object is not assignable to {@code clazz}
     */
    public static <T> T deserialize(Class<T> clazz, Path file, ObjectInputFilter filter) throws IOException {
        Objects.requireNonNull(filter, "The filter parameter may not be null.");
        return read(clazz, file, filter);
    }

    private static <T> T read(Class<T> clazz, Path file, @Nullable ObjectInputFilter filter) throws IOException {
        Objects.requireNonNull(clazz, "The clazz parameter may not be null.");
        Objects.requireNonNull(file, "The file parameter may not be null.");

        try (ObjectInputStream s = new ObjectInputStream(Files.newInputStream(file))) {
            if (filter != null) {
                s.setObjectInputFilter(filter);
            }
            Object object = s.readObject();
            if (object != null && !clazz.isInstance(object)) {
                throw new UncheckedCrfException(
                        "Deserialized object of type " + object.getClass().getName() + " is not assignable to "
                                + clazz.getName() + "."
                );
            }
            return clazz.cast(object);
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
