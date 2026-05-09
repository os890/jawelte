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
package org.os890.jawelte.tests.jpa.scenario46;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** {@code @Transactional} CDI service that drives the test. */
@ApplicationScoped
public class CustomerOrderService {

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public CustomerOrderService() {
    }

    /**
     * Persist a {@link Customer} together with one {@link Order}
     * referencing it.
     *
     * @param customerName     the customer's name
     * @param orderDescription the order description
     * @return the persisted order's id
     */
    @Transactional
    public Long createCustomerWithOrder(String customerName, String orderDescription) {
        Customer customer = new Customer(customerName);
        entityManager.persist(customer);
        Order order = new Order(orderDescription, customer);
        entityManager.persist(order);
        return order.getId();
    }

    /**
     * Look up the order's customer name by walking the
     * {@code @ManyToOne} relationship.
     *
     * @param orderId the order id
     * @return the customer's name
     */
    @Transactional
    public String findCustomerNameForOrder(Long orderId) {
        Order order = entityManager.find(Order.class, orderId);
        if (order == null || order.getCustomer() == null) {
            return null;
        }
        return order.getCustomer().getName();
    }

    /**
     * Total {@link Customer} row count.
     *
     * @return the row count
     */
    @Transactional
    public long countCustomers() {
        return entityManager
                .createQuery("SELECT COUNT(c) FROM Customer c", Long.class)
                .getSingleResult();
    }

    /**
     * Total {@link Order} row count.
     *
     * @return the row count
     */
    @Transactional
    public long countOrders() {
        return entityManager
                .createQuery("SELECT COUNT(o) FROM Order o", Long.class)
                .getSingleResult();
    }
}
