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
import org.os890.jawelte.tests.lnp.scenario09.entity.ecommerce.ProductStatus;

/**
 * E-commerce domain service. {@code @Stateless} so jawelte's
 * ejb-module manages the lifecycle and implicit
 * {@code @Transactional} interceptor applies REQUIRED semantics on
 * every public method. Method names mirror scenario-01's CRUD test
 * methods 1-on-1 so every scenario-09 test body is a single delegate
 * call into this EJB.
 */
@Stateless
public class EcommerceService {

    @Inject
    private EntityManager em;

    /** No-arg constructor required by the EJB stereotype. */
    public EcommerceService() {
    }

    /** SELECT c FROM Customer c. */
    public void queryAllCustomers() {
        em.createQuery("SELECT c FROM Customer c", Customer.class)
                .getResultList();
    }

    /** SELECT p FROM Product p WHERE p.status = ACTIVE. */
    public void queryProductsByStatus() {
        em.createQuery(
                "SELECT p FROM Product p WHERE p.status = :s",
                Product.class)
                .setParameter("s", ProductStatus.ACTIVE)
                .getResultList();
    }

    /** SELECT DISTINCT o FROM CustomerOrder o JOIN FETCH o.items. */
    public void queryOrdersWithItems() {
        em.createQuery(
                "SELECT DISTINCT o FROM CustomerOrder o JOIN FETCH o.items",
                CustomerOrder.class)
                .getResultList();
    }

    /** SELECT AVG(p.price) FROM Product p. */
    public void averageProductPrice() {
        em.createQuery(
                "SELECT AVG(p.price) FROM Product p", Double.class)
                .getSingleResult();
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
    public void addItemToOrder(Long orderId, Long productId, int quantity) {
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
