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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class CustomerService {

    @Inject
    private EntityManager entityManager;

    public CustomerService() {
    }

    @Transactional
    public Long createCustomer(String name) {
        Customer customer = new Customer(name);
        entityManager.persist(customer);
        return customer.getId();
    }

    @Transactional
    public long countCustomers() {
        return entityManager
                .createQuery("SELECT COUNT(c) FROM Customer c", Long.class)
                .getSingleResult();
    }
}
