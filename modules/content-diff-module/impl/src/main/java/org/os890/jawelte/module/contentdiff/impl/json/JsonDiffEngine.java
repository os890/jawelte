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
package org.os890.jawelte.module.contentdiff.impl.json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jakarta.annotation.Priority;

import org.os890.jawelte.module.contentdiff.api.DiffOptions;
import org.os890.jawelte.module.contentdiff.api.Difference;
import org.os890.jawelte.module.contentdiff.api.port.DiffEngine;
import org.os890.jawelte.module.contentdiff.impl.el.ELInterpolator;
import org.os890.jawelte.module.contentdiff.impl.internal.JsonPathMatcher;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Built-in JSON {@link DiffEngine}. Walks two Jackson {@code JsonNode}
 * trees in parallel and emits one {@link Difference} per mismatched
 * leaf, missing field, or shape disagreement. The first pass over
 * the expected document also collects a JSON-path-to-line-number
 * map via {@code JsonParser} so each {@link Difference} carries the
 * 1-based line in the expected source.
 *
 * <p>Arrays use multiset semantics only when their concrete path
 * matches a pattern in {@link DiffOptions#unorderedArrayPaths()};
 * the check is performed at every array level, so a pattern can
 * pick out a top-level array and leave nested arrays index-wise,
 * or vice versa.
 *
 * <p>Stateless and thread-safe — every {@link #diff(String, String, DiffOptions)}
 * call creates its own {@link ObjectMapper} and matcher instances.
 *
 * <p>Ships at {@link Priority}({@link Integer#MAX_VALUE}); consumers
 * override per content type by registering a competing impl with a
 * lower priority value.
 */
@Priority(Integer.MAX_VALUE)
public class JsonDiffEngine implements DiffEngine {

    private static final String ROOT_PATH = "$";

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public JsonDiffEngine() {
    }

    @Override
    public String contentType() {
        return "application/json";
    }

    @Override
    public List<Difference> diff(String expected, String actual, DiffOptions options) {
        String interpolatedExpected = ELInterpolator.interpolate(expected, options.elValues());
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Integer> expectedLines = new HashMap<>();
        JsonNode expectedTree;
        JsonNode actualTree;
        try {
            expectedTree = objectMapper.readTree(interpolatedExpected);
        } catch (IOException parseException) {
            throw new IllegalArgumentException("Malformed expected JSON", parseException);
        }
        try {
            actualTree = objectMapper.readTree(actual);
        } catch (IOException parseException) {
            throw new IllegalArgumentException("Malformed actual JSON", parseException);
        }
        collectLines(objectMapper.getFactory(), interpolatedExpected, expectedLines);
        JsonPathMatcher ignoreMatcher = JsonPathMatcher.of(options.ignorePatterns());
        JsonPathMatcher unorderedMatcher = JsonPathMatcher.of(options.unorderedArrayPaths());
        List<Difference> differences = new ArrayList<>();
        diffNodes(ROOT_PATH, expectedTree, actualTree, ignoreMatcher, unorderedMatcher,
                expectedLines, differences);
        return List.copyOf(differences);
    }

    private static void collectLines(JsonFactory factory, String content, Map<String, Integer> lines) {
        try (JsonParser parser = factory.createParser(content)) {
            JsonToken first = parser.nextToken();
            if (first == null) {
                return;
            }
            lines.put(ROOT_PATH, (int) parser.currentTokenLocation().getLineNr());
            walkLines(parser, ROOT_PATH, lines);
        } catch (IOException ignored) {
            // tree parsing already validated the content; line-map omissions are non-fatal.
        }
    }

    private static void walkLines(JsonParser parser, String path, Map<String, Integer> lines) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.START_OBJECT) {
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.currentName();
                int fieldLine = (int) parser.currentTokenLocation().getLineNr();
                String childPath = path + "." + fieldName;
                lines.put(childPath, fieldLine);
                parser.nextToken();
                walkLines(parser, childPath, lines);
            }
        } else if (token == JsonToken.START_ARRAY) {
            int index = 0;
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                String childPath = path + "[" + index + "]";
                lines.put(childPath, (int) parser.currentTokenLocation().getLineNr());
                walkLines(parser, childPath, lines);
                index++;
            }
        }
    }

    private static void diffNodes(
            String path,
            JsonNode expected,
            JsonNode actual,
            JsonPathMatcher ignoreMatcher,
            JsonPathMatcher unorderedMatcher,
            Map<String, Integer> expectedLines,
            List<Difference> out) {
        if (ignoreMatcher.matches(path)) {
            return;
        }
        if (expected.getNodeType() != actual.getNodeType()) {
            out.add(new Difference(path, formatValue(expected), formatValue(actual),
                    lineFor(path, expectedLines)));
            return;
        }
        if (expected.isObject()) {
            diffObjects(path, expected, actual, ignoreMatcher, unorderedMatcher,
                    expectedLines, out);
        } else if (expected.isArray()) {
            diffArrays(path, expected, actual, ignoreMatcher, unorderedMatcher,
                    expectedLines, out);
        } else if (!expected.equals(actual)) {
            out.add(new Difference(path, formatValue(expected), formatValue(actual),
                    lineFor(path, expectedLines)));
        }
    }

    private static void diffObjects(
            String path,
            JsonNode expected,
            JsonNode actual,
            JsonPathMatcher ignoreMatcher,
            JsonPathMatcher unorderedMatcher,
            Map<String, Integer> expectedLines,
            List<Difference> out) {
        Set<String> allFieldNames = new LinkedHashSet<>();
        expected.fieldNames().forEachRemaining(allFieldNames::add);
        actual.fieldNames().forEachRemaining(allFieldNames::add);
        for (String fieldName : allFieldNames) {
            String childPath = path + "." + fieldName;
            if (ignoreMatcher.matches(childPath)) {
                continue;
            }
            JsonNode expectedChild = expected.has(fieldName) ? expected.get(fieldName) : null;
            JsonNode actualChild = actual.has(fieldName) ? actual.get(fieldName) : null;
            if (expectedChild == null) {
                out.add(new Difference(childPath, Difference.MISSING, formatValue(actualChild),
                        lineFor(childPath, expectedLines)));
            } else if (actualChild == null) {
                out.add(new Difference(childPath, formatValue(expectedChild), Difference.MISSING,
                        lineFor(childPath, expectedLines)));
            } else {
                diffNodes(childPath, expectedChild, actualChild, ignoreMatcher,
                        unorderedMatcher, expectedLines, out);
            }
        }
    }

    private static void diffArrays(
            String path,
            JsonNode expected,
            JsonNode actual,
            JsonPathMatcher ignoreMatcher,
            JsonPathMatcher unorderedMatcher,
            Map<String, Integer> expectedLines,
            List<Difference> out) {
        if (unorderedMatcher.matches(path)) {
            diffArraysUnordered(path, expected, actual, ignoreMatcher, unorderedMatcher,
                    expectedLines, out);
            return;
        }
        int sharedLength = Math.min(expected.size(), actual.size());
        for (int index = 0; index < sharedLength; index++) {
            String childPath = path + "[" + index + "]";
            if (ignoreMatcher.matches(childPath)) {
                continue;
            }
            diffNodes(childPath, expected.get(index), actual.get(index),
                    ignoreMatcher, unorderedMatcher, expectedLines, out);
        }
        for (int index = sharedLength; index < expected.size(); index++) {
            String childPath = path + "[" + index + "]";
            if (ignoreMatcher.matches(childPath)) {
                continue;
            }
            out.add(new Difference(childPath, formatValue(expected.get(index)), Difference.MISSING,
                    lineFor(childPath, expectedLines)));
        }
        for (int index = sharedLength; index < actual.size(); index++) {
            String childPath = path + "[" + index + "]";
            if (ignoreMatcher.matches(childPath)) {
                continue;
            }
            out.add(new Difference(childPath, Difference.MISSING, formatValue(actual.get(index)), 0));
        }
    }

    private static void diffArraysUnordered(
            String path,
            JsonNode expected,
            JsonNode actual,
            JsonPathMatcher ignoreMatcher,
            JsonPathMatcher unorderedMatcher,
            Map<String, Integer> expectedLines,
            List<Difference> out) {
        boolean[] matched = new boolean[actual.size()];
        Set<Integer> unmatchedExpected = new LinkedHashSet<>();
        for (int expectedIndex = 0; expectedIndex < expected.size(); expectedIndex++) {
            String childPath = path + "[" + expectedIndex + "]";
            if (ignoreMatcher.matches(childPath)) {
                continue;
            }
            JsonNode expectedElement = expected.get(expectedIndex);
            int matchIndex = -1;
            for (int actualIndex = 0; actualIndex < actual.size(); actualIndex++) {
                if (matched[actualIndex]) {
                    continue;
                }
                if (structurallyEqual(expectedElement, actual.get(actualIndex),
                        childPath, ignoreMatcher, unorderedMatcher)) {
                    matched[actualIndex] = true;
                    matchIndex = actualIndex;
                    break;
                }
            }
            if (matchIndex == -1) {
                unmatchedExpected.add(expectedIndex);
            }
        }
        for (int expectedIndex : unmatchedExpected) {
            String childPath = path + "[" + expectedIndex + "]";
            out.add(new Difference(childPath, formatValue(expected.get(expectedIndex)),
                    Difference.MISSING, lineFor(childPath, expectedLines)));
        }
        for (int actualIndex = 0; actualIndex < actual.size(); actualIndex++) {
            if (matched[actualIndex]) {
                continue;
            }
            String childPath = path + "[" + actualIndex + "]";
            if (ignoreMatcher.matches(childPath)) {
                continue;
            }
            out.add(new Difference(childPath, Difference.MISSING,
                    formatValue(actual.get(actualIndex)), 0));
        }
    }

    private static boolean structurallyEqual(
            JsonNode left,
            JsonNode right,
            String basePath,
            JsonPathMatcher ignoreMatcher,
            JsonPathMatcher unorderedMatcher) {
        if (left.getNodeType() != right.getNodeType()) {
            return false;
        }
        if (left.isObject()) {
            Set<String> leftFields = visibleFields(left.fieldNames(), basePath, ignoreMatcher);
            Set<String> rightFields = visibleFields(right.fieldNames(), basePath, ignoreMatcher);
            if (!leftFields.equals(rightFields)) {
                return false;
            }
            for (String fieldName : leftFields) {
                String childPath = basePath + "." + fieldName;
                if (!structurallyEqual(left.get(fieldName), right.get(fieldName),
                        childPath, ignoreMatcher, unorderedMatcher)) {
                    return false;
                }
            }
            return true;
        }
        if (left.isArray()) {
            if (left.size() != right.size()) {
                return false;
            }
            if (unorderedMatcher.matches(basePath)) {
                return multisetEqual(left, right, basePath, ignoreMatcher, unorderedMatcher);
            }
            for (int index = 0; index < left.size(); index++) {
                String childPath = basePath + "[" + index + "]";
                if (!structurallyEqual(left.get(index), right.get(index),
                        childPath, ignoreMatcher, unorderedMatcher)) {
                    return false;
                }
            }
            return true;
        }
        return Objects.equals(left, right);
    }

    private static boolean multisetEqual(
            JsonNode left,
            JsonNode right,
            String basePath,
            JsonPathMatcher ignoreMatcher,
            JsonPathMatcher unorderedMatcher) {
        boolean[] matched = new boolean[right.size()];
        for (int leftIndex = 0; leftIndex < left.size(); leftIndex++) {
            String childPath = basePath + "[" + leftIndex + "]";
            boolean found = false;
            for (int rightIndex = 0; rightIndex < right.size(); rightIndex++) {
                if (matched[rightIndex]) {
                    continue;
                }
                if (structurallyEqual(left.get(leftIndex), right.get(rightIndex),
                        childPath, ignoreMatcher, unorderedMatcher)) {
                    matched[rightIndex] = true;
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> visibleFields(
            Iterator<String> fieldIterator,
            String basePath,
            JsonPathMatcher ignoreMatcher) {
        Set<String> result = new HashSet<>();
        while (fieldIterator.hasNext()) {
            String fieldName = fieldIterator.next();
            if (!ignoreMatcher.matches(basePath + "." + fieldName)) {
                result.add(fieldName);
            }
        }
        return result;
    }

    private static String formatValue(JsonNode node) {
        if (node == null) {
            return Difference.MISSING;
        }
        if (node.isNull()) {
            return "null";
        }
        if (node.isValueNode()) {
            return node.asText();
        }
        return node.toString();
    }

    private static int lineFor(String path, Map<String, Integer> lines) {
        return lines.getOrDefault(path, 0);
    }
}
