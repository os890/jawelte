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
package org.os890.jawelte.tests.jta.scenario09;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jta.impl.xa.XaDataSourceWrapper;

/**
 * Ticket-006 scenario #09 — EMF bootstrapped in JTA mode. The
 * injected {@link EntityManagerFactory#getProperties()} reports
 * {@code jakarta.persistence.transaction-type=JTA},
 * {@code hibernate.transaction.coordinator_class=jta},
 * and a {@link XaDataSourceWrapper}-typed
 * {@code jakarta.persistence.jtaDataSource}.
 */
@EnableTestBeans
public class Scenario09Test {

    @Inject
    private EntityManagerFactory entityManagerFactory;

    /** No-arg constructor for CDI. */
    public Scenario09Test() {
    }

    @Test
    public void emfPropertiesReportJtaModeWithXaWrapper() {
        Object transactionType = entityManagerFactory.getProperties().get("jakarta.persistence.transaction-type");
        assertThat(transactionType).asString().isEqualTo("JTA");

        Object coordinatorClass =
                entityManagerFactory.getProperties().get("hibernate.transaction.coordinator_class");
        assertThat(coordinatorClass).asString().isEqualTo("jta");

        Object jtaDataSource = entityManagerFactory.getProperties().get("jakarta.persistence.jtaDataSource");
        assertThat(jtaDataSource)
                .as("jtaDataSource must be the project's XaDataSourceWrapper, not a raw H2 DataSource")
                .isInstanceOf(XaDataSourceWrapper.class);
    }
}
