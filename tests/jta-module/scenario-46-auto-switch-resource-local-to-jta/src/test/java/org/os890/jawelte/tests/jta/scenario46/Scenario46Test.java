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
package org.os890.jawelte.tests.jta.scenario46;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Verifies that {@code jta-module} auto-corrects a persistence unit
 * declared as {@code transaction-type="RESOURCE_LOCAL"} in its
 * persistence.xml to {@code JTA} at EMF bootstrap (when jta-module
 * is active on the classpath). The resolver also logs an
 * {@code INFO}-level record when no explicit {@code transactionType}
 * was configured — verified at the matrix-output level rather than
 * captured here, since Surefire's forked JVM re-initialises JUL after
 * the test class's static handlers can attach.
 */
@EnableTestBeans
public class Scenario46Test {

    @Inject
    private EntityManagerFactory entityManagerFactory;

    /** No-arg constructor for CDI. */
    public Scenario46Test() {
    }

    @Test
    public void persistenceXmlSaysResourceLocalButEmfReportsJta() {
        Object transactionType =
                entityManagerFactory.getProperties().get("jakarta.persistence.transactionType");
        assertThat(transactionType)
                .as("persistence.xml declared RESOURCE_LOCAL — jta-module must auto-correct the EMF to JTA")
                .asString()
                .isEqualTo("JTA");
    }
}
