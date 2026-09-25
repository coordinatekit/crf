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
/**
 * A downstream stand-in that builds the project's libraries into a GraalVM native image.
 *
 * <p>
 * This module is never published. It exists so CI can build and run an image the way a consumer of
 * the {@code crf} libraries would, and is the place where native image metadata gets verified.
 * {@link org.coordinatekit.crf.verification.VerificationLauncher} is its entry point.
 *
 * <p>
 * It also embeds the {@code crf} command line from {@code cli}, so the command metadata is proven
 * in the same image.
 */
@NullMarked
package org.coordinatekit.crf.verification;

import org.jspecify.annotations.NullMarked;
