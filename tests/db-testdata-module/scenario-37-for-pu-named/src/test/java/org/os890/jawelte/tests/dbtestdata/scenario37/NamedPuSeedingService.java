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
package org.os890.jawelte.tests.dbtestdata.scenario37;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import org.os890.jawelte.module.dbtestdata.api.DbSeed;

/**
 * Seed via {@code DbSeed.forPersistenceUnit("testPU37")} —
 * explicit-name variant. Demonstrates that the resolver looks up
 * the named PU rather than the active one.
 */
@ApplicationScoped
public class NamedPuSeedingService {

    // Single PU on the classpath — jpa-module's EntityManagerProxy
    // pushes "testPU37" onto the calling thread's stack on first
    // access. DbSeed.forPersistenceUnit("testPU37") then finds the
    // EM via that exact PU name.
    @PersistenceContext
    private EntityManager entityManager;

    public NamedPuSeedingService() {
    }

    @Transactional
    public long seedAndCountWithNamedPu() {
        entityManager.toString();
        DbSeed.forPersistenceUnit("testPU37")
                .datasetContent(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                                + "<dataset>"
                                + "<CUSTOMER ID=\"1\" NAME=\"Alice\"/>"
                                + "<CUSTOMER ID=\"2\" NAME=\"Bob\"/>"
                                + "<CUSTOMER ID=\"3\" NAME=\"Carol\"/>"
                                + "</dataset>")
                .cleanInsert()
                .execute();
        return ((Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM CUSTOMER")
                .getSingleResult()).longValue();
    }
}
