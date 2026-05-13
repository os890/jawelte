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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

    private final Set<String> emptyTableNames;

    private ExpectedXmlLineLocator(
            Map<String, List<Integer>> linesPerTable, Set<String> emptyTableNames) {
        this.linesPerTable = linesPerTable;
        this.emptyTableNames = emptyTableNames;
    }

    /**
     * Parse {@code expectedContent} and capture both the 1-based
     * start line of every row element (a child of the dataset root)
     * and the set of table names that appear <em>only</em> as
     * zero-attribute elements &mdash; the empty-table assertion shape
     * {@code &lt;CUSTOMER/&gt;} that DbUnit's
     * {@code FlatXmlDataSetBuilder} silently drops.
     *
     * @param expectedContent the expected XML dataset
     * @return a locator backed by the captured maps
     * @throws IllegalArgumentException when the SAX parse fails
     */
    public static ExpectedXmlLineLocator parse(String expectedContent) {
        Map<String, List<Integer>> linesPerTable = new HashMap<>();
        Set<String> emptyTableNamesRaw = new HashSet<>();
        RowLineHandler handler = new RowLineHandler(linesPerTable, emptyTableNamesRaw);
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
        Set<String> trulyEmpty = new HashSet<>(emptyTableNamesRaw);
        trulyEmpty.removeAll(linesPerTable.keySet());
        return new ExpectedXmlLineLocator(linesPerTable, Collections.unmodifiableSet(trulyEmpty));
    }

    /**
     * Names of tables that appear <em>only</em> as zero-attribute
     * elements in the expected dataset, e.g. {@code &lt;CUSTOMER/&gt;}.
     * A table that also appears with an attributed row is treated as a
     * normal table and is not in this set.
     *
     * <p>The diff engine reads this to assert "the database table is
     * empty" &mdash; any actual row in such a table surfaces as an
     * {@code EXTRA_ROW} difference (unless
     * {@code DiffSpec.subsetOnly()} is on, in which case extras are
     * silently accepted, matching the rest of the engine's
     * subset-only contract).</p>
     *
     * @return the empty-table names (upper-case, unmodifiable)
     */
    public Set<String> emptyTableNames() {
        return emptyTableNames;
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

        private final Set<String> emptyTableNamesRaw;

        private Locator locator;

        private int depth;

        RowLineHandler(
                Map<String, List<Integer>> linesPerTable, Set<String> emptyTableNamesRaw) {
            this.linesPerTable = linesPerTable;
            this.emptyTableNamesRaw = emptyTableNamesRaw;
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
                if (attributes.getLength() == 0) {
                    emptyTableNamesRaw.add(upper);
                } else {
                    linesPerTable
                            .computeIfAbsent(upper, key -> new ArrayList<>())
                            .add(locator == null ? 0 : locator.getLineNumber());
                }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            depth--;
        }
    }
}
