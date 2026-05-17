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
package org.os890.jawelte.tests.lnp.scenario03;

import java.math.BigDecimal;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.module.testcontrol.api.TestControl;
import org.os890.jawelte.tests.lnp.scenario03.entity.content.Article;
import org.os890.jawelte.tests.lnp.scenario03.entity.ecommerce.Customer;
import org.os890.jawelte.tests.lnp.scenario03.entity.ecommerce.CustomerOrder;
import org.os890.jawelte.tests.lnp.scenario03.entity.ecommerce.OrderItem;
import org.os890.jawelte.tests.lnp.scenario03.entity.ecommerce.Product;
import org.os890.jawelte.tests.lnp.scenario03.entity.finance.Account;
import org.os890.jawelte.tests.lnp.scenario03.entity.hr.Department;
import org.os890.jawelte.tests.lnp.scenario03.entity.hr.Employee;
import org.os890.jawelte.tests.lnp.scenario03.entity.inventory.StockItem;

/**
 * Mirrors scenario-01-full-crud's 21 CRUD methods, but seeds the
 * fixture via db-testdata-module's {@code @TestControl(testData=...)}
 * (dbIn/*.xml) and asserts via {@code DbDiff} against
 * dbExpected/*.xml. The test method bodies perform the same
 * mutations but never run JPQL assertions — the dbExpected phase
 * handles verification automatically after the method's transaction
 * commits.
 *
 * <p>testData layout:
 * <ul>
 *   <li>{@code lnp-full-crud/seed/dbIn/full.xml} — full ~1000-row
 *       fixture, loaded by every test method.</li>
 *   <li>{@code lnp-full-crud/query-only/dbExpected/full.xml} —
 *       identical to the seed; referenced by the 14 query-only
 *       methods that do not mutate the database.</li>
 *   <li>{@code lnp-full-crud/method-NN-<name>/dbExpected/full.xml}
 *       — full post-mutation state for one of the 7 mutation
 *       methods.</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractFullCrudAllModulesScenarioTest {

    @Inject
    private EntityManager em;

    /** Default constructor required by JUnit/CDI. */
    protected AbstractFullCrudAllModulesScenarioTest() {
    }

    // ==================== E-COMMERCE ====================

    /** Read-only query — verified by dbExpected = seed. */
    @Test
    @Order(1)
    @Transactional
    @TestControl(testData = {"lnp-full-crud/seed", "lnp-full-crud/query-only"})
    @DisplayName("Query all customers")
    public void queryAllCustomers() {
        // No-op: dbExpected verifies the seed remained unchanged.
    }

    /** Read-only query — verified by dbExpected = seed. */
    @Test
    @Order(2)
    @Transactional
    @TestControl(testData = {"lnp-full-crud/seed", "lnp-full-crud/query-only"})
    @DisplayName("Query products by status")
    public void queryProductsByStatus() {
        // No-op.
    }

    /** Read-only query — verified by dbExpected = seed. */
    @Test
    @Order(3)
    @Transactional
    @TestControl(testData = {"lnp-full-crud/seed", "lnp-full-crud/query-only"})
    @DisplayName("Query orders with items (join fetch)")
    public void queryOrdersWithItems() {
        // No-op.
    }

    /** Mutation: updates customer 1's email. */
    @Test
    @Order(4)
    @Transactional
    @TestControl(testData = {"lnp-full-crud/seed",
            "lnp-full-crud/method-04-update-customer-email"})
    @DisplayName("Update customer email")
    public void updateCustomerEmail() {
        Customer c = em.find(Customer.class, 1L);
        c.setEmail("updated@test.com");
        em.flush();
    }

    /**
     * Mutation: deletes order 1 (cascade removes its items) plus the
     * matching payment row.
     */
    @Test
    @Order(5)
    @Transactional
    @TestControl(testData = {"lnp-full-crud/seed",
            "lnp-full-crud/method-05-delete-order-cascade"})
    @DisplayName("Delete an order (cascade deletes items)")
    public void deleteOrderCascade() {
        Long orderId = 1L;
        CustomerOrder o = em.find(CustomerOrder.class, orderId);
        em.createQuery("DELETE FROM Payment p WHERE p.order.id = :oid")
                .setParameter("oid", orderId).executeUpdate();
        em.remove(o);
        em.flush();
    }

    /** Read-only aggregate — verified by dbExpected = seed. */
    @Test
    @Order(6)
    @Transactional
    @TestControl(testData = {"lnp-full-crud/seed", "lnp-full-crud/query-only"})
    @DisplayName("Average product price")
    public void averageProductPrice() {
        // No-op.
    }

    /** Mutation: adds one OrderItem to order 2. */
    @Test
    @Order(7)
    @Transactional
    @TestControl(testData = {"lnp-full-crud/seed",
            "lnp-full-crud/method-07-add-item-to-order"})
    @DisplayName("Add item to existing order")
    public void addItemToOrder() {
        Long orderId = 2L;
        CustomerOrder o = em.find(CustomerOrder.class, orderId);
        Product prod = em.find(Product.class, 1L);
        OrderItem item = new OrderItem();
        item.setOrder(o);
        item.setProduct(prod);
        item.setQuantity(5);
        item.setUnitPrice(prod.getPrice());
        em.persist(item);
        o.getItems().add(item);
        em.flush();
    }

    // ==================== HR ====================

    /** Read-only query — verified by dbExpected = seed. */
    @Test
    @Order(10)
    @Transactional
    @TestControl(testData = {"lnp-full-crud/seed", "lnp-full-crud/query-only"})
    @DisplayName("Query employees by department")
    public void queryEmployeesByDepartment() {
        // No-op.
    }

    /** Read-only aggregate — verified by dbExpected = seed. */
    @Test
    @Order(11)
    @Transactional
    @TestControl(testData = {"lnp-full-crud/seed", "lnp-full-crud/query-only"})
    @DisplayName("Count employees per department")
    public void countEmployeesPerDepartment() {
        // No-op.
    }

    /** Mutation: re-assigns employee 1 to the last department. */
    @Test
    @Order(12)
    @Transactional
    @TestControl(testData = {"lnp-full-crud/seed",
            "lnp-full-crud/method-12-update-employee-department"})
    @DisplayName("Update employee department")
    public void updateEmployeeDepartment() {
        Employee emp = em.find(Employee.class, 1L);
        Department lastDept = em.find(Department.class, 10L);
        emp.setDepartment(lastDept);
        em.flush();
    }

    /** Read-only aggregate — verified by dbExpected = seed. */
    @Test
    @Order(13)
    @Transactional
    @TestControl(testData = {"lnp-full-crud/seed", "lnp-full-crud/query-only"})
    @DisplayName("Average salary across all employees")
    public void averageSalary() {
        // No-op.
    }

    // ==================== CONTENT ====================

    /** Read-only query — verified by dbExpected = seed. */
    @Test
    @Order(20)
    @Transactional
    @TestControl(testData = {"lnp-full-crud/seed", "lnp-full-crud/query-only"})
    @DisplayName("Query articles by author")
    public void queryArticlesByAuthor() {
        // No-op.
    }

    /** Read-only query — verified by dbExpected = seed. */
    @Test
    @Order(21)
    @Transactional
    @TestControl(testData = {"lnp-full-crud/seed", "lnp-full-crud/query-only"})
    @DisplayName("Query articles with tags (join)")
    public void queryArticlesWithTags() {
        // No-op.
    }

    /** Mutation: replaces article 1's body. */
    @Test
    @Order(22)
    @Transactional
    @TestControl(testData = {"lnp-full-crud/seed",
            "lnp-full-crud/method-22-update-article-body"})
    @DisplayName("Update article body")
    public void updateArticleBody() {
        Article art = em.find(Article.class, 1L);
        art.setBody("Updated body content for testing.");
        em.flush();
    }

    // ==================== FINANCE ====================

    /** Read-only query — verified by dbExpected = seed. */
    @Test
    @Order(30)
    @Transactional
    @TestControl(testData = {"lnp-full-crud/seed", "lnp-full-crud/query-only"})
    @DisplayName("Query transactions by account")
    public void queryTransactionsByAccount() {
        // No-op.
    }

    /** Read-only aggregate — verified by dbExpected = seed. */
    @Test
    @Order(31)
    @Transactional
    @TestControl(testData = {"lnp-full-crud/seed", "lnp-full-crud/query-only"})
    @DisplayName("Sum account balances")
    public void sumAccountBalances() {
        // No-op.
    }

    /** Mutation: increases account 1's balance by 500. */
    @Test
    @Order(32)
    @Transactional
    @TestControl(testData = {"lnp-full-crud/seed",
            "lnp-full-crud/method-32-update-account-balance"})
    @DisplayName("Update account balance")
    public void updateAccountBalance() {
        Account acc = em.find(Account.class, 1L);
        acc.setBalance(acc.getBalance().add(new BigDecimal("500")));
        em.flush();
    }

    // ==================== INVENTORY ====================

    /** Read-only query — verified by dbExpected = seed. */
    @Test
    @Order(40)
    @Transactional
    @TestControl(testData = {"lnp-full-crud/seed", "lnp-full-crud/query-only"})
    @DisplayName("Query stock by warehouse")
    public void queryStockByWarehouse() {
        // No-op.
    }

    /** Read-only aggregate — verified by dbExpected = seed. */
    @Test
    @Order(41)
    @Transactional
    @TestControl(testData = {"lnp-full-crud/seed", "lnp-full-crud/query-only"})
    @DisplayName("Total stock across all warehouses")
    public void totalStockQuantity() {
        // No-op.
    }

    /** Mutation: bumps stock item 1's quantity by 50. */
    @Test
    @Order(42)
    @Transactional
    @TestControl(testData = {"lnp-full-crud/seed",
            "lnp-full-crud/method-42-update-stock-quantity"})
    @DisplayName("Update stock quantity")
    public void updateStockQuantity() {
        StockItem si = em.find(StockItem.class, 1L);
        si.setQuantity(si.getQuantity() + 50);
        em.flush();
    }

    // ==================== CROSS-DOMAIN ====================

    /** Read-only check — verified by dbExpected = seed. */
    @Test
    @Order(50)
    @Transactional
    @TestControl(testData = {"lnp-full-crud/seed", "lnp-full-crud/query-only"})
    @DisplayName("Cross-domain: count all entity tables have data")
    public void allTablesPopulated() {
        // No-op.
    }
}
