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
import jakarta.json.JsonObjectBuilder;
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
import org.os890.jawelte.tests.lnp.scenario05.entity.ecommerce.ProductStatus;
import org.os890.jawelte.tests.lnp.scenario05.entity.finance.Account;
import org.os890.jawelte.tests.lnp.scenario05.entity.finance.FinancialTransaction;
import org.os890.jawelte.tests.lnp.scenario05.entity.hr.Department;
import org.os890.jawelte.tests.lnp.scenario05.entity.hr.Employee;
import org.os890.jawelte.tests.lnp.scenario05.entity.inventory.StockItem;

/**
 * JAX-RS resource exposing the 21 CRUD operations of scenario-02
 * over HTTP, one endpoint per test method. Every endpoint returns a
 * realistic entity-shaped JSON payload built via JSON-P (jakarta.json
 * + Parsson) — the abstract test base asserts each response against a
 * file under {@code src/test/resources/lnp-full-crud/expected-responses/}
 * using {@code ResponseDiff.forJson(...).expected(path).assertEquals()}.
 *
 * <p>Each endpoint is {@code @Transactional} so the mutation commits
 * inside the server's request-scoped transaction. db-testdata-module's
 * {@code dbExpected} observer compares the DB state independently
 * after the test method returns; the two assertions (HTTP response
 * shape + post-mutation DB content) cover both layers.
 */
@Path("/lnp")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class LnpRestResource {

    @Inject
    private EntityManager em;

    /** Default constructor for CDI. */
    public LnpRestResource() {
    }

    // ==================== E-COMMERCE ====================

    /** Read-only query — every customer, ordered by id. */
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

    /** Read-only query — products filtered by status. */
    @GET
    @Path("/products/by-status")
    @Transactional
    public String queryProductsByStatus(@QueryParam("status") String status) {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        em.createQuery(
                "SELECT p FROM Product p WHERE p.status = :s ORDER BY p.id",
                Product.class)
                .setParameter("s", ProductStatus.valueOf(status))
                .getResultList()
                .forEach(p -> arr.add(Json.createObjectBuilder()
                        .add("id", p.getId())
                        .add("sku", p.getSku())
                        .add("name", p.getName())
                        .add("status", p.getStatus().name())
                        .add("price", p.getPrice())));
        return arr.build().toString();
    }

    /** Read-only query — orders with their item count. */
    @GET
    @Path("/orders/with-items")
    @Transactional
    public String queryOrdersWithItems() {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        em.createQuery(
                "SELECT DISTINCT o FROM CustomerOrder o "
                        + "LEFT JOIN FETCH o.items ORDER BY o.id",
                CustomerOrder.class)
                .getResultList()
                .forEach(o -> {
                    JsonObjectBuilder row = Json.createObjectBuilder()
                            .add("id", o.getId())
                            .add("customerId", o.getCustomer().getId())
                            .add("totalAmount", o.getTotalAmount())
                            .add("itemCount", o.getItems().size());
                    if (o.getStatus() != null) {
                        row.add("status", o.getStatus().name());
                    }
                    arr.add(row);
                });
        return arr.build().toString();
    }

    /** Mutation: updates customer N's email. */
    @PUT
    @Path("/customers/{id}/email")
    @Transactional
    public String updateCustomerEmail(@PathParam("id") Long id,
                                       @QueryParam("value") String value) {
        Customer c = em.find(Customer.class, id);
        c.setEmail(value);
        em.flush();
        return Json.createObjectBuilder()
                .add("id", c.getId())
                .add("name", c.getName())
                .add("email", c.getEmail())
                .build().toString();
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
        Long customerId = o.getCustomer().getId();
        em.createQuery("DELETE FROM Payment p WHERE p.order.id = :oid")
                .setParameter("oid", id).executeUpdate();
        em.remove(o);
        em.flush();
        return Json.createObjectBuilder()
                .add("deletedId", id)
                .add("customerId", customerId)
                .build().toString();
    }

    /** Read-only aggregate — average product price across all products. */
    @GET
    @Path("/products/avg-price")
    @Transactional
    public String averageProductPrice() {
        Double avg = em.createQuery(
                "SELECT AVG(p.price) FROM Product p", Double.class)
                .getSingleResult();
        return Json.createObjectBuilder()
                .add("avg", BigDecimal.valueOf(avg)
                        .setScale(2, java.math.RoundingMode.HALF_UP))
                .build().toString();
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
        return Json.createObjectBuilder()
                .add("id", item.getId())
                .add("orderId", orderId)
                .add("productId", productId)
                .add("quantity", quantity)
                .add("unitPrice", item.getUnitPrice())
                .build().toString();
    }

    // ==================== HR ====================

    /** Read-only query — employees in a specific department. */
    @GET
    @Path("/employees/by-department")
    @Transactional
    public String queryEmployeesByDepartment(@QueryParam("dept") Long deptId) {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        em.createQuery(
                "SELECT e FROM Employee e WHERE e.department.id = :d ORDER BY e.id",
                Employee.class)
                .setParameter("d", deptId)
                .getResultList()
                .forEach(e -> arr.add(Json.createObjectBuilder()
                        .add("id", e.getId())
                        .add("firstName", e.getFirstName())
                        .add("lastName", e.getLastName())
                        .add("departmentId", e.getDepartment().getId())));
        return arr.build().toString();
    }

    /** Read-only aggregate — employee count per department. */
    @GET
    @Path("/employees/count-by-department")
    @Transactional
    public String countEmployeesPerDepartment() {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        em.createQuery(
                "SELECT e.department.id, COUNT(e) FROM Employee e "
                        + "GROUP BY e.department.id ORDER BY e.department.id",
                Object[].class)
                .getResultList()
                .forEach(row -> arr.add(Json.createObjectBuilder()
                        .add("departmentId", (Long) row[0])
                        .add("count", (Long) row[1])));
        return arr.build().toString();
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
        return Json.createObjectBuilder()
                .add("id", emp.getId())
                .add("firstName", emp.getFirstName())
                .add("lastName", emp.getLastName())
                .add("departmentId", emp.getDepartment().getId())
                .build().toString();
    }

    /** Read-only aggregate — total employee count (substitute for avg-salary). */
    @GET
    @Path("/employees/avg-salary")
    @Transactional
    public String averageSalary() {
        Long count = em.createQuery(
                "SELECT COUNT(e) FROM Employee e", Long.class)
                .getSingleResult();
        return Json.createObjectBuilder()
                .add("count", count)
                .build().toString();
    }

    // ==================== CONTENT ====================

    /** Read-only query — articles by a specific author. */
    @GET
    @Path("/articles/by-author")
    @Transactional
    public String queryArticlesByAuthor(@QueryParam("author") Long authorId) {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        em.createQuery(
                "SELECT a FROM Article a WHERE a.author.id = :id ORDER BY a.id",
                Article.class)
                .setParameter("id", authorId)
                .getResultList()
                .forEach(a -> arr.add(Json.createObjectBuilder()
                        .add("id", a.getId())
                        .add("title", a.getTitle())
                        .add("authorId", a.getAuthor().getId())));
        return arr.build().toString();
    }

    /** Read-only query — first 20 articles, omitting tag detail. */
    @GET
    @Path("/articles/with-tags")
    @Transactional
    public String queryArticlesWithTags() {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        em.createQuery(
                "SELECT DISTINCT a FROM Article a LEFT JOIN FETCH a.tags "
                        + "ORDER BY a.id",
                Article.class)
                .setMaxResults(20)
                .getResultList()
                .forEach(a -> {
                    JsonArrayBuilder tagIds = Json.createArrayBuilder();
                    a.getTags().forEach(t -> tagIds.add(t.getId()));
                    arr.add(Json.createObjectBuilder()
                            .add("id", a.getId())
                            .add("title", a.getTitle())
                            .add("tagIds", tagIds));
                });
        return arr.build().toString();
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
        return Json.createObjectBuilder()
                .add("id", art.getId())
                .add("title", art.getTitle())
                .add("body", art.getBody())
                .build().toString();
    }

    // ==================== FINANCE ====================

    /** Read-only query — transactions for a specific account. */
    @GET
    @Path("/transactions/by-account")
    @Transactional
    public String queryTransactionsByAccount(@QueryParam("account") Long acc) {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        em.createQuery(
                "SELECT t FROM FinancialTransaction t "
                        + "WHERE t.account.id = :id ORDER BY t.id",
                FinancialTransaction.class)
                .setParameter("id", acc)
                .getResultList()
                .forEach(t -> {
                    JsonObjectBuilder row = Json.createObjectBuilder()
                            .add("id", t.getId())
                            .add("amount", t.getAmount());
                    if (t.getType() != null) {
                        row.add("type", t.getType().name());
                    }
                    arr.add(row);
                });
        return arr.build().toString();
    }

    /** Read-only aggregate — sum of every account's balance. */
    @GET
    @Path("/accounts/sum-balance")
    @Transactional
    public String sumAccountBalances() {
        BigDecimal sum = em.createQuery(
                "SELECT SUM(a.balance) FROM Account a", BigDecimal.class)
                .getSingleResult();
        return Json.createObjectBuilder()
                .add("sum", sum.setScale(2, java.math.RoundingMode.HALF_UP))
                .build().toString();
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
        JsonObjectBuilder out = Json.createObjectBuilder()
                .add("id", acc.getId())
                .add("name", acc.getName())
                .add("balance", acc.getBalance());
        if (acc.getAccountNumber() != null) {
            out.add("accountNumber", acc.getAccountNumber());
        }
        return out.build().toString();
    }

    // ==================== INVENTORY ====================

    /** Read-only query — stock items in a specific warehouse. */
    @GET
    @Path("/stock/by-warehouse")
    @Transactional
    public String queryStockByWarehouse(@QueryParam("warehouse") Long w) {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        em.createQuery(
                "SELECT s FROM StockItem s WHERE s.warehouse.id = :w ORDER BY s.id",
                StockItem.class)
                .setParameter("w", w)
                .getResultList()
                .forEach(s -> arr.add(Json.createObjectBuilder()
                        .add("id", s.getId())
                        .add("productSku", s.getProductSku())
                        .add("quantity", s.getQuantity())
                        .add("warehouseId", s.getWarehouse().getId())));
        return arr.build().toString();
    }

    /** Read-only aggregate — total stock quantity across all warehouses. */
    @GET
    @Path("/stock/total")
    @Transactional
    public String totalStockQuantity() {
        Long total = em.createQuery(
                "SELECT SUM(s.quantity) FROM StockItem s", Long.class)
                .getSingleResult();
        return Json.createObjectBuilder()
                .add("total", total)
                .build().toString();
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
        return Json.createObjectBuilder()
                .add("id", si.getId())
                .add("productSku", si.getProductSku())
                .add("quantity", si.getQuantity())
                .build().toString();
    }

    // ==================== CROSS-DOMAIN ====================

    /** Read-only check — quick header counts across primary tables. */
    @GET
    @Path("/tables/populated")
    @Transactional
    public String allTablesPopulated() {
        Long customers = em.createQuery(
                "SELECT COUNT(c) FROM Customer c", Long.class).getSingleResult();
        Long products = em.createQuery(
                "SELECT COUNT(p) FROM Product p", Long.class).getSingleResult();
        Long orders = em.createQuery(
                "SELECT COUNT(o) FROM CustomerOrder o", Long.class).getSingleResult();
        Long employees = em.createQuery(
                "SELECT COUNT(e) FROM Employee e", Long.class).getSingleResult();
        return Json.createObjectBuilder()
                .add("customers", customers)
                .add("products", products)
                .add("orders", orders)
                .add("employees", employees)
                .build().toString();
    }
}
