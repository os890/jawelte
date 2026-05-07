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
package org.os890.jawelte.tests.jpa.scenario59;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.os890.jawelte.module.jpa.impl.util.PersistenceXmlParser;
import org.os890.jawelte.module.jpa.impl.util.PersistenceXmlParser.ParsedPersistenceUnit;

/**
 * Direct unit tests against
 * {@link PersistenceXmlParser#parseAll(ClassLoader)} — synthesises
 * isolated classloaders backed by temp-dir trees so the assertions
 * don't pollute (or get polluted by) the test reactor's own
 * {@code persistence.xml} resources.
 */
public class Scenario59Test {

    /** No-arg constructor required by JUnit. */
    public Scenario59Test() {
    }

    /** A single PU declaration is parsed; <class> elements drive hasClassElements. */
    @Test
    public void singlePuWithExplicitClassElement(@TempDir Path tempDir) throws IOException {
        Path metaInf = Files.createDirectories(tempDir.resolve("META-INF"));
        Files.writeString(metaInf.resolve("persistence.xml"), """
                <persistence xmlns="https://jakarta.ee/xml/ns/persistence" version="3.2">
                  <persistence-unit name="puA" transaction-type="RESOURCE_LOCAL">
                    <class>com.example.Foo</class>
                  </persistence-unit>
                </persistence>
                """);

        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, null)) {
            List<ParsedPersistenceUnit> units = PersistenceXmlParser.parseAll(classLoader);

            assertThat(units).hasSize(1);
            assertThat(units.get(0).name()).isEqualTo("puA");
            assertThat(units.get(0).classes()).containsExactly("com.example.Foo");
            assertThat(units.get(0).hasClassElements()).isTrue();
        }
    }

    /** Multiple PUs in a single persistence.xml are all parsed in declaration order. */
    @Test
    public void multiplePuInOneXmlAreParsedInOrder(@TempDir Path tempDir) throws IOException {
        Path metaInf = Files.createDirectories(tempDir.resolve("META-INF"));
        Files.writeString(metaInf.resolve("persistence.xml"), """
                <persistence xmlns="https://jakarta.ee/xml/ns/persistence" version="3.2">
                  <persistence-unit name="puA" transaction-type="RESOURCE_LOCAL"/>
                  <persistence-unit name="puB" transaction-type="RESOURCE_LOCAL"/>
                </persistence>
                """);

        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, null)) {
            List<ParsedPersistenceUnit> units = PersistenceXmlParser.parseAll(classLoader);

            assertThat(units).extracting(ParsedPersistenceUnit::name)
                    .containsExactly("puA", "puB");
            assertThat(units).allMatch(unit -> !unit.hasClassElements());
        }
    }

    /** A PU without <class> elements reports hasClassElements()=false. */
    @Test
    public void puWithoutClassElementsTriggersAutoDiscovery(@TempDir Path tempDir) throws IOException {
        Path metaInf = Files.createDirectories(tempDir.resolve("META-INF"));
        Files.writeString(metaInf.resolve("persistence.xml"), """
                <persistence xmlns="https://jakarta.ee/xml/ns/persistence" version="3.2">
                  <persistence-unit name="puNoClasses" transaction-type="RESOURCE_LOCAL"/>
                </persistence>
                """);

        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, null)) {
            List<ParsedPersistenceUnit> units = PersistenceXmlParser.parseAll(classLoader);

            assertThat(units).hasSize(1);
            assertThat(units.get(0).classes()).isEmpty();
            assertThat(units.get(0).hasClassElements())
                    .as("hasClassElements()=false drives the ASM auto-discovery path")
                    .isFalse();
        }
    }

    /** When prod and test xmls are both visible, only the test-classpath one wins. */
    @Test
    public void testClasspathWinsOverProdJarPersistenceXml(@TempDir Path tempDir) throws IOException {
        Path prodRoot = Files.createDirectories(tempDir.resolve("prod-classes"));
        Files.createDirectories(prodRoot.resolve("META-INF"));
        Files.writeString(prodRoot.resolve("META-INF/persistence.xml"), """
                <persistence xmlns="https://jakarta.ee/xml/ns/persistence" version="3.2">
                  <persistence-unit name="prodPu" transaction-type="JTA"/>
                </persistence>
                """);

        Path testRoot = Files.createDirectories(tempDir.resolve("test-classes"));
        Files.createDirectories(testRoot.resolve("META-INF"));
        Files.writeString(testRoot.resolve("META-INF/persistence.xml"), """
                <persistence xmlns="https://jakarta.ee/xml/ns/persistence" version="3.2">
                  <persistence-unit name="testPu" transaction-type="RESOURCE_LOCAL"/>
                </persistence>
                """);

        URL[] classpath = {prodRoot.toUri().toURL(), testRoot.toUri().toURL()};
        try (URLClassLoader classLoader = new URLClassLoader(classpath, null)) {
            List<ParsedPersistenceUnit> units = PersistenceXmlParser.parseAll(classLoader);

            assertThat(units).extracting(ParsedPersistenceUnit::name)
                    .as("only the test-classpath persistence.xml should win — the prod jar is ignored")
                    .containsExactly("testPu");
        }
    }
}
