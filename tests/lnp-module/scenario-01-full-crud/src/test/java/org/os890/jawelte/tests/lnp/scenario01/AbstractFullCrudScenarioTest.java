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
package org.os890.jawelte.tests.lnp.scenario01;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.tests.lnp.scenario01.entity.content.Article;
import org.os890.jawelte.tests.lnp.scenario01.entity.content.Author;
import org.os890.jawelte.tests.lnp.scenario01.entity.ecommerce.Customer;
import org.os890.jawelte.tests.lnp.scenario01.entity.ecommerce.CustomerOrder;
import org.os890.jawelte.tests.lnp.scenario01.entity.ecommerce.OrderItem;
import org.os890.jawelte.tests.lnp.scenario01.entity.ecommerce.Product;
import org.os890.jawelte.tests.lnp.scenario01.entity.ecommerce.ProductStatus;
import org.os890.jawelte.tests.lnp.scenario01.entity.finance.Account;
import org.os890.jawelte.tests.lnp.scenario01.entity.finance.FinancialTransaction;
import org.os890.jawelte.tests.lnp.scenario01.entity.hr.Department;
import org.os890.jawelte.tests.lnp.scenario01.entity.hr.Employee;
import org.os890.jawelte.tests.lnp.scenario01.entity.inventory.StockItem;
import org.os890.jawelte.tests.lnp.scenario01.entity.inventory.Warehouse;

/**
 * Tests CRUD operations on a fully populated database (all 50 tables).
 * {@link TestDataPopulator#populate(EntityManager)} is called before
 * each test method, so every method starts with a known dataset.
 *
 * This exercises: per-method truncation + re-population overhead,
 * cross-table queries, updates, deletes on a populated database.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractFullCrudScenarioTest {

    @Inject
    protected EntityManager em;

    private TestDataPopulator.PopulatedData data;
    @BeforeEach
    @Transactional
    void populateAll() {
        data = TestDataPopulator.populate(em);
    }

    // ==================== E-COMMERCE ====================

    @Test @Order(1) @Transactional
    @DisplayName("Query all customers")
    void queryAllCustomers() {
        List<Customer> customers = em.createQuery(
                "SELECT c FROM Customer c", Customer.class).getResultList();
        assertEquals(100, customers.size());
    }

    @Test @Order(2) @Transactional
    @DisplayName("Query products by status")
    void queryProductsByStatus() {
        List<Product> active = em.createQuery(
                "SELECT p FROM Product p WHERE p.status = :s", Product.class)
                .setParameter("s", ProductStatus.ACTIVE).getResultList();
        assertFalse(active.isEmpty());
    }

    @Test @Order(3) @Transactional
    @DisplayName("Query orders with items (join fetch)")
    void queryOrdersWithItems() {
        List<CustomerOrder> orders = em.createQuery(
                "SELECT DISTINCT o FROM CustomerOrder o JOIN FETCH o.items",
                CustomerOrder.class).getResultList();
        assertEquals(100, orders.size());
        orders.forEach(o -> assertFalse(o.getItems().isEmpty()));
    }

    @Test @Order(4) @Transactional
    @DisplayName("Update customer email")
    void updateCustomerEmail() {
        Long id = data.customers.get(0).getId();
        Customer c = em.find(Customer.class, id);
        c.setEmail("updated@test.com");
        em.flush();
        em.clear();
        Customer found = em.find(Customer.class, id);
        assertEquals("updated@test.com", found.getEmail());
    }

    @Test @Order(5) @Transactional
    @DisplayName("Delete an order (cascade deletes items)")
    void deleteOrderCascade() {
        // Find an order directly — all 100 orders have payments now,
        // so we delete the payment first then the order
        Long orderId = data.orders.get(0).getId();
        CustomerOrder o = em.find(CustomerOrder.class, orderId);
        assertFalse(o.getItems().isEmpty());
        // Remove payment if exists
        em.createQuery("DELETE FROM Payment p WHERE p.order.id = :oid")
                .setParameter("oid", orderId).executeUpdate();
        em.remove(o);
        em.flush();
        em.clear();
        assertNull(em.find(CustomerOrder.class, orderId));
    }

    @Test @Order(6) @Transactional
    @DisplayName("Average product price")
    void averageProductPrice() {
        Double avg = em.createQuery(
                "SELECT AVG(p.price) FROM Product p", Double.class).getSingleResult();
        assertNotNull(avg);
        assertTrue(avg > 0);
    }

    @Test @Order(7) @Transactional
    @DisplayName("Add item to existing order")
    void addItemToOrder() {
        Long orderId = data.orders.get(1).getId();
        CustomerOrder o = em.find(CustomerOrder.class, orderId);
        int before = o.getItems().size();
        Product prod = em.find(Product.class, data.products.get(0).getId());
        OrderItem item = new OrderItem();
        item.setOrder(o);
        item.setProduct(prod);
        item.setQuantity(5);
        item.setUnitPrice(prod.getPrice());
        em.persist(item);
        o.getItems().add(item);
        em.flush();
        em.clear();
        CustomerOrder found = em.find(CustomerOrder.class, orderId);
        assertEquals(before + 1, found.getItems().size());
    }

    // ==================== HR ====================

    @Test @Order(10) @Transactional
    @DisplayName("Query employees by department")
    void queryEmployeesByDepartment() {
        Department dept = em.find(Department.class, data.departments.get(0).getId());
        List<Employee> employees = em.createQuery(
                "SELECT e FROM Employee e WHERE e.department = :d", Employee.class)
                .setParameter("d", dept).getResultList();
        assertFalse(employees.isEmpty());
    }

    @Test @Order(11) @Transactional
    @DisplayName("Count employees per department")
    void countEmployeesPerDepartment() {
        List<Object[]> results = em.createQuery(
                "SELECT e.department.name, COUNT(e) FROM Employee e GROUP BY e.department.name",
                Object[].class).getResultList();
        assertFalse(results.isEmpty());
    }

    @Test @Order(12) @Transactional
    @DisplayName("Update employee department")
    void updateEmployeeDepartment() {
        Long empId = data.employees.get(0).getId();
        Long newDeptId = data.departments.get(data.departments.size() - 1).getId();
        Employee emp = em.find(Employee.class, empId);
        Department newDept = em.find(Department.class, newDeptId);
        emp.setDepartment(newDept);
        em.flush();
        em.clear();
        Employee found = em.find(Employee.class, empId);
        assertEquals(newDeptId, found.getDepartment().getId());
    }

    @Test @Order(13) @Transactional
    @DisplayName("Average salary across all employees")
    void averageSalary() {
        Double avg = em.createQuery(
                "SELECT AVG(s.amount) FROM Salary s", Double.class).getSingleResult();
        assertNotNull(avg);
    }

    // ==================== CONTENT ====================

    @Test @Order(20) @Transactional
    @DisplayName("Query articles by author")
    void queryArticlesByAuthor() {
        Author auth = em.find(Author.class, data.authors.get(0).getId());
        List<Article> articles = em.createQuery(
                "SELECT a FROM Article a WHERE a.author = :auth", Article.class)
                .setParameter("auth", auth).getResultList();
        assertFalse(articles.isEmpty());
    }

    @Test @Order(21) @Transactional
    @DisplayName("Query articles with tags (join)")
    void queryArticlesWithTags() {
        List<Article> articles = em.createQuery(
                "SELECT DISTINCT a FROM Article a JOIN a.tags t WHERE t.name = :tag",
                Article.class)
                .setParameter("tag", "Tag-0").getResultList();
        assertFalse(articles.isEmpty());
    }

    @Test @Order(22) @Transactional
    @DisplayName("Update article body")
    void updateArticleBody() {
        Long artId = data.articles.get(0).getId();
        Article art = em.find(Article.class, artId);
        art.setBody("Updated body content for testing.");
        em.flush();
        em.clear();
        Article found = em.find(Article.class, artId);
        assertEquals("Updated body content for testing.", found.getBody());
    }

    // ==================== FINANCE ====================

    @Test @Order(30) @Transactional
    @DisplayName("Query transactions by account")
    void queryTransactionsByAccount() {
        Account acc = em.find(Account.class, data.accounts.get(0).getId());
        List<FinancialTransaction> txs = em.createQuery(
                "SELECT t FROM FinancialTransaction t WHERE t.account = :acc",
                FinancialTransaction.class)
                .setParameter("acc", acc).getResultList();
        assertEquals(2, txs.size());
    }

    @Test @Order(31) @Transactional
    @DisplayName("Sum account balances")
    void sumAccountBalances() {
        BigDecimal total = em.createQuery(
                "SELECT SUM(a.balance) FROM Account a", BigDecimal.class).getSingleResult();
        assertNotNull(total);
        assertTrue(total.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test @Order(32) @Transactional
    @DisplayName("Update account balance")
    void updateAccountBalance() {
        Long accId = data.accounts.get(0).getId();
        Account acc = em.find(Account.class, accId);
        acc.setBalance(acc.getBalance().add(new BigDecimal("500")));
        em.flush();
        em.clear();
        Account found = em.find(Account.class, accId);
        assertTrue(found.getBalance().compareTo(new BigDecimal("10000")) > 0);
    }

    // ==================== INVENTORY ====================

    @Test @Order(40) @Transactional
    @DisplayName("Query stock by warehouse")
    void queryStockByWarehouse() {
        Warehouse wh = em.find(Warehouse.class, data.warehouses.get(0).getId());
        List<StockItem> stock = em.createQuery(
                "SELECT si FROM StockItem si WHERE si.warehouse = :wh", StockItem.class)
                .setParameter("wh", wh).getResultList();
        assertEquals(10, stock.size());
    }

    @Test @Order(41) @Transactional
    @DisplayName("Total stock across all warehouses")
    void totalStockQuantity() {
        Long total = em.createQuery(
                "SELECT SUM(si.quantity) FROM StockItem si", Long.class).getSingleResult();
        assertNotNull(total);
        assertTrue(total > 0);
    }

    @Test @Order(42) @Transactional
    @DisplayName("Update stock quantity")
    void updateStockQuantity() {
        StockItem si = em.createQuery(
                "SELECT si FROM StockItem si", StockItem.class)
                .setMaxResults(1).getSingleResult();
        si.setQuantity(si.getQuantity() + 50);
        em.flush();
        em.clear();
        StockItem found = em.find(StockItem.class, si.getId());
        assertTrue(found.getQuantity() > 100);
    }

    // ==================== CROSS-DOMAIN ====================

    @Test @Order(50) @Transactional
    @DisplayName("Cross-domain: count all entity tables have data")
    void allTablesPopulated() {
        assertTrue(count("Customer") > 0);
        assertTrue(count("Product") > 0);
        assertTrue(count("CustomerOrder") > 0);
        assertTrue(count("OrderItem") > 0);
        assertTrue(count("Payment") > 0);
        assertTrue(count("Review") > 0);
        assertTrue(count("Category") > 0);
        assertTrue(count("Department") > 0);
        assertTrue(count("Employee") > 0);
        assertTrue(count("Skill") > 0);
        assertTrue(count("Project") > 0);
        assertTrue(count("ProjectAssignment") > 0);
        assertTrue(count("Salary") > 0);
        assertTrue(count("LeaveRequest") > 0);
        assertTrue(count("OfficeLocation") > 0);
        assertTrue(count("Article") > 0);
        assertTrue(count("Author") > 0);
        assertTrue(count("Tag") > 0);
        assertTrue(count("Comment") > 0);
        assertTrue(count("Media") > 0);
        assertTrue(count("ContentSettings") > 0);
        assertTrue(count("ContentCategory") > 0);
        assertTrue(count("Account") > 0);
        assertTrue(count("FinancialTransaction") > 0);
        assertTrue(count("Currency") > 0);
        assertTrue(count("ExchangeRate") > 0);
        assertTrue(count("Budget") > 0);
        assertTrue(count("BudgetLine") > 0);
        assertTrue(count("Invoice") > 0);
        assertTrue(count("InvoiceLine") > 0);
        assertTrue(count("Warehouse") > 0);
        assertTrue(count("StockItem") > 0);
        assertTrue(count("Bin") > 0);
        assertTrue(count("Supplier") > 0);
        assertTrue(count("PurchaseOrder") > 0);
        assertTrue(count("PurchaseOrderLine") > 0);
        assertTrue(count("StockTransfer") > 0);
        assertTrue(count("StockTransferLine") > 0);
        // Logistics
        assertTrue(count("Shipment") > 0);
        assertTrue(count("ShipmentItem") > 0);
        assertTrue(count("Carrier") > 0);
        assertTrue(count("Route") > 0);
        assertTrue(count("DeliveryZone") > 0);
        assertTrue(count("TrackingEvent") > 0);
        assertTrue(count("PackageDimension") > 0);
        assertTrue(count("FreightRate") > 0);
        assertTrue(count("DeliveryAttempt") > 0);
        assertTrue(count("ReturnRequest") > 0);
        assertTrue(count("ReturnItem") > 0);
        assertTrue(count("ShippingLabel") > 0);
        // Marketing
        assertTrue(count("Campaign") > 0);
        assertTrue(count("CampaignChannel") > 0);
        assertTrue(count("Promotion") > 0);
        assertTrue(count("Coupon") > 0);
        assertTrue(count("CouponUsage") > 0);
        assertTrue(count("NewsletterSubscription") > 0);
        assertTrue(count("AdPlacement") > 0);
        assertTrue(count("ClickTracking") > 0);
        assertTrue(count("AbTest") > 0);
        assertTrue(count("AbTestVariant") > 0);
        assertTrue(count("LandingPage") > 0);
        assertTrue(count("LeadScore") > 0);
        // Support
        assertTrue(count("Ticket") > 0);
        assertTrue(count("TicketComment") > 0);
        assertTrue(count("TicketCategory") > 0);
        assertTrue(count("KnowledgeArticle") > 0);
        assertTrue(count("SlaPolicy") > 0);
        assertTrue(count("SlaViolation") > 0);
        assertTrue(count("ChatSession") > 0);
        assertTrue(count("ChatMessage") > 0);
        assertTrue(count("FaqEntry") > 0);
        assertTrue(count("SupportAgent") > 0);
        assertTrue(count("EscalationRule") > 0);
        assertTrue(count("SatisfactionSurvey") > 0);
        // CRM
        assertTrue(count("Contact") > 0);
        assertTrue(count("ContactGroup") > 0);
        assertTrue(count("Opportunity") > 0);
        assertTrue(count("OpportunityStage") > 0);
        assertTrue(count("Activity") > 0);
        assertTrue(count("ActivityType") > 0);
        assertTrue(count("Note") > 0);
        assertTrue(count("Pipeline") > 0);
        assertTrue(count("Deal") > 0);
        assertTrue(count("DealProduct") > 0);
        assertTrue(count("CrmCampaign") > 0);
        assertTrue(count("Interaction") > 0);
        // Analytics
        assertTrue(count("PageView") > 0);
        assertTrue(count("EventLog") > 0);
        assertTrue(count("UserSession") > 0);
        assertTrue(count("Funnel") > 0);
        assertTrue(count("FunnelStep") > 0);
        assertTrue(count("Metric") > 0);
        assertTrue(count("Dashboard") > 0);
        assertTrue(count("Widget") > 0);
        assertTrue(count("Report") > 0);
        assertTrue(count("ReportSchedule") > 0);
        assertTrue(count("DataExport") > 0);
    }

    private long count(String entity) {
        return em.createQuery("SELECT COUNT(e) FROM " + entity + " e", Long.class)
                .getSingleResult();
    }
}
