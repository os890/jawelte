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
package org.os890.jawelte.tests.springdata.scenario12;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Scenario 12 — synthetic repository beans are
 * {@code @RequestScoped}. Within a single test method (one
 * request context) every injection site resolves to the same CDI
 * client proxy reference; CRUD round-trip through that shared
 * reference works end-to-end.
 */
@EnableTestBeans
public class Scenario12Test {

    @Inject
    private CustomerRepository customerRepository;

    @Inject
    private SiblingHolder siblingHolder;

    /** Cross-IP proxy identity within one request context + a CRUD round-trip. */
    @Test
    public void requestScopedRepositoryIsSharedAcrossInjectionSitesWithinATestMethod() {
        assertThat(customerRepository)
                .as("@RequestScoped — every injection point in the same request context "
                        + "resolves to the same CDI client proxy reference")
                .isSameAs(siblingHolder.getCustomerRepository());

        Long id = siblingHolder.saveCustomer("Alice");
        assertThat(id)
                .as("the request-scoped repository persists rows")
                .isNotNull();
        assertThat(siblingHolder.customerCount())
                .as("CRUD via the shared request-scoped repository works")
                .isEqualTo(1L);
    }
}
