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
package org.os890.jawelte.tests.lnp.scenario08.repository;

import org.os890.jawelte.tests.lnp.scenario08.entity.content.Article;
import org.os890.jawelte.tests.lnp.scenario08.entity.ecommerce.Customer;
import org.os890.jawelte.tests.lnp.scenario08.entity.ecommerce.CustomerOrder;
import org.os890.jawelte.tests.lnp.scenario08.entity.ecommerce.OrderItem;
import org.os890.jawelte.tests.lnp.scenario08.entity.ecommerce.Payment;
import org.os890.jawelte.tests.lnp.scenario08.entity.ecommerce.Product;
import org.os890.jawelte.tests.lnp.scenario08.entity.finance.Account;
import org.os890.jawelte.tests.lnp.scenario08.entity.hr.Department;
import org.os890.jawelte.tests.lnp.scenario08.entity.hr.Employee;
import org.os890.jawelte.tests.lnp.scenario08.entity.inventory.StockItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Aggregator for the per-entity Spring Data repositories used by
 * scenario-08. Each repository is auto-discovered by
 * spring-data-module's CDI extension and injected into the abstract
 * test base.
 */
public abstract class CrudRepositories {

    /** Static-utility holder, never instantiated. */
    protected CrudRepositories() {
    }

    /** Customer repository. */
    public interface CustomerRepository extends JpaRepository<Customer, Long> {
    }

    /** Product repository. */
    public interface ProductRepository extends JpaRepository<Product, Long> {
    }

    /** Customer-order repository. */
    public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    }

    /** Order-item repository. */
    public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    }

    /** Department repository. */
    public interface DepartmentRepository extends JpaRepository<Department, Long> {
    }

    /** Employee repository. */
    public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    }

    /** Article repository. */
    public interface ArticleRepository extends JpaRepository<Article, Long> {
    }

    /** Account repository. */
    public interface AccountRepository extends JpaRepository<Account, Long> {
    }

    /** Stock-item repository. */
    public interface StockItemRepository extends JpaRepository<StockItem, Long> {
    }

    /**
     * Payment repository — adds a derived bulk-delete by order id so
     * the deleteOrderCascade scenario can remove the dependent
     * payment row before the order itself.
     */
    public interface PaymentRepository extends JpaRepository<Payment, Long> {
        /**
         * Delete every payment row attached to the given order. The
         * explicit {@code @Param} binding is required because Java's
         * default class-file format doesn't retain method-parameter
         * names, so Spring Data can't reflectively resolve
         * {@code :orderId} → {@code Long orderId} without it (compile
         * would otherwise need {@code -parameters}, which the rest of
         * the build doesn't enable).
         *
         * @param orderId the foreign-key id
         */
        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("DELETE FROM Payment p WHERE p.order.id = :orderId")
        void deleteByOrderId(@Param("orderId") Long orderId);
    }
}
