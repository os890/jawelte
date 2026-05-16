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
package org.os890.jawelte.tests.springdata.scenario07;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Scenario 7 — `@Transactional` service bean injects the repository and
 * commits via its own method-level transaction. Data persisted in one
 * service call is visible in a second service call.
 */
@EnableTestBeans
public class Scenario07Test {

    @Inject
    private CustomerService customerService;

    /** No-arg constructor for CDI. */
    public Scenario07Test() {
    }

    /** Service.@Transactional.save commits — count from a fresh tx sees the row. */
    @Test
    public void transactionalServiceCommitsThroughRepository() {
        Long savedId = customerService.createCustomer("Alice");
        assertThat(savedId)
                .as("repository.save inside @Transactional must assign a generated id")
                .isNotNull();

        long count = customerService.countCustomers();
        assertThat(count)
                .as("a second @Transactional service call sees exactly the row the first committed")
                .isEqualTo(1L);
    }
}
