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
package example.springdata;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;

@EnableTestBeans
@PersistenceConfig(persistenceUnitName = "customersPU")
class CustomerRepositoryTest {

    @Inject
    CustomerRepository customerRepository;

    @Test
    @Transactional
    void savedCustomerIsRetrievableAndCounted() {
        Customer saved = customerRepository.save(new Customer("Alice"));

        assertThat(saved.getId()).isNotNull();
        assertThat(customerRepository.findById(saved.getId()))
                .hasValueSatisfying(found -> assertThat(found.getName()).isEqualTo("Alice"));
        assertThat(customerRepository.count()).isEqualTo(1);
    }
}
