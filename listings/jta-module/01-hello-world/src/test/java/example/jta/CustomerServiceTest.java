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
package example.jta;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Hello-world for jta-module: the test method itself is NOT
 * @Transactional. The transaction boundary lives inside CustomerService,
 * exactly as it would in production code. Each @Transactional call
 * begins a fresh JTA transaction; on commit, the next @Transactional
 * read sees the row.
 */
@EnableTestBeans
class CustomerServiceTest {

    @Inject
    private CustomerService customerService;

    @Test
    void transactionalServiceCallCommitsAndIsVisibleToTheNextCall() {
        Long generatedId = customerService.createCustomer("Alice");
        assertThat(generatedId)
                .as("createCustomer's JTA transaction committed and assigned a generated id")
                .isNotNull();

        assertThat(customerService.countCustomers())
                .as("a fresh JTA read sees the row the previous @Transactional committed")
                .isEqualTo(1L);
    }
}
