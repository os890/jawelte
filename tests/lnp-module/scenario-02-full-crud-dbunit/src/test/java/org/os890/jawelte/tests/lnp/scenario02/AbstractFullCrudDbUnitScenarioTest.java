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
package org.os890.jawelte.tests.lnp.scenario02;

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
import org.os890.jawelte.tests.lnp.scenario02.entity.content.Article;
import org.os890.jawelte.tests.lnp.scenario02.entity.ecommerce.Customer;
import org.os890.jawelte.tests.lnp.scenario02.entity.ecommerce.CustomerOrder;
import org.os890.jawelte.tests.lnp.scenario02.entity.ecommerce.OrderItem;
import org.os890.jawelte.tests.lnp.scenario02.entity.ecommerce.Product;
import org.os890.jawelte.tests.lnp.scenario02.entity.ecommerce.ProductStatus;
import org.os890.jawelte.tests.lnp.scenario02.entity.finance.Account;
import org.os890.jawelte.tests.lnp.scenario02.entity.finance.FinancialTransaction;
import org.os890.jawelte.tests.lnp.scenario02.entity.hr.Department;
import org.os890.jawelte.tests.lnp.scenario02.entity.hr.Employee;
import org.os890.jawelte.tests.lnp.scenario02.entity.inventory.StockItem;

/**
 * Mirrors scenario-01-full-crud's 21 CRUD methods, but seeds the
 * fixture via db-testdata-module's {@code @TestControl(testData=...)}
 * (dbIn/*.xml) and asserts via {@code DbDiff} against
 * dbExpected/*.xml. Every test body runs the same JPQL as the
 * scenario-01 baseline — only the in-test {@code assertX(...)} calls
 * are dropped, because the dbExpected phase handles verification
 * automatically after the method's transaction commits.
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
public abstract class AbstractFullCrudDbUnitScenarioTest {

    @Inject
    private EntityManager em;

    /** Default constructor required by JUnit/CDI. */
    protected AbstractFullCrudDbUnitScenarioTest() {
    }

    // ==================== E-COMMERCE ====================

    @Test
    @Order(1)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query all customers")
    public void queryAllCustomers() {
        em.createQuery("SELECT c FROM Customer c", Customer.class)
                .getResultList();
    }

    @Test
    @Order(2)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query products by status")
    public void queryProductsByStatus() {
        em.createQuery(
                "SELECT p FROM Product p WHERE p.status = :s",
                Product.class)
                .setParameter("s", ProductStatus.ACTIVE)
                .getResultList();
    }

    @Test
    @Order(3)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query orders with items (join fetch)")
    public void queryOrdersWithItems() {
        em.createQuery(
                "SELECT DISTINCT o FROM CustomerOrder o JOIN FETCH o.items",
                CustomerOrder.class)
                .getResultList();
    }

    @Test
    @Order(4)
    @Transactional
    @TestControl(testData = "lnp-full-crud/method-04-update-customer-email")
    @DisplayName("Update customer email")
    public void updateCustomerEmail() {
        Customer c = em.find(Customer.class, 1L);
        c.setEmail("updated@test.com");
        em.flush();
    }

    @Test
    @Order(5)
    @Transactional
    @TestControl(testData = "lnp-full-crud/method-05-delete-order-cascade")
    @DisplayName("Delete an order (cascade deletes items)")
    public void deleteOrderCascade() {
        Long orderId = 1L;
        CustomerOrder o = em.find(CustomerOrder.class, orderId);
        em.createQuery("DELETE FROM Payment p WHERE p.order.id = :oid")
                .setParameter("oid", orderId).executeUpdate();
        em.remove(o);
        em.flush();
    }

    @Test
    @Order(6)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Average product price")
    public void averageProductPrice() {
        em.createQuery(
                "SELECT AVG(p.price) FROM Product p", Double.class)
                .getSingleResult();
    }

    @Test
    @Order(7)
    @Transactional
    @TestControl(testData = "lnp-full-crud/method-07-add-item-to-order")
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

    @Test
    @Order(10)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query employees by department")
    public void queryEmployeesByDepartment() {
        em.createQuery(
                "SELECT e FROM Employee e WHERE e.department.id = :d",
                Employee.class)
                .setParameter("d", 1L)
                .getResultList();
    }

    @Test
    @Order(11)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Count employees per department")
    public void countEmployeesPerDepartment() {
        em.createQuery(
                "SELECT e.department.name, COUNT(e) FROM Employee e "
                        + "GROUP BY e.department.name",
                Object[].class)
                .getResultList();
    }

    @Test
    @Order(12)
    @Transactional
    @TestControl(testData = "lnp-full-crud/method-12-update-employee-department")
    @DisplayName("Update employee department")
    public void updateEmployeeDepartment() {
        Employee emp = em.find(Employee.class, 1L);
        Department lastDept = em.find(Department.class, 10L);
        emp.setDepartment(lastDept);
        em.flush();
    }

    @Test
    @Order(13)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Average salary across all employees")
    public void averageSalary() {
        em.createQuery(
                "SELECT AVG(s.amount) FROM Salary s", Double.class)
                .getSingleResult();
    }

    // ==================== CONTENT ====================

    @Test
    @Order(20)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query articles by author")
    public void queryArticlesByAuthor() {
        em.createQuery(
                "SELECT a FROM Article a WHERE a.author.id = :id",
                Article.class)
                .setParameter("id", 1L)
                .getResultList();
    }

    @Test
    @Order(21)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query articles with tags (join)")
    public void queryArticlesWithTags() {
        em.createQuery(
                "SELECT DISTINCT a FROM Article a JOIN a.tags t "
                        + "WHERE t.name = :tag",
                Article.class)
                .setParameter("tag", "Tag-0")
                .getResultList();
    }

    @Test
    @Order(22)
    @Transactional
    @TestControl(testData = "lnp-full-crud/method-22-update-article-body")
    @DisplayName("Update article body")
    public void updateArticleBody() {
        Article art = em.find(Article.class, 1L);
        art.setBody("Updated body content for testing.");
        em.flush();
    }

    // ==================== FINANCE ====================

    @Test
    @Order(30)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query transactions by account")
    public void queryTransactionsByAccount() {
        em.createQuery(
                "SELECT t FROM FinancialTransaction t WHERE t.account.id = :id",
                FinancialTransaction.class)
                .setParameter("id", 1L)
                .getResultList();
    }

    @Test
    @Order(31)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Sum account balances")
    public void sumAccountBalances() {
        em.createQuery(
                "SELECT SUM(a.balance) FROM Account a", BigDecimal.class)
                .getSingleResult();
    }

    @Test
    @Order(32)
    @Transactional
    @TestControl(testData = "lnp-full-crud/method-32-update-account-balance")
    @DisplayName("Update account balance")
    public void updateAccountBalance() {
        Account acc = em.find(Account.class, 1L);
        acc.setBalance(acc.getBalance().add(new BigDecimal("500")));
        em.flush();
    }

    // ==================== INVENTORY ====================

    @Test
    @Order(40)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query stock by warehouse")
    public void queryStockByWarehouse() {
        em.createQuery(
                "SELECT si FROM StockItem si WHERE si.warehouse.id = :id",
                StockItem.class)
                .setParameter("id", 1L)
                .getResultList();
    }

    @Test
    @Order(41)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Total stock across all warehouses")
    public void totalStockQuantity() {
        em.createQuery(
                "SELECT SUM(si.quantity) FROM StockItem si", Long.class)
                .getSingleResult();
    }

    @Test
    @Order(42)
    @Transactional
    @TestControl(testData = "lnp-full-crud/method-42-update-stock-quantity")
    @DisplayName("Update stock quantity")
    public void updateStockQuantity() {
        StockItem si = em.find(StockItem.class, 1L);
        si.setQuantity(si.getQuantity() + 50);
        em.flush();
    }

    // ==================== CROSS-DOMAIN ====================

    @Test
    @Order(50)
    @Transactional
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Cross-domain: count every populated table")
    public void allTablesPopulated() {
        for (String entity : ENTITIES) {
            count(entity);
        }
    }

    private long count(String entity) {
        return em.createQuery(
                "SELECT COUNT(e) FROM " + entity + " e", Long.class)
                .getSingleResult();
    }

    private static final String[] ENTITIES = {
            "Customer", "Product", "CustomerOrder", "OrderItem", "Payment",
            "Review", "Category",
            "Department", "Employee", "Skill", "Project", "ProjectAssignment",
            "Salary", "LeaveRequest", "OfficeLocation",
            "Article", "Author", "Tag", "Comment", "Media",
            "ContentSettings", "ContentCategory",
            "Account", "FinancialTransaction", "Currency", "ExchangeRate",
            "Budget", "BudgetLine", "Invoice", "InvoiceLine",
            "Warehouse", "StockItem", "Bin", "Supplier",
            "PurchaseOrder", "PurchaseOrderLine",
            "StockTransfer", "StockTransferLine",
            "Shipment", "ShipmentItem", "Carrier", "Route", "DeliveryZone",
            "TrackingEvent", "PackageDimension", "FreightRate",
            "DeliveryAttempt", "ReturnRequest", "ReturnItem", "ShippingLabel",
            "Campaign", "CampaignChannel", "Promotion", "Coupon",
            "CouponUsage", "NewsletterSubscription", "AdPlacement",
            "ClickTracking", "AbTest", "AbTestVariant",
            "LandingPage", "LeadScore",
            "Ticket", "TicketComment", "TicketCategory", "KnowledgeArticle",
            "SlaPolicy", "SlaViolation", "ChatSession", "ChatMessage",
            "FaqEntry", "SupportAgent", "EscalationRule", "SatisfactionSurvey",
            "Contact", "ContactGroup", "Opportunity", "OpportunityStage",
            "Activity", "ActivityType", "Note", "Pipeline",
            "Deal", "DealProduct", "CrmCampaign", "Interaction",
            "PageView", "EventLog", "UserSession", "Funnel", "FunnelStep",
            "Metric", "Dashboard", "Widget", "Report", "ReportSchedule",
            "DataExport"
    };
}
