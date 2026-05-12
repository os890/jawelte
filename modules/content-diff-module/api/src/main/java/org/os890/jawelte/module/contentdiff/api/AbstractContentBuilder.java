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
package org.os890.jawelte.module.contentdiff.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.os890.jawelte.module.contentdiff.api.port.DiffEngine;

/**
 * Package-private shared base for {@link JsonBuilder} and
 * {@link XmlBuilder}. Holds the actual content, the resolved engine,
 * the accumulated options, and the eventually-loaded expected
 * content; subclasses contribute their format name to the error
 * message format. Single-use, not thread-safe.
 *
 * <p>The {@code unorderedArrayPaths} state is mutated only by
 * subclasses that expose a public setter (JSON does, XML doesn't).
 * It lives on the base because {@link #assertEquals()} consumes it
 * when constructing {@link DiffOptions}.
 *
 * @param <S> the self-type used to keep the fluent chain typed
 */
abstract class AbstractContentBuilder<S extends AbstractContentBuilder<S>> {

    private final DiffEngine engine;
    private final String actualContent;
    private final List<String> ignoreDefaults;

    private String expectedResource;
    private String expectedContent;
    private final List<String> additionalIgnorePatterns = new ArrayList<>();
    private final List<String> unorderedArrayPaths;
    private final Map<String, Object> elValues = new LinkedHashMap<>();

    AbstractContentBuilder(
            DiffEngine engine,
            String actualContent,
            List<String> ignoreDefaults,
            List<String> unorderedDefaults) {
        this.engine = engine;
        this.actualContent = actualContent;
        this.ignoreDefaults = ignoreDefaults;
        this.unorderedArrayPaths = new ArrayList<>(unorderedDefaults);
    }

    /**
     * Load the expected content from {@code classpathResource}
     * using the thread's context classloader. Mutually exclusive
     * with {@link #expectedContent(String)} — calling both throws
     * {@link IllegalStateException}.
     *
     * <p>The resource is loaded lazily during {@link #assertEquals()},
     * so a missing resource surfaces as
     * {@link IllegalArgumentException} from {@code assertEquals()}
     * rather than from this method.
     *
     * @param classpathResource the classpath-relative path
     * @return this builder for chaining
     * @throws IllegalStateException if {@link #expectedContent(String)}
     *         has already been called
     */
    public S expected(String classpathResource) {
        if (expectedContent != null) {
            throw new IllegalStateException(
                    "expected(...) and expectedContent(...) are mutually exclusive");
        }
        this.expectedResource = classpathResource;
        return self();
    }

    /**
     * Use {@code content} as the expected document directly.
     * Mutually exclusive with {@link #expected(String)} — calling
     * both throws {@link IllegalStateException}.
     *
     * @param content the expected document text
     * @return this builder for chaining
     * @throws IllegalStateException if {@link #expected(String)} has
     *         already been called
     */
    public S expectedContent(String content) {
        if (expectedResource != null) {
            throw new IllegalStateException(
                    "expected(...) and expectedContent(...) are mutually exclusive");
        }
        this.expectedContent = content;
        return self();
    }

    /**
     * Add ignore patterns. Cumulative — multiple calls union the set.
     * Patterns are validated lazily during {@link #assertEquals()};
     * malformed patterns throw {@link IllegalArgumentException}
     * from {@code assertEquals()}, not from here.
     *
     * @param patterns one or more pattern strings; the dialect is
     *                 engine-specific (JSON-path or XPath)
     * @return this builder for chaining
     */
    public S ignoring(String... patterns) {
        for (String pattern : patterns) {
            additionalIgnorePatterns.add(pattern);
        }
        return self();
    }

    /**
     * Provide key-value pairs for Jakarta EL interpolation in the
     * expected document. Cumulative — multiple calls merge (later
     * keys override earlier ones).
     *
     * @param values map of EL bindings; the value side accepts
     *               arbitrary objects so EL can resolve property
     *               access and method calls
     * @return this builder for chaining
     */
    public S withValues(Map<String, Object> values) {
        this.elValues.putAll(values);
        return self();
    }

    /**
     * Run the diff. Returns silently when the engine reports no
     * differences; otherwise throws {@link AssertionError} with the
     * multi-line message format documented in the api contract.
     *
     * @throws AssertionError           when at least one difference
     *                                  is reported
     * @throws IllegalArgumentException for malformed expected /
     *                                  actual content, malformed
     *                                  ignore patterns, or missing
     *                                  classpath resource
     */
    public void assertEquals() {
        String resolvedExpected = resolveExpected();
        List<String> mergedPatterns = new ArrayList<>(ignoreDefaults.size() + additionalIgnorePatterns.size());
        mergedPatterns.addAll(ignoreDefaults);
        mergedPatterns.addAll(additionalIgnorePatterns);
        DiffOptions options = new DiffOptions(mergedPatterns, unorderedArrayPaths, elValues);
        List<Difference> differences = engine.diff(resolvedExpected, actualContent, options);
        if (differences.isEmpty()) {
            return;
        }
        throw new AssertionError(formatMessage(differences));
    }

    /**
     * Accumulator visible to subclasses that expose a public
     * setter for unordered-array paths.
     *
     * @return the mutable list backing the builder's per-path
     *         unordered-array state
     */
    List<String> unorderedArrayPaths() {
        return unorderedArrayPaths;
    }

    /**
     * The user-visible format name embedded in the {@link AssertionError}
     * message ("JSON" or "XML"). Subclasses contribute the literal —
     * the engine's {@code contentType()} is the MIME (e.g.
     * {@code application/json}) and is not surfaced to the user.
     *
     * @return the format name
     */
    abstract String formatName();

    @SuppressWarnings("unchecked")
    private S self() {
        return (S) this;
    }

    private String resolveExpected() {
        if (expectedContent != null) {
            return expectedContent;
        }
        if (expectedResource == null) {
            throw new IllegalStateException(
                    "Neither expected(...) nor expectedContent(...) was called");
        }
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(expectedResource)) {
            if (stream == null) {
                throw new IllegalArgumentException("Resource not found: " + expectedResource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ioException) {
            throw new IllegalArgumentException(
                    "Failed to read classpath resource: " + expectedResource, ioException);
        }
    }

    private String formatMessage(List<Difference> differences) {
        StringBuilder message = new StringBuilder();
        message.append(formatName())
                .append(" diff found ")
                .append(differences.size())
                .append(" difference(s):");
        for (Difference difference : differences) {
            message.append(System.lineSeparator())
                    .append("  ")
                    .append(difference.path())
                    .append(": expected=\"")
                    .append(difference.expected())
                    .append("\" actual=\"")
                    .append(difference.actual())
                    .append("\" (expected file line ")
                    .append(difference.expectedLineNumber())
                    .append(")");
        }
        return message.toString();
    }
}
