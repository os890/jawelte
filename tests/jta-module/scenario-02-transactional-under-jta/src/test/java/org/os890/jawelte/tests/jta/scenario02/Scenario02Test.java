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
package org.os890.jawelte.tests.jta.scenario02;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Ticket-006 scenario #02 — {@code @Transactional} under JTA. A
 * service bean's {@code @Transactional} method calls
 * {@code entityManager.persist(...)} inside a JTA transaction
 * driven by {@code JtaTransactionStrategy}; on commit the row is
 * visible to a subsequent {@code @Transactional} read.
 */
@EnableTestBeans
public class Scenario02Test {

    @Inject
    private CustomerService customerService;

    /** No-arg constructor for CDI. */
    public Scenario02Test() {
    }

    @Test
    public void transactionalCommitsTheJtaPersist() {
        Long generatedId = customerService.createCustomer("Alice");
        assertThat(generatedId)
                .as("persist + JTA commit must assign a generated id")
                .isNotNull();

        long count = customerService.countCustomers();
        assertThat(count)
                .as("a fresh JTA read must see exactly the row that the previous @Transactional committed")
                .isEqualTo(1L);
    }
}
