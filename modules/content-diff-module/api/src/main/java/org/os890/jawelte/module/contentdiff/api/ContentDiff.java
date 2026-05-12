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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.os890.jawelte.core.api.port.ConfigResolver;
import org.os890.jawelte.core.api.port.ServicePriorityResolver;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.contentdiff.api.port.DiffEngine;

/**
 * Fluent entry point for semantic content diffing. {@link
 * #forJson(String)} and {@link #forXml(String)} are the only static
 * factories; both return typed builders bound to the supplied actual
 * payload.
 *
 * <h2>Engine selection</h2>
 *
 * <p>Engine lookup enumerates every registered {@link DiffEngine}
 * via {@link ServiceLoader#load(Class)}, filters by
 * {@link DiffEngine#contentType()}, and resolves the lowest-priority
 * candidate through the active
 * {@link ServicePriorityResolver} (obtained through
 * {@link TestContext#loadService(Class)}). The resolved engine is
 * cached per content type for the JVM lifetime — subsequent
 * {@code forJson(...)} / {@code forXml(...)} calls skip the resolver.
 *
 * <h2>MicroProfile Config defaults</h2>
 *
 * <p>Three keys carry JVM-wide defaults that are <em>prepended</em>
 * to whatever the caller adds through the builder:
 *
 * <ul>
 *   <li>{@value #JSON_IGNORE_DEFAULTS_KEY} —
 *       JSON-path patterns to skip during JSON diffs.</li>
 *   <li>{@value #XML_IGNORE_DEFAULTS_KEY} —
 *       XPath patterns to skip during XML diffs.</li>
 *   <li>{@value #JSON_UNORDERED_DEFAULTS_KEY} —
 *       JSON-path patterns identifying arrays that should be
 *       compared as multisets (the JSON analogue of
 *       {@link JsonBuilder#unorderedArrays(String...)}).</li>
 * </ul>
 *
 * <p>All three accept a comma-separated list and default to empty.
 * Values are read once on first use via the active
 * {@link ConfigResolver} and cached for the JVM lifetime.
 *
 * <h2>Util-class convention</h2>
 *
 * <p>{@code abstract} per the project's util-class convention; no
 * constructor, no instance, no subclassing intended outside of this
 * class's own static initialiser.
 */
public abstract class ContentDiff {

    /**
     * MP Config key for the per-JVM default JSON-ignore-pattern list.
     * Comma-separated. Read once via {@link ConfigResolver} on first
     * {@link #forJson(String)} call; prepended to caller-supplied
     * patterns from {@code ignoring(...)}. Default: empty list.
     */
    public static final String JSON_IGNORE_DEFAULTS_KEY =
            "org.os890.jawelte.module.contentdiff.api.ContentDiff.json.ignore";

    /**
     * MP Config key for the per-JVM default XML-ignore-pattern list.
     * Same shape as {@link #JSON_IGNORE_DEFAULTS_KEY}, consumed by
     * {@link #forXml(String)}.
     */
    public static final String XML_IGNORE_DEFAULTS_KEY =
            "org.os890.jawelte.module.contentdiff.api.ContentDiff.xml.ignore";

    /**
     * MP Config key for the per-JVM default list of array paths the
     * JSON engine should compare with multiset semantics. Same shape
     * as {@link #JSON_IGNORE_DEFAULTS_KEY}; consumed by
     * {@link #forJson(String)} and merged with any paths the caller
     * adds through
     * {@link JsonBuilder#unorderedArrays(String...)}.
     */
    public static final String JSON_UNORDERED_DEFAULTS_KEY =
            "org.os890.jawelte.module.contentdiff.api.ContentDiff.json.unordered-arrays";

    static final String JSON_CONTENT_TYPE = "application/json";

    static final String XML_CONTENT_TYPE = "application/xml";

    static final String JSON_FORMAT_NAME = "JSON";

    static final String XML_FORMAT_NAME = "XML";

    private static final ConcurrentMap<String, DiffEngine> CACHED_ENGINES = new ConcurrentHashMap<>();

    private static final ConcurrentMap<String, List<String>> CACHED_CSV_DEFAULTS = new ConcurrentHashMap<>();

    private ContentDiff() {
    }

    /**
     * Resolve the {@link DiffEngine} for {@code application/json} and
     * return a builder bound to {@code actualContent}.
     *
     * @param actualContent the actual JSON document; must not be {@code null}
     * @return a fresh single-use {@link JsonBuilder}
     * @throws NullPointerException  if {@code actualContent} is {@code null}
     * @throws IllegalStateException if no {@link DiffEngine} is
     *         registered with {@code contentType() == "application/json"}
     */
    public static JsonBuilder forJson(String actualContent) {
        Objects.requireNonNull(actualContent, "actualContent");
        DiffEngine engine = resolveEngine(JSON_CONTENT_TYPE);
        List<String> ignoreDefaults = resolveCsvDefaults(JSON_IGNORE_DEFAULTS_KEY);
        List<String> unorderedDefaults = resolveCsvDefaults(JSON_UNORDERED_DEFAULTS_KEY);
        return new JsonBuilder(engine, actualContent, ignoreDefaults, unorderedDefaults);
    }

    /**
     * Resolve the {@link DiffEngine} for {@code application/xml} and
     * return a builder bound to {@code actualContent}.
     *
     * @param actualContent the actual XML document; must not be {@code null}
     * @return a fresh single-use {@link XmlBuilder}
     * @throws NullPointerException  if {@code actualContent} is {@code null}
     * @throws IllegalStateException if no {@link DiffEngine} is
     *         registered with {@code contentType() == "application/xml"}
     */
    public static XmlBuilder forXml(String actualContent) {
        Objects.requireNonNull(actualContent, "actualContent");
        DiffEngine engine = resolveEngine(XML_CONTENT_TYPE);
        List<String> ignoreDefaults = resolveCsvDefaults(XML_IGNORE_DEFAULTS_KEY);
        return new XmlBuilder(engine, actualContent, ignoreDefaults);
    }

    private static DiffEngine resolveEngine(String contentType) {
        DiffEngine cached = CACHED_ENGINES.get(contentType);
        if (cached != null) {
            return cached;
        }
        List<DiffEngine> matching = new ArrayList<>();
        for (DiffEngine candidate : ServiceLoader.load(DiffEngine.class)) {
            if (contentType.equals(candidate.contentType())) {
                matching.add(candidate);
            }
        }
        if (matching.isEmpty()) {
            throw new IllegalStateException("No DiffEngine for content type: " + contentType);
        }
        ServicePriorityResolver resolver = TestContext.loadService(ServicePriorityResolver.class);
        DiffEngine resolved = resolver.resolve(matching);
        CACHED_ENGINES.put(contentType, resolved);
        return resolved;
    }

    private static List<String> resolveCsvDefaults(String configKey) {
        List<String> cached = CACHED_CSV_DEFAULTS.get(configKey);
        if (cached != null) {
            return cached;
        }
        ConfigResolver configResolver = TestContext.loadService(ConfigResolver.class);
        List<String> entries = configResolver.resolve(configKey)
                .map(ContentDiff::splitCsv)
                .orElseGet(List::of);
        CACHED_CSV_DEFAULTS.put(configKey, entries);
        return entries;
    }

    private static List<String> splitCsv(String csv) {
        List<String> entries = new ArrayList<>();
        for (String entry : csv.split(",")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                entries.add(trimmed);
            }
        }
        return List.copyOf(entries);
    }
}
