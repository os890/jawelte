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
package org.os890.jawelte.tests.lnp.scenario09.service;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.os890.jawelte.tests.lnp.scenario09.entity.ecommerce.Customer;
import org.os890.jawelte.tests.lnp.scenario09.entity.ecommerce.CustomerOrder;
import org.os890.jawelte.tests.lnp.scenario09.entity.ecommerce.OrderItem;
import org.os890.jawelte.tests.lnp.scenario09.entity.ecommerce.Product;

/**
 * E-commerce domain service. {@code @Stateless} so jawelte's
 * ejb-module manages the lifecycle and implicit
 * {@code @Transactional} interceptor applies REQUIRED semantics on
 * every public method.
 */
@Stateless
public class EcommerceService {

    @Inject
    private EntityManager em;

    /** No-arg constructor required by the EJB stereotype. */
    public EcommerceService() {
    }

    /** Touch every Customer row — read-only query. */
    public void listCustomers() {
        em.createQuery("SELECT c FROM Customer c", Customer.class)
                .getResultList();
    }

    /** Touch every Product row — read-only query. */
    public void listProducts() {
        em.createQuery("SELECT p FROM Product p", Product.class)
                .getResultList();
    }

    /** Touch every CustomerOrder row — read-only query. */
    public void listOrders() {
        em.createQuery(
                "SELECT DISTINCT o FROM CustomerOrder o "
                        + "LEFT JOIN FETCH o.items",
                CustomerOrder.class).getResultList();
    }

    /** Update the email on customer N. */
    public void updateCustomerEmail(Long id, String email) {
        Customer c = em.find(Customer.class, id);
        c.setEmail(email);
        em.flush();
    }

    /** Delete order N (cascade items + dependent payment row). */
    public void deleteOrderCascade(Long orderId) {
        CustomerOrder o = em.find(CustomerOrder.class, orderId);
        em.createQuery("DELETE FROM Payment p WHERE p.order.id = :oid")
                .setParameter("oid", orderId)
                .executeUpdate();
        em.remove(o);
        em.flush();
    }

    /** Add an OrderItem to order N referencing product P. */
    public void addItem(Long orderId, Long productId, int quantity) {
        CustomerOrder o = em.find(CustomerOrder.class, orderId);
        Product prod = em.find(Product.class, productId);
        OrderItem item = new OrderItem();
        item.setOrder(o);
        item.setProduct(prod);
        item.setQuantity(quantity);
        item.setUnitPrice(prod.getPrice());
        em.persist(item);
        o.getItems().add(item);
        em.flush();
    }
}
