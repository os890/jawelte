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
package org.os890.jawelte.tests.jpa.scenario11;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Project-wide rollback rule: a {@code @Transactional} method that throws a
 * <em>checked</em> {@link BusinessException} must also roll back. This is the
 * intentional divergence from Jakarta EE's commit-on-checked default — the
 * scenario directory keeps the POC-era "commit-on-checked-exception" label,
 * but the assertion locks in jawelte's actual behaviour.
 */
@EnableTestBeans
public class Scenario11Test {

    @Inject
    private CustomerService customerService;

    /** No-arg constructor for CDI. */
    public Scenario11Test() {
    }

    /** Checked exception → rollback → row count is zero (jawelte rule). */
    @Test
    public void checkedExceptionAlsoRollsBack() {
        assertThatThrownBy(() -> customerService.persistAndThrowChecked("Alice"))
                .as("the service's checked exception must propagate to the caller")
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("scenario-11");

        assertThat(customerService.countCustomers())
                .as("jawelte's project-wide rule rolls back on every thrown exception "
                        + "— including checked ones — diverging intentionally from "
                        + "Jakarta EE's commit-on-checked default")
                .isZero();
    }
}
