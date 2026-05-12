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
package org.os890.jawelte.module.dbtestdata.impl.util;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.os890.jawelte.module.dbtestdata.api.DbDifference;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Pre-parses a DbUnit-flat XML dataset to extract the 1-based
 * line numbers of each row element. Used by the diff engine to
 * populate {@link DbDifference#expectedLineNumber()} so test authors
 * can navigate directly to the failing row in their expected
 * fixture.
 *
 * <p>DbUnit's standard {@code FlatXmlDataSetBuilder} does not expose
 * SAX {@link Locator} information through its public api; this class
 * runs a second SAX pass dedicated to capturing the start-line of
 * every row element (i.e. every element whose parent is the dataset
 * root). Table names are stored in upper case to match DbUnit's own
 * normalisation.
 *
 * <p>Stateless after {@link #parse(String)}; the resulting locator
 * is safe to share across threads (its internal state is only read).
 */
public class ExpectedXmlLineLocator {

    private final Map<String, List<Integer>> linesPerTable;

    private ExpectedXmlLineLocator(Map<String, List<Integer>> linesPerTable) {
        this.linesPerTable = linesPerTable;
    }

    /**
     * Parse {@code expectedContent} and capture the 1-based start
     * line of every row element (a child of the dataset root).
     *
     * @param expectedContent the expected XML dataset
     * @return a locator backed by the captured line map
     * @throws IllegalArgumentException when the SAX parse fails
     */
    public static ExpectedXmlLineLocator parse(String expectedContent) {
        Map<String, List<Integer>> linesPerTable = new HashMap<>();
        RowLineHandler handler = new RowLineHandler(linesPerTable);
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setValidating(false);
            SAXParser parser = factory.newSAXParser();
            parser.parse(
                    new org.xml.sax.InputSource(new StringReader(expectedContent)),
                    handler);
        } catch (Exception parseFailure) {
            throw new IllegalArgumentException(
                    "Malformed dataset: " + parseFailure.getMessage(), parseFailure);
        }
        return new ExpectedXmlLineLocator(linesPerTable);
    }

    /**
     * Resolve the 1-based line number of {@code rowIndex} within
     * {@code tableName}. Returns {@code 0} when no entry exists —
     * matches the api contract for "no meaningful line known".
     *
     * @param tableName the table (case-insensitive — stored as
     *                  upper case)
     * @param rowIndex  the 0-based row index within that table
     * @return the 1-based line, or {@code 0} when out of range
     */
    public int lineFor(String tableName, int rowIndex) {
        List<Integer> lines = linesPerTable.get(tableName.toUpperCase(Locale.ROOT));
        if (lines == null || rowIndex < 0 || rowIndex >= lines.size()) {
            return 0;
        }
        return lines.get(rowIndex);
    }

    private static class RowLineHandler extends DefaultHandler {

        private final Map<String, List<Integer>> linesPerTable;

        private Locator locator;

        private int depth;

        RowLineHandler(Map<String, List<Integer>> linesPerTable) {
            this.linesPerTable = linesPerTable;
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
        }

        @Override
        public void startElement(
                String uri, String localName, String qName, Attributes attributes) throws SAXException {
            depth++;
            // depth 1 = root dataset element; depth 2 = row elements.
            if (depth == 2) {
                String upper = qName.toUpperCase(Locale.ROOT);
                linesPerTable
                        .computeIfAbsent(upper, key -> new ArrayList<>())
                        .add(locator == null ? 0 : locator.getLineNumber());
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            depth--;
        }
    }
}
