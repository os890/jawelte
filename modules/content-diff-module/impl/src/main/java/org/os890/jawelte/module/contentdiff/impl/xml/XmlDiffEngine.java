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
package org.os890.jawelte.module.contentdiff.impl.xml;

import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import jakarta.annotation.Priority;

import org.os890.jawelte.module.contentdiff.api.DiffOptions;
import org.os890.jawelte.module.contentdiff.api.Difference;
import org.os890.jawelte.module.contentdiff.api.port.DiffEngine;
import org.os890.jawelte.module.contentdiff.impl.el.ELInterpolator;
import org.os890.jawelte.module.contentdiff.impl.internal.XmlPathMatcher;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Built-in XML {@link DiffEngine}. Walks two DOM trees in parallel
 * and emits one {@link Difference} per mismatched text node,
 * missing element, or attribute discrepancy. A separate SAX pass on
 * the expected document populates an XPath-to-line-number map so
 * each {@link Difference} carries the line number where the
 * mismatched element appears in the expected source.
 *
 * <p>Element ordering matters by default; attribute sets are
 * compared as unordered sets. {@link
 * DiffOptions#unorderedArrayPaths()} is a no-op for XML (JSON
 * arrays have no XML analogue) and the
 * {@code XmlBuilder} factory always passes an empty list there.
 *
 * <p>Path representation: every non-root element step carries a
 * 1-based predicate ({@code /orders/order[1]/id[1]}). The matcher
 * implementation tolerates user patterns without explicit
 * predicates ({@code /orders/order/id}), so the diff engine emits
 * unambiguous paths while user patterns stay readable.
 *
 * <p>Leaf-element text comparison ignores surrounding whitespace —
 * both sides are {@code String.trim()}-ed before equality. The
 * document is also DOM-normalised after parsing so adjacent text
 * nodes (rare but possible around CDATA / comments) collapse into
 * a single text child before the comparison.
 *
 * <p>Stateless and thread-safe — every {@link #diff(String, String, DiffOptions)}
 * call constructs its own factories.
 */
@Priority(Integer.MAX_VALUE)
public class XmlDiffEngine implements DiffEngine {

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public XmlDiffEngine() {
    }

    @Override
    public String contentType() {
        return "application/xml";
    }

    @Override
    public List<Difference> diff(String expected, String actual, DiffOptions options) {
        String interpolatedExpected = ELInterpolator.interpolate(expected, options.elValues());
        Document expectedDocument = parseDocument(interpolatedExpected, "expected");
        Document actualDocument = parseDocument(actual, "actual");
        // normalize() merges adjacent text nodes and discards empty
        // ones — turns "<a>hello<!-- comment --> world</a>" into a
        // single text-node child for the leaf-text comparison below.
        expectedDocument.getDocumentElement().normalize();
        actualDocument.getDocumentElement().normalize();
        Map<String, Integer> expectedLines = collectLines(interpolatedExpected);
        XmlPathMatcher ignoreMatcher = XmlPathMatcher.of(options.ignorePatterns());
        List<Difference> differences = new ArrayList<>();
        Element expectedRoot = expectedDocument.getDocumentElement();
        Element actualRoot = actualDocument.getDocumentElement();
        if (!expectedRoot.getNodeName().equals(actualRoot.getNodeName())) {
            String rootPath = "/" + expectedRoot.getNodeName();
            differences.add(new Difference(rootPath,
                    "<" + expectedRoot.getNodeName() + ">",
                    "<" + actualRoot.getNodeName() + ">",
                    lineFor(rootPath, expectedLines)));
            return List.copyOf(differences);
        }
        diffElement("/" + expectedRoot.getNodeName(), expectedRoot, actualRoot,
                ignoreMatcher, expectedLines, differences);
        return List.copyOf(differences);
    }

    private static Document parseDocument(String content, String side) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(content)));
        } catch (Exception parseException) {
            throw new IllegalArgumentException("Malformed " + side + " XML", parseException);
        }
    }

    private static Map<String, Integer> collectLines(String content) {
        Map<String, Integer> lines = new HashMap<>();
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setNamespaceAware(false);
            SAXParser parser = factory.newSAXParser();
            parser.parse(new InputSource(new StringReader(content)),
                    new LineCollectingHandler(lines));
        } catch (Exception ignored) {
            // Best-effort; missing line numbers default to 0 at lookup time.
        }
        return lines;
    }

    private static void diffElement(
            String currentPath,
            Element expected,
            Element actual,
            XmlPathMatcher ignoreMatcher,
            Map<String, Integer> expectedLines,
            List<Difference> out) {
        if (ignoreMatcher.matches(currentPath)) {
            return;
        }
        diffAttributes(currentPath, expected, actual, ignoreMatcher, expectedLines, out);
        List<Element> expectedChildren = elementChildren(expected);
        List<Element> actualChildren = elementChildren(actual);
        if (expectedChildren.isEmpty() && actualChildren.isEmpty()) {
            // Leaf-element text comparison ignores surrounding
            // whitespace on either side. Indentation, trailing
            // newlines from XML serialisers, and tab vs space drift
            // between fixtures shouldn't manifest as diffs.
            String expectedText = expected.getTextContent().trim();
            String actualText = actual.getTextContent().trim();
            if (!expectedText.equals(actualText)) {
                out.add(new Difference(currentPath, expectedText, actualText,
                        lineFor(currentPath, expectedLines)));
            }
            return;
        }
        diffChildElements(currentPath, expectedChildren, actualChildren,
                ignoreMatcher, expectedLines, out);
    }

    private static void diffAttributes(
            String currentPath,
            Element expected,
            Element actual,
            XmlPathMatcher ignoreMatcher,
            Map<String, Integer> expectedLines,
            List<Difference> out) {
        Map<String, String> expectedAttributes = attributes(expected);
        Map<String, String> actualAttributes = attributes(actual);
        Set<String> allNames = new LinkedHashSet<>();
        allNames.addAll(expectedAttributes.keySet());
        allNames.addAll(actualAttributes.keySet());
        for (String attributeName : allNames) {
            String attributePath = currentPath + "/@" + attributeName;
            if (ignoreMatcher.matches(attributePath)) {
                continue;
            }
            String expectedValue = expectedAttributes.get(attributeName);
            String actualValue = actualAttributes.get(attributeName);
            if (expectedValue == null) {
                out.add(new Difference(attributePath, Difference.MISSING, actualValue,
                        lineFor(currentPath, expectedLines)));
            } else if (actualValue == null) {
                out.add(new Difference(attributePath, expectedValue, Difference.MISSING,
                        lineFor(currentPath, expectedLines)));
            } else if (!expectedValue.equals(actualValue)) {
                out.add(new Difference(attributePath, expectedValue, actualValue,
                        lineFor(currentPath, expectedLines)));
            }
        }
    }

    private static void diffChildElements(
            String currentPath,
            List<Element> expectedChildren,
            List<Element> actualChildren,
            XmlPathMatcher ignoreMatcher,
            Map<String, Integer> expectedLines,
            List<Difference> out) {
        Map<String, List<Element>> expectedGrouped = groupByLocalName(expectedChildren);
        Map<String, List<Element>> actualGrouped = groupByLocalName(actualChildren);
        Set<String> allNames = new LinkedHashSet<>();
        allNames.addAll(expectedGrouped.keySet());
        allNames.addAll(actualGrouped.keySet());
        for (String name : allNames) {
            List<Element> expectedSameName = expectedGrouped.getOrDefault(name, List.of());
            List<Element> actualSameName = actualGrouped.getOrDefault(name, List.of());
            int sharedCount = Math.min(expectedSameName.size(), actualSameName.size());
            for (int index = 0; index < sharedCount; index++) {
                String childPath = currentPath + "/" + name + "[" + (index + 1) + "]";
                diffElement(childPath, expectedSameName.get(index), actualSameName.get(index),
                        ignoreMatcher, expectedLines, out);
            }
            for (int index = sharedCount; index < expectedSameName.size(); index++) {
                String childPath = currentPath + "/" + name + "[" + (index + 1) + "]";
                if (ignoreMatcher.matches(childPath)) {
                    continue;
                }
                out.add(new Difference(childPath, summarise(expectedSameName.get(index)),
                        Difference.MISSING, lineFor(childPath, expectedLines)));
            }
            for (int index = sharedCount; index < actualSameName.size(); index++) {
                String childPath = currentPath + "/" + name + "[" + (index + 1) + "]";
                if (ignoreMatcher.matches(childPath)) {
                    continue;
                }
                out.add(new Difference(childPath, Difference.MISSING,
                        summarise(actualSameName.get(index)), 0));
            }
        }
    }

    private static List<Element> elementChildren(Element parent) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                result.add((Element) child);
            }
        }
        return result;
    }

    private static Map<String, List<Element>> groupByLocalName(List<Element> elements) {
        Map<String, List<Element>> grouped = new LinkedHashMap<>();
        for (Element element : elements) {
            grouped.computeIfAbsent(element.getNodeName(), name -> new ArrayList<>()).add(element);
        }
        return grouped;
    }

    private static Map<String, String> attributes(Element element) {
        Map<String, String> result = new LinkedHashMap<>();
        NamedNodeMap namedNodeMap = element.getAttributes();
        for (int index = 0; index < namedNodeMap.getLength(); index++) {
            Node attribute = namedNodeMap.item(index);
            result.put(attribute.getNodeName(), attribute.getNodeValue());
        }
        return result;
    }

    private static String summarise(Element element) {
        String text = element.getTextContent();
        if (text.isBlank() && element.getChildNodes().getLength() == 0) {
            return "<" + element.getNodeName() + "/>";
        }
        if (elementChildren(element).isEmpty()) {
            return text;
        }
        return "<" + element.getNodeName() + ">...</" + element.getNodeName() + ">";
    }

    private static int lineFor(String path, Map<String, Integer> lines) {
        return lines.getOrDefault(path, 0);
    }

    private static class LineCollectingHandler extends DefaultHandler {

        private final Map<String, Integer> lines;
        private final Deque<String> pathStack = new ArrayDeque<>();
        private final Deque<Map<String, Integer>> siblingCounts = new ArrayDeque<>();
        private Locator documentLocator;

        LineCollectingHandler(Map<String, Integer> lines) {
            this.lines = lines;
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            this.documentLocator = locator;
        }

        @Override
        public void startDocument() {
            siblingCounts.push(new HashMap<>());
        }

        @Override
        public void startElement(String namespaceUri, String localName, String qualifiedName, Attributes attributes) {
            String elementName = qualifiedName;
            Map<String, Integer> currentCounts = siblingCounts.peek();
            int siblingIndex = currentCounts.merge(elementName, 1, Integer::sum);
            String currentPath;
            if (pathStack.isEmpty()) {
                currentPath = "/" + elementName;
            } else {
                currentPath = pathStack.peek() + "/" + elementName + "[" + siblingIndex + "]";
            }
            pathStack.push(currentPath);
            siblingCounts.push(new HashMap<>());
            int lineNumber = documentLocator == null ? 0 : documentLocator.getLineNumber();
            lines.put(currentPath, lineNumber);
        }

        @Override
        public void endElement(String namespaceUri, String localName, String qualifiedName) {
            pathStack.pop();
            siblingCounts.pop();
        }
    }
}
