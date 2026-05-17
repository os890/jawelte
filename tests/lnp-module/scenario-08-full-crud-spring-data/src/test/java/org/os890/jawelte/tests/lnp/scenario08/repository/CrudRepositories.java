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

import java.math.BigDecimal;
import java.util.List;

import org.os890.jawelte.tests.lnp.scenario08.entity.content.Article;
import org.os890.jawelte.tests.lnp.scenario08.entity.ecommerce.Customer;
import org.os890.jawelte.tests.lnp.scenario08.entity.ecommerce.CustomerOrder;
import org.os890.jawelte.tests.lnp.scenario08.entity.ecommerce.OrderItem;
import org.os890.jawelte.tests.lnp.scenario08.entity.ecommerce.Payment;
import org.os890.jawelte.tests.lnp.scenario08.entity.ecommerce.Product;
import org.os890.jawelte.tests.lnp.scenario08.entity.ecommerce.ProductStatus;
import org.os890.jawelte.tests.lnp.scenario08.entity.finance.Account;
import org.os890.jawelte.tests.lnp.scenario08.entity.finance.FinancialTransaction;
import org.os890.jawelte.tests.lnp.scenario08.entity.hr.Department;
import org.os890.jawelte.tests.lnp.scenario08.entity.hr.Employee;
import org.os890.jawelte.tests.lnp.scenario08.entity.hr.Salary;
import org.os890.jawelte.tests.lnp.scenario08.entity.inventory.StockItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Aggregator for the per-entity Spring Data repositories used by
 * scenario-08. Each repository is auto-discovered by
 * spring-data-module's CDI extension and injected into the abstract
 * test base. Method names mirror scenario-01's CRUD test methods 1-on-1
 * so every scenario-08 read-only body is a single delegate call into
 * the matching repo — derived queries for simple filters, explicit
 * {@code @Query} for joins and aggregates.
 *
 * <p>Every {@code @Query} carrying a {@code :name} placeholder pairs
 * the parameter with {@code @Param("name")} because Java's default
 * class-file format doesn't retain method-parameter names at runtime
 * and the rest of the build doesn't enable {@code -parameters}.
 */
public abstract class CrudRepositories {

    /** Static-utility holder, never instantiated. */
    protected CrudRepositories() {
    }

    /** Customer repository. */
    public interface CustomerRepository extends JpaRepository<Customer, Long> {
    }

    /** Product repository — name-aligned with scenario-01's product queries. */
    public interface ProductRepository extends JpaRepository<Product, Long> {
        /**
         * Derived query — SELECT p FROM Product p WHERE p.status = ?.
         *
         * @param status the product status to filter by
         * @return all products whose status equals {@code status}
         */
        List<Product> findByStatus(ProductStatus status);

        /**
         * Aggregate — SELECT AVG(p.price) FROM Product p.
         *
         * @return the average product price (or null if the table is empty)
         */
        @Query("SELECT AVG(p.price) FROM Product p")
        Double averagePrice();
    }

    /** Customer-order repository — name-aligned with scenario-01. */
    public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
        /**
         * Mirrors scenario-01's {@code queryOrdersWithItems} — distinct
         * orders with their items eagerly fetched in one query.
         *
         * @return every order with its {@code items} collection
         *         already populated (avoids the N+1 lazy-load round-trip)
         */
        @Query("SELECT DISTINCT o FROM CustomerOrder o JOIN FETCH o.items")
        List<CustomerOrder> findAllWithItems();
    }

    /** Order-item repository. */
    public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    }

    /** Department repository. */
    public interface DepartmentRepository extends JpaRepository<Department, Long> {
    }

    /** Employee repository — name-aligned with scenario-01. */
    public interface EmployeeRepository extends JpaRepository<Employee, Long> {
        /**
         * Derived query — SELECT e FROM Employee e WHERE e.department.id = ?.
         *
         * @param departmentId the foreign-key id of the department
         * @return employees in that department
         */
        List<Employee> findByDepartmentId(Long departmentId);

        /**
         * Aggregate — group employees by department and count.
         *
         * @return rows of {@code [department.name, count]}
         */
        @Query("SELECT e.department.name, COUNT(e) FROM Employee e "
                + "GROUP BY e.department.name")
        List<Object[]> countPerDepartment();
    }

    /** Salary repository — exists only so {@code averageSalary} touches the right table. */
    public interface SalaryRepository extends JpaRepository<Salary, Long> {
        /**
         * Aggregate — SELECT AVG(s.amount) FROM Salary s.
         *
         * @return the average salary across all employees
         */
        @Query("SELECT AVG(s.amount) FROM Salary s")
        Double averageAmount();
    }

    /** Article repository — name-aligned with scenario-01. */
    public interface ArticleRepository extends JpaRepository<Article, Long> {
        /**
         * Derived query — SELECT a FROM Article a WHERE a.author.id = ?.
         *
         * @param authorId the foreign-key id of the author
         * @return articles by that author
         */
        List<Article> findByAuthorId(Long authorId);

        /**
         * Mirrors scenario-01's {@code queryArticlesWithTags} — distinct
         * articles joined to their tag set, filtered by tag name.
         *
         * @param tagName the tag to filter by (e.g. {@code "Tag-0"})
         * @return articles carrying that tag
         */
        @Query("SELECT DISTINCT a FROM Article a JOIN a.tags t "
                + "WHERE t.name = :tagName")
        List<Article> findByTagName(@Param("tagName") String tagName);
    }

    /** Account repository — name-aligned with scenario-01. */
    public interface AccountRepository extends JpaRepository<Account, Long> {
        /**
         * Aggregate — SELECT SUM(a.balance) FROM Account a.
         *
         * @return the sum of every account balance
         */
        @Query("SELECT SUM(a.balance) FROM Account a")
        BigDecimal sumBalances();
    }

    /** Financial-transaction repository — required for scenario-01's by-account read. */
    public interface FinancialTransactionRepository
            extends JpaRepository<FinancialTransaction, Long> {
        /**
         * Derived query — SELECT t FROM FinancialTransaction t
         * WHERE t.account.id = ?.
         *
         * @param accountId the foreign-key id of the account
         * @return transactions on that account
         */
        List<FinancialTransaction> findByAccountId(Long accountId);
    }

    /** Stock-item repository — name-aligned with scenario-01. */
    public interface StockItemRepository extends JpaRepository<StockItem, Long> {
        /**
         * Derived query — SELECT si FROM StockItem si WHERE si.warehouse.id = ?.
         *
         * @param warehouseId the foreign-key id of the warehouse
         * @return stock items in that warehouse
         */
        List<StockItem> findByWarehouseId(Long warehouseId);

        /**
         * Aggregate — SELECT SUM(si.quantity) FROM StockItem si.
         *
         * @return the total stock quantity across every warehouse
         */
        @Query("SELECT SUM(si.quantity) FROM StockItem si")
        Long totalQuantity();
    }

    /**
     * Payment repository — adds a bulk-delete by order id so the
     * deleteOrderCascade scenario can remove the dependent payment row
     * before the order itself.
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
