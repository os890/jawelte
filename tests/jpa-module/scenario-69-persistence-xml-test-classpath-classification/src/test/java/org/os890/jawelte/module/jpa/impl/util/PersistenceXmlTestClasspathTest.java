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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@code PersistenceXmlParser.isTestClasspathPath} must classify a
 * {@code persistence.xml} as test-scoped only for real build test-output
 * locations, not for any path that merely contains a directory literally
 * named {@code test}. A false positive would make {@code selectPreferred}
 * drop the genuinely test-scoped file in favour of a production one.
 *
 * <p>This test lives in {@code PersistenceXmlParser}'s package so it can
 * exercise the package-private classifier directly.
 */
class PersistenceXmlTestClasspathTest {

    @Test
    void classifiesRealBuildTestOutputAsTest() {
        // Maven
        assertThat(PersistenceXmlParser.isTestClasspathPath("/proj/target/test-classes/META-INF/persistence.xml"))
                .as("Maven target/test-classes").isTrue();
        // Gradle test classes + resources
        assertThat(PersistenceXmlParser.isTestClasspathPath("/proj/build/classes/java/test/META-INF/persistence.xml"))
                .as("Gradle build/classes/java/test").isTrue();
        assertThat(PersistenceXmlParser.isTestClasspathPath("/proj/build/resources/test/META-INF/persistence.xml"))
                .as("Gradle build/resources/test").isTrue();
    }

    @Test
    void doesNotClassifyProductionOutputAsTest() {
        assertThat(PersistenceXmlParser.isTestClasspathPath("/proj/target/classes/META-INF/persistence.xml"))
                .as("Maven target/classes (production)").isFalse();
        assertThat(PersistenceXmlParser.isTestClasspathPath("/proj/build/classes/java/main/META-INF/persistence.xml"))
                .as("Gradle build/classes/java/main (production)").isFalse();
    }

    @Test
    void doesNotMisclassifyUnrelatedTestNamedSegments() {
        // A dependency jar living under a directory literally named "test".
        assertThat(PersistenceXmlParser.isTestClasspathPath(
                "file:/home/test/.m2/repository/org/acme/app/1.0/app-1.0.jar!/META-INF/persistence.xml"))
                .as("jar under /home/test/ must NOT be test-scoped").isFalse();
        // A CI workspace path that happens to contain a 'test' segment.
        assertThat(PersistenceXmlParser.isTestClasspathPath("/build/test/proj/target/classes/META-INF/persistence.xml"))
                .as("CI /build/test/ workspace with a production target/classes must NOT be test-scoped").isFalse();
        // A Gradle production path whose home directory is literally 'test'.
        assertThat(PersistenceXmlParser.isTestClasspathPath(
                "/home/test/proj/build/classes/java/main/META-INF/persistence.xml"))
                .as("Gradle production path under a /home/test/ home must NOT be test-scoped").isFalse();
    }
}
