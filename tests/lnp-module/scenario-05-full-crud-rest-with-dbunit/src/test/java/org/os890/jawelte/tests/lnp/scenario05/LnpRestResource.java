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

import java.math.BigDecimal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.os890.jawelte.tests.lnp.scenario05.entity.content.Article;
import org.os890.jawelte.tests.lnp.scenario05.entity.ecommerce.Customer;
import org.os890.jawelte.tests.lnp.scenario05.entity.ecommerce.CustomerOrder;
import org.os890.jawelte.tests.lnp.scenario05.entity.ecommerce.OrderItem;
import org.os890.jawelte.tests.lnp.scenario05.entity.ecommerce.Product;
import org.os890.jawelte.tests.lnp.scenario05.entity.finance.Account;
import org.os890.jawelte.tests.lnp.scenario05.entity.hr.Department;
import org.os890.jawelte.tests.lnp.scenario05.entity.hr.Employee;
import org.os890.jawelte.tests.lnp.scenario05.entity.inventory.StockItem;

/**
 * JAX-RS resource that exposes the same 21 CRUD operations as
 * {@code AbstractFullCrudDbUnitScenarioTest} (scenario-02) over HTTP,
 * one endpoint per test method. The resource is deployed by
 * jaxrs-module's {@code SeBootstrap}-based embedded server and is the
 * piece that turns scenario-05 from a direct-JPA test into a
 * REST-roundtrip test.
 *
 * <p>Each endpoint is {@code @Transactional} so the mutation commits
 * inside the server's request-scoped transaction, which is exactly
 * what db-testdata-module's {@code dbExpected} observer compares
 * against once the test method returns. The response payload is a
 * fixed {@code {"ok":true}} String — small, deterministic, and
 * trivial to assert against via
 * {@link org.os890.jawelte.module.jaxrs.api.ResponseDiff#forJson}.
 */
@Path("/lnp")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class LnpRestResource {

    private static final String OK = "{\"ok\":true}";

    @Inject
    private EntityManager em;

    /** Default constructor for CDI. */
    public LnpRestResource() {
    }

    // ==================== E-COMMERCE ====================

    /** Read-only query — verified by dbExpected = seed. */
    @GET
    @Path("/customers")
    @Transactional
    public String queryAllCustomers() {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        em.createQuery("SELECT c FROM Customer c ORDER BY c.id", Customer.class)
                .getResultList()
                .forEach(c -> arr.add(Json.createObjectBuilder()
                        .add("id", c.getId())
                        .add("name", c.getName())
                        .add("email", c.getEmail())));
        return arr.build().toString();
    }

    /** Read-only query — verified by dbExpected = seed. */
    @GET
    @Path("/products/by-status")
    @Transactional
    public String queryProductsByStatus(@QueryParam("status") String status) {
        em.createQuery(
                "SELECT COUNT(p) FROM Product p WHERE p.status = :s",
                Long.class)
                .setParameter("s",
                        org.os890.jawelte.tests.lnp.scenario05.entity.ecommerce
                                .ProductStatus.ACTIVE)
                .getSingleResult();
        return OK;
    }

    /** Read-only query — verified by dbExpected = seed. */
    @GET
    @Path("/orders/with-items")
    @Transactional
    public String queryOrdersWithItems() {
        em.createQuery(
                "SELECT DISTINCT o FROM CustomerOrder o LEFT JOIN FETCH o.items",
                CustomerOrder.class)
                .getResultList();
        return OK;
    }

    /** Mutation: updates customer 1's email. */
    @PUT
    @Path("/customers/{id}/email")
    @Transactional
    public String updateCustomerEmail(@PathParam("id") Long id,
                                       @QueryParam("value") String value) {
        Customer c = em.find(Customer.class, id);
        c.setEmail(value);
        em.flush();
        return OK;
    }

    /**
     * Mutation: deletes order N (cascade removes its items) plus the
     * matching payment row.
     */
    @DELETE
    @Path("/orders/{id}")
    @Transactional
    public String deleteOrderCascade(@PathParam("id") Long id) {
        CustomerOrder o = em.find(CustomerOrder.class, id);
        em.createQuery("DELETE FROM Payment p WHERE p.order.id = :oid")
                .setParameter("oid", id).executeUpdate();
        em.remove(o);
        em.flush();
        return OK;
    }

    /** Read-only aggregate — verified by dbExpected = seed. */
    @GET
    @Path("/products/avg-price")
    @Transactional
    public String averageProductPrice() {
        em.createQuery("SELECT AVG(p.price) FROM Product p", Double.class)
                .getSingleResult();
        return OK;
    }

    /** Mutation: adds one OrderItem to order N. */
    @POST
    @Path("/orders/{id}/items")
    @Transactional
    public String addItemToOrder(@PathParam("id") Long orderId,
                                  @QueryParam("productId") Long productId,
                                  @QueryParam("quantity") int quantity) {
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
        return OK;
    }

    // ==================== HR ====================

    /** Read-only query — verified by dbExpected = seed. */
    @GET
    @Path("/employees/by-department")
    @Transactional
    public String queryEmployeesByDepartment(@QueryParam("dept") Long deptId) {
        em.createQuery(
                "SELECT COUNT(e) FROM Employee e WHERE e.department.id = :d",
                Long.class)
                .setParameter("d", deptId)
                .getSingleResult();
        return OK;
    }

    /** Read-only aggregate — verified by dbExpected = seed. */
    @GET
    @Path("/employees/count-by-department")
    @Transactional
    public String countEmployeesPerDepartment() {
        em.createQuery(
                "SELECT e.department.id, COUNT(e) FROM Employee e "
                        + "GROUP BY e.department.id",
                Object[].class)
                .getResultList();
        return OK;
    }

    /** Mutation: re-assigns employee N to a different department. */
    @PUT
    @Path("/employees/{id}/department/{deptId}")
    @Transactional
    public String updateEmployeeDepartment(@PathParam("id") Long id,
                                            @PathParam("deptId") Long deptId) {
        Employee emp = em.find(Employee.class, id);
        Department dept = em.find(Department.class, deptId);
        emp.setDepartment(dept);
        em.flush();
        return OK;
    }

    /** Read-only aggregate — verified by dbExpected = seed. */
    @GET
    @Path("/employees/avg-salary")
    @Transactional
    public String averageSalary() {
        em.createQuery("SELECT COUNT(e) FROM Employee e", Long.class)
                .getSingleResult();
        return OK;
    }

    // ==================== CONTENT ====================

    /** Read-only query — verified by dbExpected = seed. */
    @GET
    @Path("/articles/by-author")
    @Transactional
    public String queryArticlesByAuthor(@QueryParam("author") Long authorId) {
        em.createQuery(
                "SELECT COUNT(a) FROM Article a WHERE a.author.id = :id",
                Long.class)
                .setParameter("id", authorId)
                .getSingleResult();
        return OK;
    }

    /** Read-only query — verified by dbExpected = seed. */
    @GET
    @Path("/articles/with-tags")
    @Transactional
    public String queryArticlesWithTags() {
        em.createQuery(
                "SELECT DISTINCT a FROM Article a LEFT JOIN FETCH a.tags",
                Article.class)
                .getResultList();
        return OK;
    }

    /** Mutation: replaces article N's body. */
    @PUT
    @Path("/articles/{id}/body")
    @Transactional
    public String updateArticleBody(@PathParam("id") Long id,
                                     @QueryParam("text") String text) {
        Article art = em.find(Article.class, id);
        art.setBody(text);
        em.flush();
        return OK;
    }

    // ==================== FINANCE ====================

    /** Read-only query — verified by dbExpected = seed. */
    @GET
    @Path("/transactions/by-account")
    @Transactional
    public String queryTransactionsByAccount(@QueryParam("account") Long acc) {
        em.createQuery(
                "SELECT COUNT(t) FROM FinancialTransaction t WHERE t.account.id = :id",
                Long.class)
                .setParameter("id", acc)
                .getSingleResult();
        return OK;
    }

    /** Read-only aggregate — verified by dbExpected = seed. */
    @GET
    @Path("/accounts/sum-balance")
    @Transactional
    public String sumAccountBalances() {
        em.createQuery(
                "SELECT SUM(a.balance) FROM Account a", BigDecimal.class)
                .getSingleResult();
        return OK;
    }

    /** Mutation: increases account N's balance by the given amount. */
    @PUT
    @Path("/accounts/{id}/balance/add")
    @Transactional
    public String updateAccountBalance(@PathParam("id") Long id,
                                        @QueryParam("amount") String amount) {
        Account acc = em.find(Account.class, id);
        acc.setBalance(acc.getBalance().add(new BigDecimal(amount)));
        em.flush();
        return OK;
    }

    // ==================== INVENTORY ====================

    /** Read-only query — verified by dbExpected = seed. */
    @GET
    @Path("/stock/by-warehouse")
    @Transactional
    public String queryStockByWarehouse(@QueryParam("warehouse") Long w) {
        em.createQuery(
                "SELECT COUNT(s) FROM StockItem s WHERE s.warehouse.id = :w",
                Long.class)
                .setParameter("w", w)
                .getSingleResult();
        return OK;
    }

    /** Read-only aggregate — verified by dbExpected = seed. */
    @GET
    @Path("/stock/total")
    @Transactional
    public String totalStockQuantity() {
        em.createQuery(
                "SELECT SUM(s.quantity) FROM StockItem s", Long.class)
                .getSingleResult();
        return OK;
    }

    /** Mutation: bumps stock item N's quantity by the given amount. */
    @PUT
    @Path("/stock/{id}/quantity/add")
    @Transactional
    public String updateStockQuantity(@PathParam("id") Long id,
                                       @QueryParam("amount") int amount) {
        StockItem si = em.find(StockItem.class, id);
        si.setQuantity(si.getQuantity() + amount);
        em.flush();
        return OK;
    }

    // ==================== CROSS-DOMAIN ====================

    /** Read-only check — verified by dbExpected = seed. */
    @GET
    @Path("/tables/populated")
    @Transactional
    public String allTablesPopulated() {
        em.createQuery("SELECT COUNT(c) FROM Customer c", Long.class)
                .getSingleResult();
        return OK;
    }
}
