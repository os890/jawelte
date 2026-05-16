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
package org.os890.jawelte.tests.springdata.scenario10;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Scenario 10 — `@NoRepositoryBean` on a parent interface does not
 * propagate to a concrete child. The concrete child is registered;
 * no separate bean exists whose {@code beanClass} is the parent.
 */
@EnableTestBeans
public class Scenario10Test {

    @Inject
    private CustomerRepository customerRepository;

    @Inject
    private BeanManager beanManager;

    /** No-arg constructor for CDI. */
    public Scenario10Test() {
    }

    /** The concrete child is registered; the `@NoRepositoryBean` parent has no own bean. */
    @Test
    public void concreteChildRegisteredParentSkipped() {
        assertThat(customerRepository)
                .as("CustomerRepository (the concrete child) is injected")
                .isNotNull();

        assertThat(beanManager.getBeans(CustomerRepository.class))
                .as("exactly one bean registered for the concrete child")
                .hasSize(1);

        for (Bean<?> candidate : beanManager.getBeans(BaseRepository.class)) {
            assertThat(candidate.getBeanClass())
                    .as("no orphan bean for the @NoRepositoryBean-marked parent; "
                            + "the only bean carrying BaseRepository as a type is the "
                            + "synthetic for the concrete CustomerRepository")
                    .isEqualTo(CustomerRepository.class);
        }
    }
}
