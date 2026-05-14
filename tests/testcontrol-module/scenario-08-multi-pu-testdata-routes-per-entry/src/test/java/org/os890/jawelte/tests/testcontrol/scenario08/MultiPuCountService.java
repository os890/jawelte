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
package org.os890.jawelte.tests.testcontrol.scenario08;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

/**
 * Counts rows in each of the two persistence units used by
 * {@link Scenario08Test}. Two distinct
 * {@code @PersistenceContext(unitName=…)} injection points let the
 * service query the customers PU and the orders PU independently —
 * confirming testcontrol's {@code puName:} prefix routed each
 * {@code testData} entry's seed to the right database.
 */
@ApplicationScoped
public class MultiPuCountService {

    @PersistenceContext(unitName = "testcontrolScenario08CustomersPU")
    private EntityManager customersEntityManager;

    @PersistenceContext(unitName = "testcontrolScenario08OrdersPU")
    private EntityManager ordersEntityManager;

    public MultiPuCountService() {
    }

    @Transactional
    public long countCustomers() {
        return ((Number) customersEntityManager
                .createNativeQuery("SELECT COUNT(*) FROM CUSTOMER")
                .getSingleResult())
                .longValue();
    }

    @Transactional
    public long countOrders() {
        return ((Number) ordersEntityManager
                .createNativeQuery("SELECT COUNT(*) FROM CUSTOMER_ORDER")
                .getSingleResult())
                .longValue();
    }
}
