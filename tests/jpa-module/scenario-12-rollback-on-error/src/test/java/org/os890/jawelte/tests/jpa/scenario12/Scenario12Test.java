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
package org.os890.jawelte.tests.jpa.scenario12;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * A {@code @Transactional} method that throws an {@link Error} must roll back:
 * the persisted row never reaches the DB. Locks in the interceptor's
 * {@code catch (Error)} branch.
 */
@EnableTestBeans
public class Scenario12Test {

    @Inject
    private CustomerService customerService;

    /** No-arg constructor for CDI. */
    public Scenario12Test() {
    }

    /** Error → rollback → row count is zero. */
    @Test
    public void errorAlsoRollsBack() {
        assertThatThrownBy(() -> customerService.persistAndThrowError("Alice"))
                .as("the service's Error must propagate to the caller")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("scenario-12");

        assertThat(customerService.countCustomers())
                .as("rollback on Error must discard the persisted row")
                .isZero();
    }
}
