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
package org.os890.jawelte.tests.jpa.scenario08;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * {@code @Transactional} on a service-bean method commits the persist; the
 * inserted row is queryable from a fresh tx.
 */
@EnableTestBeans
public class Scenario08Test {

    @Inject
    private CustomerService customerService;

    /** No-arg constructor for CDI. */
    public Scenario08Test() {
    }

    /** Service.@Transactional persist commits — id assigned and row visible. */
    @Test
    public void transactionalServiceMethodCommitsThePersist() {
        Long generatedId = customerService.createCustomer("Alice");
        assertThat(generatedId)
                .as("persist + commit must assign a generated id")
                .isNotNull();

        long count = customerService.countCustomers();
        assertThat(count)
                .as("a fresh tx must see exactly the row that the previous @Transactional committed")
                .isEqualTo(1L);
    }
}
