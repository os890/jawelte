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
package org.os890.jawelte.tests.testcontrol.scenario01;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

/**
 * Counter of CUSTOMER rows used by {@link Scenario01Test} to observe
 * what testcontrol's seed phase actually wrote to the database.
 * {@code @Transactional} so jta-module's interceptor opens a
 * transaction and pushes the EM onto the active stack — the query
 * sees data committed by testcontrol's earlier seed transaction.
 */
@ApplicationScoped
public class Scenario01CustomerCountService {

    @PersistenceContext
    private EntityManager entityManager;

    public Scenario01CustomerCountService() {
    }

    @Transactional
    public long countCustomers() {
        return ((Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM CUSTOMER")
                .getSingleResult())
                .longValue();
    }
}
