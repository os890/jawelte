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
package org.os890.jawelte.tests.lnp.scenario09;

import java.math.BigDecimal;

import jakarta.inject.Inject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.module.testcontrol.api.TestControl;
import org.os890.jawelte.tests.lnp.scenario09.service.ContentService;
import org.os890.jawelte.tests.lnp.scenario09.service.EcommerceService;
import org.os890.jawelte.tests.lnp.scenario09.service.FinanceService;
import org.os890.jawelte.tests.lnp.scenario09.service.HrService;
import org.os890.jawelte.tests.lnp.scenario09.service.InventoryService;

/**
 * Mirrors scenario-01-full-crud's 21 CRUD methods, but routes every
 * persistence call through a per-domain {@code @Stateless} EJB
 * service (under jawelte's ejb-module) instead of hitting an injected
 * {@code EntityManager} directly. The dbIn/dbExpected seed-and-diff
 * envelope from db-testdata-module verifies state automatically after
 * each method's transaction commits, so test bodies stay free of
 * JPQL assertions and consist of a single delegate call into the EJB.
 *
 * <p>The LNP signal this scenario contributes: the per-class overhead
 * of injecting and invoking EJB-managed services (plus the implicit
 * {@code @Transactional} interceptor jawelte's ejb-module applies on
 * {@code @Stateless} beans) compared to direct {@code EntityManager}
 * access in scenario-02.
 *
 * <p>testData layout — every {@code @TestControl} folder is a
 * self-contained pair carrying both {@code dbIn/full.xml} (the
 * input fixture) and {@code dbExpected/full.xml} (the verification
 * snapshot):
 * <ul>
 *   <li>{@code lnp-full-crud/seed/} — full ~1000-row fixture;
 *       {@code dbExpected} mirrors {@code dbIn} (DB unchanged),
 *       referenced by the read-only methods.</li>
 *   <li>{@code lnp-full-crud/method-NN-<name>/} — same seed in
 *       {@code dbIn/}, post-mutation snapshot in
 *       {@code dbExpected/}, referenced by the matching mutation
 *       method.</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractFullCrudEjbScenarioTest {

    @Inject
    private EcommerceService ecommerce;

    @Inject
    private HrService hr;

    @Inject
    private ContentService content;

    @Inject
    private FinanceService finance;

    @Inject
    private InventoryService inventory;

    /** Default constructor required by JUnit/CDI. */
    protected AbstractFullCrudEjbScenarioTest() {
    }

    // ==================== E-COMMERCE ====================

    @Test
    @Order(1)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query all customers via EJB")
    public void queryAllCustomers() {
        ecommerce.queryAllCustomers();
    }

    @Test
    @Order(2)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query products by status via EJB")
    public void queryProductsByStatus() {
        ecommerce.queryProductsByStatus();
    }

    @Test
    @Order(3)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query orders with items via EJB")
    public void queryOrdersWithItems() {
        ecommerce.queryOrdersWithItems();
    }

    @Test
    @Order(4)
    @TestControl(testData = "lnp-full-crud/method-04-update-customer-email")
    @DisplayName("Update customer email via EJB")
    public void updateCustomerEmail() {
        ecommerce.updateCustomerEmail(1L, "updated@test.com");
    }

    @Test
    @Order(5)
    @TestControl(testData = "lnp-full-crud/method-05-delete-order-cascade")
    @DisplayName("Delete an order (cascade) via EJB")
    public void deleteOrderCascade() {
        ecommerce.deleteOrderCascade(1L);
    }

    @Test
    @Order(6)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Average product price via EJB")
    public void averageProductPrice() {
        ecommerce.averageProductPrice();
    }

    @Test
    @Order(7)
    @TestControl(testData = "lnp-full-crud/method-07-add-item-to-order")
    @DisplayName("Add item to existing order via EJB")
    public void addItemToOrder() {
        ecommerce.addItemToOrder(2L, 1L, 5);
    }

    // ==================== HR ====================

    @Test
    @Order(10)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query employees by department via EJB")
    public void queryEmployeesByDepartment() {
        hr.queryEmployeesByDepartment(1L);
    }

    @Test
    @Order(11)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Count employees per department via EJB")
    public void countEmployeesPerDepartment() {
        hr.countEmployeesPerDepartment();
    }

    @Test
    @Order(12)
    @TestControl(testData = "lnp-full-crud/method-12-update-employee-department")
    @DisplayName("Update employee department via EJB")
    public void updateEmployeeDepartment() {
        hr.updateEmployeeDepartment(1L, 10L);
    }

    @Test
    @Order(13)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Average salary across all employees via EJB")
    public void averageSalary() {
        hr.averageSalary();
    }

    // ==================== CONTENT ====================

    @Test
    @Order(20)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query articles by author via EJB")
    public void queryArticlesByAuthor() {
        content.queryArticlesByAuthor(1L);
    }

    @Test
    @Order(21)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query articles with tags via EJB")
    public void queryArticlesWithTags() {
        content.queryArticlesWithTags("Tag-0");
    }

    @Test
    @Order(22)
    @TestControl(testData = "lnp-full-crud/method-22-update-article-body")
    @DisplayName("Update article body via EJB")
    public void updateArticleBody() {
        content.updateArticleBody(1L, "Updated body content for testing.");
    }

    // ==================== FINANCE ====================

    @Test
    @Order(30)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query transactions by account via EJB")
    public void queryTransactionsByAccount() {
        finance.queryTransactionsByAccount(1L);
    }

    @Test
    @Order(31)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Sum account balances via EJB")
    public void sumAccountBalances() {
        finance.sumAccountBalances();
    }

    @Test
    @Order(32)
    @TestControl(testData = "lnp-full-crud/method-32-update-account-balance")
    @DisplayName("Update account balance via EJB")
    public void updateAccountBalance() {
        finance.updateAccountBalance(1L, new BigDecimal("500"));
    }

    // ==================== INVENTORY ====================

    @Test
    @Order(40)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query stock by warehouse via EJB")
    public void queryStockByWarehouse() {
        inventory.queryStockByWarehouse(1L);
    }

    @Test
    @Order(41)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Total stock across all warehouses via EJB")
    public void totalStockQuantity() {
        inventory.totalStockQuantity();
    }

    @Test
    @Order(42)
    @TestControl(testData = "lnp-full-crud/method-42-update-stock-quantity")
    @DisplayName("Update stock quantity via EJB")
    public void updateStockQuantity() {
        inventory.updateStockQuantity(1L, 50);
    }

    // ==================== CROSS-DOMAIN ====================

    @Test
    @Order(50)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Cross-domain: touch every EJB service")
    public void allTablesPopulated() {
        ecommerce.queryAllCustomers();
        hr.countEmployeesPerDepartment();
        content.queryArticlesByAuthor(1L);
        finance.sumAccountBalances();
        inventory.totalStockQuantity();
    }
}
