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
import org.os890.jawelte.module.contentdiff.api.ContentDiff;
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
        getJson("/lnp/products/by-status?status=ACTIVE",
                "lnp-full-crud/expected-responses/queryProductsByStatus.json");
    }

    /** Read-only query — verified by dbExpected = seed. */
    @Test
    @Order(3)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query orders with items via REST")
    public void queryOrdersWithItems() {
        getJson("/lnp/orders/with-items",
                "lnp-full-crud/expected-responses/queryOrdersWithItems.json");
    }

    /** Mutation: updates customer 1's email via REST. */
    @Test
    @Order(4)
    @TestControl(testData = "lnp-full-crud/method-04-update-customer-email")
    @DisplayName("Update customer email via REST")
    public void updateCustomerEmail() {
        putJson("/lnp/customers/1/email?value=updated@test.com",
                "lnp-full-crud/expected-responses/updateCustomerEmail.json");
    }

    /** Mutation: deletes order 1 (cascade removes its items) via REST. */
    @Test
    @Order(5)
    @TestControl(testData = "lnp-full-crud/method-05-delete-order-cascade")
    @DisplayName("Delete an order (cascade) via REST")
    public void deleteOrderCascade() {
        deleteJson("/lnp/orders/1",
                "lnp-full-crud/expected-responses/deleteOrderCascade.json");
    }

    /** Read-only aggregate — verified by dbExpected = seed. */
    @Test
    @Order(6)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Average product price via REST")
    public void averageProductPrice() {
        getJson("/lnp/products/avg-price",
                "lnp-full-crud/expected-responses/averageProductPrice.json");
    }

    /** Mutation: adds one OrderItem to order 2 via REST. */
    @Test
    @Order(7)
    @TestControl(testData = "lnp-full-crud/method-07-add-item-to-order")
    @DisplayName("Add item to existing order via REST")
    public void addItemToOrder() {
        postJson("/lnp/orders/2/items?productId=1&quantity=5",
                "lnp-full-crud/expected-responses/addItemToOrder.json");
    }

    // ==================== HR ====================

    /** Read-only query — verified by dbExpected = seed. */
    @Test
    @Order(10)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query employees by department via REST")
    public void queryEmployeesByDepartment() {
        getJson("/lnp/employees/by-department?dept=1",
                "lnp-full-crud/expected-responses/queryEmployeesByDepartment.json");
    }

    /** Read-only aggregate — verified by dbExpected = seed. */
    @Test
    @Order(11)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Count employees per department via REST")
    public void countEmployeesPerDepartment() {
        getJson("/lnp/employees/count-by-department",
                "lnp-full-crud/expected-responses/countEmployeesPerDepartment.json");
    }

    /** Mutation: re-assigns employee 1 to dept 10 via REST. */
    @Test
    @Order(12)
    @TestControl(testData = "lnp-full-crud/method-12-update-employee-department")
    @DisplayName("Update employee department via REST")
    public void updateEmployeeDepartment() {
        putJson("/lnp/employees/1/department/10",
                "lnp-full-crud/expected-responses/updateEmployeeDepartment.json");
    }

    /** Read-only aggregate — verified by dbExpected = seed. */
    @Test
    @Order(13)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Average salary via REST")
    public void averageSalary() {
        getJson("/lnp/employees/avg-salary",
                "lnp-full-crud/expected-responses/averageSalary.json");
    }

    // ==================== CONTENT ====================

    /** Read-only query — verified by dbExpected = seed. */
    @Test
    @Order(20)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query articles by author via REST")
    public void queryArticlesByAuthor() {
        getJson("/lnp/articles/by-author?author=1",
                "lnp-full-crud/expected-responses/queryArticlesByAuthor.json");
    }

    /** Read-only query — verified by dbExpected = seed. */
    @Test
    @Order(21)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query articles with tags via REST")
    public void queryArticlesWithTags() {
        getJson("/lnp/articles/with-tags",
                "lnp-full-crud/expected-responses/queryArticlesWithTags.json");
    }

    /** Mutation: replaces article 1's body via REST. */
    @Test
    @Order(22)
    @TestControl(testData = "lnp-full-crud/method-22-update-article-body")
    @DisplayName("Update article body via REST")
    public void updateArticleBody() {
        putJson("/lnp/articles/1/body?text=Updated+body+content+for+testing.",
                "lnp-full-crud/expected-responses/updateArticleBody.json");
    }

    // ==================== FINANCE ====================

    /** Read-only query — verified by dbExpected = seed. */
    @Test
    @Order(30)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query transactions by account via REST")
    public void queryTransactionsByAccount() {
        getJson("/lnp/transactions/by-account?account=1",
                "lnp-full-crud/expected-responses/queryTransactionsByAccount.json");
    }

    /** Read-only aggregate — verified by dbExpected = seed. */
    @Test
    @Order(31)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Sum account balances via REST")
    public void sumAccountBalances() {
        getJson("/lnp/accounts/sum-balance",
                "lnp-full-crud/expected-responses/sumAccountBalances.json");
    }

    /** Mutation: bumps account 1's balance by 500 via REST. */
    @Test
    @Order(32)
    @TestControl(testData = "lnp-full-crud/method-32-update-account-balance")
    @DisplayName("Update account balance via REST")
    public void updateAccountBalance() {
        putJson("/lnp/accounts/1/balance/add?amount=500",
                "lnp-full-crud/expected-responses/updateAccountBalance.json");
    }

    // ==================== INVENTORY ====================

    /** Read-only query — verified by dbExpected = seed. */
    @Test
    @Order(40)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Query stock by warehouse via REST")
    public void queryStockByWarehouse() {
        getJson("/lnp/stock/by-warehouse?warehouse=1",
                "lnp-full-crud/expected-responses/queryStockByWarehouse.json");
    }

    /** Read-only aggregate — verified by dbExpected = seed. */
    @Test
    @Order(41)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Total stock quantity via REST")
    public void totalStockQuantity() {
        getJson("/lnp/stock/total",
                "lnp-full-crud/expected-responses/totalStockQuantity.json");
    }

    /** Mutation: bumps stock item 1's quantity by 50 via REST. */
    @Test
    @Order(42)
    @TestControl(testData = "lnp-full-crud/method-42-update-stock-quantity")
    @DisplayName("Update stock quantity via REST")
    public void updateStockQuantity() {
        putJson("/lnp/stock/1/quantity/add?amount=50",
                "lnp-full-crud/expected-responses/updateStockQuantity.json");
    }

    // ==================== CROSS-DOMAIN ====================

    /** Read-only check — verified by dbExpected = seed. */
    @Test
    @Order(50)
    @TestControl(testData = "lnp-full-crud/seed")
    @DisplayName("Cross-domain populated check via REST")
    public void allTablesPopulated() {
        getJson("/lnp/tables/populated",
                "lnp-full-crud/expected-responses/allTablesPopulated.json");
    }

    // ==================== HTTP helpers ====================
    //
    // Every helper triggers the endpoint and compares the JSON
    // response against an expected file under
    // src/test/resources/lnp-full-crud/expected-responses/. The
    // actual response is also dumped to target/responses/ for fast
    // iteration when the expected fixture drifts (see dumpActual
    // below — copy that file over to the resources directory and
    // re-run).

    private void getJson(String pathAndQuery, String classpathResource) {
        invokeAndAssertExpected("GET", pathAndQuery, null, classpathResource);
    }

    private void putJson(String pathAndQuery, String classpathResource) {
        invokeAndAssertExpected("PUT", pathAndQuery, "", classpathResource);
    }

    private void postJson(String pathAndQuery, String classpathResource) {
        invokeAndAssertExpected("POST", pathAndQuery, "", classpathResource);
    }

    private void deleteJson(String pathAndQuery, String classpathResource) {
        invokeAndAssertExpected("DELETE", pathAndQuery, null, classpathResource);
    }

    private void invokeAndAssertExpected(String method, String pathAndQuery,
                                          String body,
                                          String classpathResource) {
        // Dump the actual response body to target/responses/<methodName>.json
        // alongside running the diff. The disk dump is unconditional —
        // when the expected file is missing or out of date the dump on
        // disk can be promoted to the expected file directly, avoiding
        // brittle scraping of the surefire-reports XML.
        invokeJson(method, pathAndQuery, body, response -> {
            String actual = response.readEntity(String.class);
            dumpActual(classpathResource, actual);
            ContentDiff.forJson(actual)
                    .expected(classpathResource)
                    .assertEquals();
        });
    }

    private static void dumpActual(String classpathResource, String body) {
        java.nio.file.Path target = java.nio.file.Path.of(
                "target", "responses",
                classpathResource.substring(classpathResource.lastIndexOf('/') + 1));
        try {
            java.nio.file.Files.createDirectories(target.getParent());
            java.nio.file.Files.writeString(target, body);
        } catch (java.io.IOException ignored) {
            // Best-effort capture for dev convenience; never let it
            // mask the underlying assertion result.
        }
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
