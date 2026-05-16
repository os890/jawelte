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
package org.os890.jawelte.tests.springdata.scenario01;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.mockito.internal.creation.bytebuddy.MockAccess;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.springframework.data.repository.Repository;

/**
 * Scenario 1 — Spring Data repository interfaces are injectable and
 * resolve to a real Spring Data implementation (not a Mockito mock).
 */
@EnableTestBeans
public class Scenario01Test {

    @Inject
    private CustomerRepository customerRepository;

    /** No-arg constructor for CDI. */
    public Scenario01Test() {
    }

    /** Repository is auto-discovered, non-null, and is the real Spring Data proxy. */
    @Test
    public void repositoryIsInjectableAndNotAMock() {
        assertThat(customerRepository)
                .as("spring-data-module's CDI extension must auto-discover CustomerRepository")
                .isNotNull();

        assertThat(customerRepository)
                .as("the injected instance must be a real Spring Data Repository, not a Mockito mock")
                .isInstanceOf(Repository.class)
                .isNotInstanceOf(MockAccess.class);
    }
}
