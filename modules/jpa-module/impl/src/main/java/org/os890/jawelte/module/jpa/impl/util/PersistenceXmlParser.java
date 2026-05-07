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
package org.os890.jawelte.module.jpa.impl.util;

import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Lightweight DOM-based parser for {@code META-INF/persistence.xml}
 * resources on a {@link ClassLoader}. Returns one
 * {@link ParsedPersistenceUnit} entry per {@code <persistence-unit>}
 * element across every {@code persistence.xml} reachable through
 * {@link ClassLoader#getResources(String)}.
 *
 * <p>Used by {@code JpaCdiExtension.beforeBeanDiscovery} to enumerate
 * persistence-unit names (the bootstrap doesn't otherwise know what
 * to pass to {@link jakarta.persistence.Persistence#createEntityManagerFactory(String, java.util.Map)})
 * and to decide whether ASM-based entity auto-discovery is needed
 * for a given persistence unit (skipped when at least one
 * {@code <class>} element is declared).
 *
 * <p>Read errors are logged at {@link Level#WARNING} and skipped —
 * a malformed {@code persistence.xml} on the classpath never breaks
 * the bootstrap of others.
 */
public abstract class PersistenceXmlParser {

    private static final Logger LOG = System.getLogger(PersistenceXmlParser.class.getName());

    /**
     * Suppressed-instantiation constructor. The class is
     * {@code abstract} so direct {@code new} is impossible; the
     * explicit declaration silences {@code javadoc -doclint:all} on
     * the otherwise synthesized default constructor.
     */
    protected PersistenceXmlParser() {
    }

    /**
     * Parse every {@code META-INF/persistence.xml} resource visible
     * to the given {@link ClassLoader}.
     *
     * @param classLoader the class loader whose resources to scan
     * @return the persistence units found, in classpath traversal
     *         order; never {@code null}
     */
    public static List<ParsedPersistenceUnit> parseAll(ClassLoader classLoader) {
        List<ParsedPersistenceUnit> result = new ArrayList<>();
        try {
            Enumeration<URL> resources = classLoader.getResources("META-INF/persistence.xml");
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try (InputStream stream = url.openStream()) {
                    result.addAll(parseOne(stream));
                } catch (Exception parseFailure) {
                    LOG.log(Level.WARNING, "Failed to parse persistence.xml at " + url, parseFailure);
                }
            }
        } catch (Exception scanFailure) {
            LOG.log(Level.WARNING, "Failed to scan classpath for persistence.xml", scanFailure);
        }
        return List.copyOf(result);
    }

    private static List<ParsedPersistenceUnit> parseOne(InputStream stream) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(null);
        NodeList persistenceUnits = builder.parse(stream).getElementsByTagNameNS("*", "persistence-unit");
        List<ParsedPersistenceUnit> result = new ArrayList<>();
        for (int index = 0; index < persistenceUnits.getLength(); index++) {
            Element persistenceUnitElement = (Element) persistenceUnits.item(index);
            String name = persistenceUnitElement.getAttribute("name");
            List<String> classes = new ArrayList<>();
            NodeList classElements = persistenceUnitElement.getElementsByTagNameNS("*", "class");
            for (int classIndex = 0; classIndex < classElements.getLength(); classIndex++) {
                String text = classElements.item(classIndex).getTextContent();
                if (text != null && !text.isBlank()) {
                    classes.add(text.trim());
                }
            }
            result.add(new ParsedPersistenceUnit(name, List.copyOf(classes)));
        }
        return result;
    }

    /**
     * Parsed shape of a {@code <persistence-unit>} element.
     *
     * @param name              the {@code name} attribute
     * @param classes           the bodies of {@code <class>} child
     *                          elements; empty if none declared
     */
    public record ParsedPersistenceUnit(String name, List<String> classes) {

        /**
         * Whether the persistence unit declares at least one
         * {@code <class>} element. When {@code false},
         * {@code JpaCdiExtension} runs ASM-based auto-discovery to
         * populate the EMF property bag.
         *
         * @return {@code true} if at least one {@code <class>} was
         *         declared, {@code false} otherwise
         */
        public boolean hasClassElements() {
            return !classes.isEmpty();
        }
    }
}
