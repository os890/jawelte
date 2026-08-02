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
package org.os890.jawelte.tests.ejb.scenario29;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Half B of the cached-classpath-scan scenario. This module holds
 * two {@code @EnableTestBeans} classes on purpose: each boots its own
 * CDI container in the same JVM, so the extension's
 * {@code BeforeBeanDiscovery} scan request runs twice while the
 * underlying classpath walk must run only once.
 *
 * <p>The assertions are deliberately order-independent — both classes
 * make the same claim about the whole module rather than about "the
 * second boot", so neither the JUnit execution order nor a third test
 * class added later can flip the result.
 *
 * <p>The counters live in
 * {@link TestScenarioCountingEjbAnnotationScanner}, a subclass of the
 * shipped scanner registered at a higher precedence for this module
 * only; {@code Scenario29ATest} is the other half.
 */
@EnableTestBeans
class Scenario29BTest {

    @Inject
    ScannedStateless scannedStateless;

    @Test
    void classpathIsWalkedOnceAcrossEveryContainerBootInThisJvm() {
        assertThat(TestScenarioCountingEjbAnnotationScanner.scanCount())
                .as("the extension asks the scanner for the EJB types on every container boot")
                .isGreaterThanOrEqualTo(1);
        assertThat(TestScenarioCountingEjbAnnotationScanner.classpathWalkCount())
                .as("the classpath walk itself must run exactly once for this classloader, "
                        + "however many containers boot")
                .isEqualTo(1);
    }

    @Test
    void cachedScanStillYieldsAUsableSessionBean() {
        assertThat(scannedStateless)
                .as("the @Stateless type discovered through the cached scan must still be a bean")
                .isNotNull();
        assertThat(scannedStateless.ping()).isEqualTo("pong");
    }
}
