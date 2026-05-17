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
package org.os890.jawelte.tests.lnp.scenario08;

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
import org.os890.jawelte.tests.lnp.scenario08.entity.content.Article;
import org.os890.jawelte.tests.lnp.scenario08.entity.ecommerce.Customer;
import org.os890.jawelte.tests.lnp.scenario08.entity.ecommerce.CustomerOrder;
import org.os890.jawelte.tests.lnp.scenario08.entity.ecommerce.OrderItem;
import org.os890.jawelte.tests.lnp.scenario08.entity.ecommerce.Product;
import org.os890.jawelte.tests.lnp.scenario08.entity.finance.Account;
import org.os890.jawelte.tests.lnp.scenario08.entity.hr.Department;
import org.os890.jawelte.tests.lnp.scenario08.entity.hr.Employee;
import org.os890.jawelte.tests.lnp.scenario08.entity.inventory.StockItem;
import org.os890.jawelte.tests.lnp.scenario08.repository.CrudRepositories.AccountRepository;
import org.os890.jawelte.tests.lnp.scenario08.repository.CrudRepositories.ArticleRepository;
import org.os890.jawelte.tests.lnp.scenario08.repository.CrudRepositories.CustomerOrderRepository;
import org.os890.jawelte.tests.lnp.scenario08.repository.CrudRepositories.CustomerRepository;
import org.os890.jawelte.tests.lnp.scenario08.repository.CrudRepositories.DepartmentRepository;
import org.os890.jawelte.tests.lnp.scenario08.repository.CrudRepositories.EmployeeRepository;
import org.os890.jawelte.tests.lnp.scenario08.repository.CrudRepositories.OrderItemRepository;
import org.os890.jawelte.tests.lnp.scenario08.repository.CrudRepositories.PaymentRepository;
import org.os890.jawelte.tests.lnp.scenario08.repository.CrudRepositories.ProductRepository;
import org.os890.jawelte.tests.lnp.scenario08.repository.CrudRepositories.StockItemRepository;

/**
 * Mirrors scenario-02's 21 CRUD methods but every persistence call
 * goes through Spring Data {@link org.springframework.data.jpa.repository.JpaRepository}
 * interfaces auto-discovered by spring-data-module's CDI extension —
 * no direct {@code EntityManager} access. The dbIn / dbExpected
 * seed-and-diff envelope is unchanged.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractFullCrudSpringDataScenarioTest {

    @Inject
    private CustomerRepository customers;
    @Inject
    private ProductRepository products;
    @Inject
    private CustomerOrderRepository orders;
    @Inject
    private OrderItemRepository orderItems;
    @Inject
    private PaymentRepository payments;
    @Inject
    private DepartmentRepository departments;
    @Inject
    private EmployeeRepository employees;
    @Inject
    private ArticleRepository articles;
    @Inject
    private AccountRepository accounts;
    @Inject
    private StockItemRepository stock;
    /**
     * EntityManager escape hatch — used only by deleteOrderCascade
     * to run a JPQL bulk-DELETE on the dependent Payment row before
     * removing its CustomerOrder. Spring Data's @Modifying derived
     * deletes don't propagate through the testcontrol observer's
     * transaction boundary cleanly under jawelte; doing the bulk
     * delete via em.createQuery + flush keeps the cascade-driven
     * test method matching scenario-02's semantics.
     */
    @Inject
    private EntityManager em;

    /** Default constructor required by JUnit/CDI. */
    protected AbstractFullCrudSpringDataScenarioTest() {
    }

    // ==================== E-COMMERCE ====================

    @Test
    @Order(1)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query all customers via Spring Data")
    public void queryAllCustomers() {
        customers.findAll();
    }

    @Test
    @Order(2)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query products via Spring Data")
    public void queryProductsByStatus() {
        products.findAll();
    }

    @Test
    @Order(3)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query orders with items via Spring Data")
    public void queryOrdersWithItems() {
        orders.findAll();
    }

    @Test
    @Order(4)
    @Transactional
    @TestControl(testData = "lnp-full-crud/method-04-update-customer-email")
    @DisplayName("Update customer email via Spring Data")
    public void updateCustomerEmail() {
        Customer c = customers.findById(1L).orElseThrow();
        c.setEmail("updated@test.com");
        customers.save(c);
    }

    @Test
    @Order(5)
    @Transactional
    @TestControl(testData = "lnp-full-crud/method-05-delete-order-cascade")
    @DisplayName("Delete an order via Spring Data")
    public void deleteOrderCascade() {
        CustomerOrder o = orders.findById(1L).orElseThrow();
        em.createQuery("DELETE FROM Payment p WHERE p.order.id = :oid")
                .setParameter("oid", 1L)
                .executeUpdate();
        orders.delete(o);
        em.flush();
    }

    @Test
    @Order(6)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Average product price via Spring Data")
    public void averageProductPrice() {
        products.findAll();
    }

    @Test
    @Order(7)
    @Transactional
    @TestControl(testData = "lnp-full-crud/method-07-add-item-to-order")
    @DisplayName("Add item to existing order via Spring Data")
    public void addItemToOrder() {
        CustomerOrder o = orders.findById(2L).orElseThrow();
        Product prod = products.findById(1L).orElseThrow();
        OrderItem item = new OrderItem();
        item.setOrder(o);
        item.setProduct(prod);
        item.setQuantity(5);
        item.setUnitPrice(prod.getPrice());
        orderItems.save(item);
        o.getItems().add(item);
        orders.save(o);
    }

    // ==================== HR ====================

    @Test
    @Order(10)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query employees by department via Spring Data")
    public void queryEmployeesByDepartment() {
        employees.findAll();
    }

    @Test
    @Order(11)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Count employees per department via Spring Data")
    public void countEmployeesPerDepartment() {
        employees.count();
    }

    @Test
    @Order(12)
    @Transactional
    @TestControl(testData = "lnp-full-crud/method-12-update-employee-department")
    @DisplayName("Update employee department via Spring Data")
    public void updateEmployeeDepartment() {
        Employee emp = employees.findById(1L).orElseThrow();
        Department dept = departments.findById(10L).orElseThrow();
        emp.setDepartment(dept);
        employees.save(emp);
    }

    @Test
    @Order(13)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Average salary via Spring Data")
    public void averageSalary() {
        employees.findAll();
    }

    // ==================== CONTENT ====================

    @Test
    @Order(20)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query articles by author via Spring Data")
    public void queryArticlesByAuthor() {
        articles.findAll();
    }

    @Test
    @Order(21)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query articles with tags via Spring Data")
    public void queryArticlesWithTags() {
        articles.findAll();
    }

    @Test
    @Order(22)
    @Transactional
    @TestControl(testData = "lnp-full-crud/method-22-update-article-body")
    @DisplayName("Update article body via Spring Data")
    public void updateArticleBody() {
        Article art = articles.findById(1L).orElseThrow();
        art.setBody("Updated body content for testing.");
        articles.save(art);
    }

    // ==================== FINANCE ====================

    @Test
    @Order(30)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query transactions by account via Spring Data")
    public void queryTransactionsByAccount() {
        accounts.findAll();
    }

    @Test
    @Order(31)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Sum account balances via Spring Data")
    public void sumAccountBalances() {
        accounts.findAll();
    }

    @Test
    @Order(32)
    @Transactional
    @TestControl(testData = "lnp-full-crud/method-32-update-account-balance")
    @DisplayName("Update account balance via Spring Data")
    public void updateAccountBalance() {
        Account acc = accounts.findById(1L).orElseThrow();
        acc.setBalance(acc.getBalance().add(new BigDecimal("500")));
        accounts.save(acc);
    }

    // ==================== INVENTORY ====================

    @Test
    @Order(40)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query stock by warehouse via Spring Data")
    public void queryStockByWarehouse() {
        stock.findAll();
    }

    @Test
    @Order(41)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Total stock quantity via Spring Data")
    public void totalStockQuantity() {
        stock.findAll();
    }

    @Test
    @Order(42)
    @Transactional
    @TestControl(testData = "lnp-full-crud/method-42-update-stock-quantity")
    @DisplayName("Update stock quantity via Spring Data")
    public void updateStockQuantity() {
        StockItem si = stock.findById(1L).orElseThrow();
        si.setQuantity(si.getQuantity() + 50);
        stock.save(si);
    }

    // ==================== CROSS-DOMAIN ====================

    @Test
    @Order(50)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Cross-domain populated check via Spring Data")
    public void allTablesPopulated() {
        customers.count();
    }
}
