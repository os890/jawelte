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
package org.os890.jawelte.tests.dbtestdata.scenario64;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import org.os890.jawelte.module.dbtestdata.api.DbDiff;
import org.os890.jawelte.module.dbtestdata.api.DbSeed;

/**
 * Touches both persistence units to push two PUs onto the calling
 * thread's stack, then calls {@code DbSeed.forPersistenceUnit()}
 * (no-arg). With both PUs active,
 * {@code forCurrentPersistenceUnit()} would raise
 * {@code IllegalStateException}; the new {@code forPersistenceUnit()}
 * reads {@code @PersistenceConfig.persistenceUnitName} on the test
 * class and routes to {@code testPU64A} instead. The accompanying
 * {@code DbDiff.forPersistenceUnit()} verifies the seed landed in
 * {@code testPU64A} (not {@code testPU64B}).
 */
@ApplicationScoped
public class AnnotationDrivenSeedingService {

    @PersistenceContext(unitName = "testPU64A")
    private EntityManager entityManagerA;

    @PersistenceContext(unitName = "testPU64B")
    private EntityManager entityManagerB;

    public AnnotationDrivenSeedingService() {
    }

    @Transactional
    public long seedWithAnnotationDrivenForPuAndCount() {
        // Both EMs reach native queries so the JPA module pushes both
        // PUs onto the active stack.
        entityManagerA.createNativeQuery("SELECT 1").getSingleResult();
        entityManagerB.createNativeQuery("SELECT 1").getSingleResult();

        DbSeed.forPersistenceUnit()
                .datasetContent(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                                + "<dataset>"
                                + "<CUSTOMER ID=\"1\" NAME=\"Alice\"/>"
                                + "<CUSTOMER ID=\"2\" NAME=\"Bob\"/>"
                                + "</dataset>")
                .cleanInsert()
                .execute();

        DbDiff.forPersistenceUnit()
                .expectedContent(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                                + "<dataset>"
                                + "<CUSTOMER ID=\"1\" NAME=\"Alice\"/>"
                                + "<CUSTOMER ID=\"2\" NAME=\"Bob\"/>"
                                + "</dataset>")
                .assertEquals();

        return ((Number) entityManagerA
                .createNativeQuery("SELECT COUNT(*) FROM CUSTOMER")
                .getSingleResult()).longValue();
    }
}
