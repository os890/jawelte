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
package org.os890.jawelte.tests.dbtestdata.scenario67;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.dbtestdata.api.DbSeed;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;

/**
 * Reproducer for the scenario-03 (lnp-module) seed failure: with
 * jta-module + a JTA detail-impl on the classpath the persistence
 * unit runs as {@code transaction-type="JTA"}, and
 * {@code DbSeed.forPersistenceUnit().…execute()} ends up routed
 * through jpa-module's {@code DefaultPersistenceUnitConnectionResolver}.
 * The resolver currently consults only jpa-module's
 * {@code TransactionScopedEmHolder}, which {@code JtaTransactionStrategy}
 * never populates, so seed fails with "No active EntityManager for
 * persistence unit …" even inside a {@code @Transactional} method.
 *
 * <p>The contract this scenario locks in: under JTA the connection
 * resolver falls back to a CDI-based {@code EntityManager} lookup so
 * {@code DbSeed} works the same way it does under RESOURCE_LOCAL.
 */
@EnableTestBeans
@PersistenceConfig(persistenceUnitName = "dbtestdataScenario67Pu")
class Scenario67Test {

    @Inject
    private EntityManager entityManager;

    @Test
    @Transactional
    void dbSeedRunsAgainstAJtaPersistenceUnit() {
        String dataset = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<dataset>"
                + "<CUSTOMER ID=\"1\" NAME=\"Alice\"/>"
                + "<CUSTOMER ID=\"2\" NAME=\"Bob\"/>"
                + "</dataset>";

        DbSeed.forPersistenceUnit()
                .datasetContent(dataset)
                .cleanInsert()
                .execute();

        Long count = entityManager
                .createQuery("SELECT COUNT(c) FROM Customer c", Long.class)
                .getSingleResult();
        assertThat(count).isEqualTo(2L);
    }
}
