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
package org.os890.jawelte.tests.lnp.scenario05;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.module.jaxrs.api.ResponseDiff;
import org.os890.jawelte.module.jaxrs.api.TestUrl;
import org.os890.jawelte.module.testcontrol.api.TestControl;

/**
 * Mirrors scenario-02's 21 CRUD methods but every method goes through
 * a JAX-RS endpoint instead of touching {@code EntityManager} directly.
 *
 * <p>Each method exercises three jawelte features at once:
 * <ul>
 *   <li>{@code @TestControl(testData = ...)} from
 *       testcontrol-module / db-testdata-module: seeds {@code dbIn/}
 *       before the method, compares the DB to {@code dbExpected/}
 *       afterwards.</li>
 *   <li>{@code @EnableJaxRs}'s embedded {@code SeBootstrap} server
 *       (from jaxrs-module) plus the {@link LnpRestResource} the
 *       numbered subclasses register: every test method sends an
 *       HTTP request to the same in-process server, hitting one
 *       endpoint per CRUD operation.</li>
 *   <li>{@link ResponseDiff#forJson} (jaxrs-module → content-diff
 *       bridge): every response is asserted to equal
 *       {@code {"ok":true}}, exercising the JSON diff path.</li>
 * </ul>
 *
 * <p>The test method itself is intentionally NOT
 * {@code @Transactional}: the server-side endpoint commits its own
 * mutation; testcontrol's seed and diff observers each run in their
 * own transaction around the method, so no test-side transaction is
 * required.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractFullCrudRestDbUnitScenarioTest {

    @Inject
    private TestUrl testUrl;

    /** Default constructor required by JUnit/CDI. */
    protected AbstractFullCrudRestDbUnitScenarioTest() {
    }

    // ==================== E-COMMERCE ====================

    /** Read-only query — verified by dbExpected = seed. */
    @Test
    @Order(1)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query all customers via REST")
    public void queryAllCustomers() {
        getJson("/lnp/customers", "lnp-full-crud/expected-responses/queryAllCustomers.json");
    }

    /** Read-only query — verified by dbExpected = seed. */
    @Test
    @Order(2)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query products by status via REST")
    public void queryProductsByStatus() {
        get("/lnp/products/by-status?status=ACTIVE");
    }

    /** Read-only query — verified by dbExpected = seed. */
    @Test
    @Order(3)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query orders with items via REST")
    public void queryOrdersWithItems() {
        get("/lnp/orders/with-items");
    }

    /** Mutation: updates customer 1's email via REST. */
    @Test
    @Order(4)
    @TestControl(testData = "lnp-full-crud/method-04-update-customer-email")
    @DisplayName("Update customer email via REST")
    public void updateCustomerEmail() {
        put("/lnp/customers/1/email?value=updated@test.com");
    }

    /** Mutation: deletes order 1 (cascade removes its items) via REST. */
    @Test
    @Order(5)
    @TestControl(testData = "lnp-full-crud/method-05-delete-order-cascade")
    @DisplayName("Delete an order (cascade) via REST")
    public void deleteOrderCascade() {
        delete("/lnp/orders/1");
    }

    /** Read-only aggregate — verified by dbExpected = seed. */
    @Test
    @Order(6)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Average product price via REST")
    public void averageProductPrice() {
        get("/lnp/products/avg-price");
    }

    /** Mutation: adds one OrderItem to order 2 via REST. */
    @Test
    @Order(7)
    @TestControl(testData = "lnp-full-crud/method-07-add-item-to-order")
    @DisplayName("Add item to existing order via REST")
    public void addItemToOrder() {
        post("/lnp/orders/2/items?productId=1&quantity=5");
    }

    // ==================== HR ====================

    /** Read-only query — verified by dbExpected = seed. */
    @Test
    @Order(10)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query employees by department via REST")
    public void queryEmployeesByDepartment() {
        get("/lnp/employees/by-department?dept=1");
    }

    /** Read-only aggregate — verified by dbExpected = seed. */
    @Test
    @Order(11)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Count employees per department via REST")
    public void countEmployeesPerDepartment() {
        get("/lnp/employees/count-by-department");
    }

    /** Mutation: re-assigns employee 1 to dept 10 via REST. */
    @Test
    @Order(12)
    @TestControl(testData = "lnp-full-crud/method-12-update-employee-department")
    @DisplayName("Update employee department via REST")
    public void updateEmployeeDepartment() {
        put("/lnp/employees/1/department/10");
    }

    /** Read-only aggregate — verified by dbExpected = seed. */
    @Test
    @Order(13)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Average salary via REST")
    public void averageSalary() {
        get("/lnp/employees/avg-salary");
    }

    // ==================== CONTENT ====================

    /** Read-only query — verified by dbExpected = seed. */
    @Test
    @Order(20)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query articles by author via REST")
    public void queryArticlesByAuthor() {
        get("/lnp/articles/by-author?author=1");
    }

    /** Read-only query — verified by dbExpected = seed. */
    @Test
    @Order(21)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query articles with tags via REST")
    public void queryArticlesWithTags() {
        get("/lnp/articles/with-tags");
    }

    /** Mutation: replaces article 1's body via REST. */
    @Test
    @Order(22)
    @TestControl(testData = "lnp-full-crud/method-22-update-article-body")
    @DisplayName("Update article body via REST")
    public void updateArticleBody() {
        put("/lnp/articles/1/body?text=Updated+body+content+for+testing.");
    }

    // ==================== FINANCE ====================

    /** Read-only query — verified by dbExpected = seed. */
    @Test
    @Order(30)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query transactions by account via REST")
    public void queryTransactionsByAccount() {
        get("/lnp/transactions/by-account?account=1");
    }

    /** Read-only aggregate — verified by dbExpected = seed. */
    @Test
    @Order(31)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Sum account balances via REST")
    public void sumAccountBalances() {
        get("/lnp/accounts/sum-balance");
    }

    /** Mutation: bumps account 1's balance by 500 via REST. */
    @Test
    @Order(32)
    @TestControl(testData = "lnp-full-crud/method-32-update-account-balance")
    @DisplayName("Update account balance via REST")
    public void updateAccountBalance() {
        put("/lnp/accounts/1/balance/add?amount=500");
    }

    // ==================== INVENTORY ====================

    /** Read-only query — verified by dbExpected = seed. */
    @Test
    @Order(40)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query stock by warehouse via REST")
    public void queryStockByWarehouse() {
        get("/lnp/stock/by-warehouse?warehouse=1");
    }

    /** Read-only aggregate — verified by dbExpected = seed. */
    @Test
    @Order(41)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Total stock quantity via REST")
    public void totalStockQuantity() {
        get("/lnp/stock/total");
    }

    /** Mutation: bumps stock item 1's quantity by 50 via REST. */
    @Test
    @Order(42)
    @TestControl(testData = "lnp-full-crud/method-42-update-stock-quantity")
    @DisplayName("Update stock quantity via REST")
    public void updateStockQuantity() {
        put("/lnp/stock/1/quantity/add?amount=50");
    }

    // ==================== CROSS-DOMAIN ====================

    /** Read-only check — verified by dbExpected = seed. */
    @Test
    @Order(50)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Cross-domain populated check via REST")
    public void allTablesPopulated() {
        get("/lnp/tables/populated");
    }

    // ==================== HTTP helpers ====================

    private void get(String pathAndQuery) {
        invokeAndAssertOk("GET", pathAndQuery, null);
    }

    private void put(String pathAndQuery) {
        invokeAndAssertOk("PUT", pathAndQuery, "");
    }

    private void post(String pathAndQuery) {
        invokeAndAssertOk("POST", pathAndQuery, "");
    }

    private void delete(String pathAndQuery) {
        invokeAndAssertOk("DELETE", pathAndQuery, null);
    }

    /**
     * GET the URL and compare the JSON response against the expected
     * payload loaded from {@code classpathResource}. The endpoint is
     * expected to return realistic entity-shaped JSON (large enough
     * that inline {@code expectedContent} would be unreadable), and
     * {@link ResponseDiff} drives the structural diff.
     *
     * @param pathAndQuery     URL path + query (e.g.
     *                         {@code "/lnp/customers"})
     * @param classpathResource classpath path of the expected JSON
     *                         file (e.g.
     *                         {@code "lnp-full-crud/expected-responses/queryAllCustomers.json"})
     */
    private void getJson(String pathAndQuery, String classpathResource) {
        invokeAndAssertExpected("GET", pathAndQuery, null, classpathResource);
    }

    private void invokeAndAssertOk(String method, String pathAndQuery,
                                    String body) {
        invokeJson(method, pathAndQuery, body, response ->
                ResponseDiff.forJson(response)
                        .expectedContent("{\"ok\":true}")
                        .assertEquals());
    }

    private void invokeAndAssertExpected(String method, String pathAndQuery,
                                          String body,
                                          String classpathResource) {
        invokeJson(method, pathAndQuery, body, response ->
                ResponseDiff.forJson(response)
                        .expected(classpathResource)
                        .assertEquals());
    }

    private void invokeJson(String method, String pathAndQuery, String body,
                             java.util.function.Consumer<Response> assertion) {
        String url = testUrl.get() + pathAndQuery;
        try (Client client = ClientBuilder.newClient()) {
            Invocation.Builder request = client.target(url)
                    .request(MediaType.APPLICATION_JSON);
            Response response = (body == null)
                    ? request.method(method)
                    : request.method(method,
                            Entity.entity(body, MediaType.APPLICATION_JSON));
            try (Response r = response) {
                assertion.accept(r);
            }
        }
    }
}
