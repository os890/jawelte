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
package org.os890.jawelte.tests.jpa.scenario10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * A {@code @Transactional} method that throws a {@code RuntimeException} must roll
 * back: the persisted row never makes it to the DB.
 */
@EnableTestBeans
public class Scenario10Test {

    @Inject
    private CustomerService customerService;

    /** No-arg constructor for CDI. */
    public Scenario10Test() {
    }

    /** RuntimeException → rollback → row count is zero. */
    @Test
    public void runtimeExceptionRollsBackThePersist() {
        assertThatThrownBy(() -> customerService.persistAndThrowRuntime("Alice"))
                .as("the service's RuntimeException must propagate to the caller")
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("scenario-10");

        assertThat(customerService.countCustomers())
                .as("rollback on RuntimeException must discard the persisted row")
                .isZero();
    }
}
