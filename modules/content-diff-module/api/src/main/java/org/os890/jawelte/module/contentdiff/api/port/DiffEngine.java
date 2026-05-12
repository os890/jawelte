/*
 * Copyright 2026 os890
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.os890.jawelte.module.contentdiff.api.port;

import java.util.List;

import org.os890.jawelte.module.contentdiff.api.DiffOptions;
import org.os890.jawelte.module.contentdiff.api.Difference;

/**
 * Pluggable per-content-type diff engine. The single SPI port
 * content-diff-module exposes. Implementations register via
 * {@code META-INF/services/org.os890.jawelte.module.contentdiff.api.port.DiffEngine}
 * and carry a {@code jakarta.annotation.Priority} for ordering
 * (lowest value wins; full class names break ties).
 *
 * <p>Selection: {@code ContentDiff.forJson(...)} / {@code forXml(...)}
 * enumerate every registered engine via
 * {@link java.util.ServiceLoader}, filter by
 * {@link #contentType()}, and hand the filtered list to the active
 * {@link org.os890.jawelte.core.api.port.ServicePriorityResolver}
 * (obtained through {@code TestContext.loadService(...)}) — the head
 * of the priority-sorted list wins. The resolved engine is cached
 * per content type for the JVM lifetime.
 *
 * <p>Implementations must be thread-safe (a single engine instance
 * is shared across every concurrent {@code assertEquals()} call).
 * The built-in JSON and XML engines achieve this by creating their
 * per-call helpers ({@code ObjectMapper}, {@code DocumentBuilder})
 * inside {@link #diff(String, String, DiffOptions)} so they hold no
 * shared mutable state.
 */
public interface DiffEngine {

    /**
     * The MIME type this engine handles (e.g. {@code "application/json"}).
     * Stable for the lifetime of the engine instance — must return
     * the same value on every call.
     *
     * <p>Used as the selection key by {@code ContentDiff.forJson(...)}
     * / {@code forXml(...)}: engines for different content types
     * never compete; engines for the same content type are ranked by
     * {@code @Priority}.
     *
     * @return the MIME type; never {@code null}
     */
    String contentType();

    /**
     * Compute the structural differences between {@code expected} and
     * {@code actual} under the given {@code options}. An empty list
     * means the two documents are equivalent.
     *
     * @param expected the expected document content; never {@code null}
     * @param actual   the actual document content; never {@code null}
     * @param options  the comparison options; never {@code null}
     * @return an immutable list of differences; never {@code null};
     *         possibly empty
     * @throws IllegalArgumentException if {@code expected} or
     *         {@code actual} is malformed for this engine's content
     *         type, or if an entry of
     *         {@link DiffOptions#ignorePatterns()} is malformed
     */
    List<Difference> diff(String expected, String actual, DiffOptions options);
}
