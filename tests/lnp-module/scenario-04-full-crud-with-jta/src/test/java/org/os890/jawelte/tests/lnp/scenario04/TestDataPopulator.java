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
package org.os890.jawelte.tests.lnp.scenario04;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.os890.jawelte.tests.lnp.scenario04.entity.analytics.Dashboard;
import org.os890.jawelte.tests.lnp.scenario04.entity.analytics.DataExport;
import org.os890.jawelte.tests.lnp.scenario04.entity.analytics.EventLog;
import org.os890.jawelte.tests.lnp.scenario04.entity.analytics.Funnel;
import org.os890.jawelte.tests.lnp.scenario04.entity.analytics.FunnelStep;
import org.os890.jawelte.tests.lnp.scenario04.entity.analytics.Metric;
import org.os890.jawelte.tests.lnp.scenario04.entity.analytics.PageView;
import org.os890.jawelte.tests.lnp.scenario04.entity.analytics.Report;
import org.os890.jawelte.tests.lnp.scenario04.entity.analytics.ReportSchedule;
import org.os890.jawelte.tests.lnp.scenario04.entity.analytics.UserSession;
import org.os890.jawelte.tests.lnp.scenario04.entity.analytics.Widget;
import org.os890.jawelte.tests.lnp.scenario04.entity.content.Article;
import org.os890.jawelte.tests.lnp.scenario04.entity.content.Author;
import org.os890.jawelte.tests.lnp.scenario04.entity.content.Comment;
import org.os890.jawelte.tests.lnp.scenario04.entity.content.ContentCategory;
import org.os890.jawelte.tests.lnp.scenario04.entity.content.ContentSettings;
import org.os890.jawelte.tests.lnp.scenario04.entity.content.ContentVersion;
import org.os890.jawelte.tests.lnp.scenario04.entity.content.Media;
import org.os890.jawelte.tests.lnp.scenario04.entity.content.Rating;
import org.os890.jawelte.tests.lnp.scenario04.entity.content.Tag;
import org.os890.jawelte.tests.lnp.scenario04.entity.crm.Activity;
import org.os890.jawelte.tests.lnp.scenario04.entity.crm.ActivityType;
import org.os890.jawelte.tests.lnp.scenario04.entity.crm.Contact;
import org.os890.jawelte.tests.lnp.scenario04.entity.crm.ContactGroup;
import org.os890.jawelte.tests.lnp.scenario04.entity.crm.CrmCampaign;
import org.os890.jawelte.tests.lnp.scenario04.entity.crm.Deal;
import org.os890.jawelte.tests.lnp.scenario04.entity.crm.DealProduct;
import org.os890.jawelte.tests.lnp.scenario04.entity.crm.Interaction;
import org.os890.jawelte.tests.lnp.scenario04.entity.crm.Note;
import org.os890.jawelte.tests.lnp.scenario04.entity.crm.Opportunity;
import org.os890.jawelte.tests.lnp.scenario04.entity.crm.OpportunityStage;
import org.os890.jawelte.tests.lnp.scenario04.entity.crm.Pipeline;
import org.os890.jawelte.tests.lnp.scenario04.entity.ecommerce.Address;
import org.os890.jawelte.tests.lnp.scenario04.entity.ecommerce.Category;
import org.os890.jawelte.tests.lnp.scenario04.entity.ecommerce.Customer;
import org.os890.jawelte.tests.lnp.scenario04.entity.ecommerce.CustomerOrder;
import org.os890.jawelte.tests.lnp.scenario04.entity.ecommerce.OrderItem;
import org.os890.jawelte.tests.lnp.scenario04.entity.ecommerce.OrderStatus;
import org.os890.jawelte.tests.lnp.scenario04.entity.ecommerce.Payment;
import org.os890.jawelte.tests.lnp.scenario04.entity.ecommerce.PaymentMethod;
import org.os890.jawelte.tests.lnp.scenario04.entity.ecommerce.Product;
import org.os890.jawelte.tests.lnp.scenario04.entity.ecommerce.ProductStatus;
import org.os890.jawelte.tests.lnp.scenario04.entity.ecommerce.Review;
import org.os890.jawelte.tests.lnp.scenario04.entity.finance.Account;
import org.os890.jawelte.tests.lnp.scenario04.entity.finance.AccountType;
import org.os890.jawelte.tests.lnp.scenario04.entity.finance.Budget;
import org.os890.jawelte.tests.lnp.scenario04.entity.finance.BudgetLine;
import org.os890.jawelte.tests.lnp.scenario04.entity.finance.Currency;
import org.os890.jawelte.tests.lnp.scenario04.entity.finance.ExchangeRate;
import org.os890.jawelte.tests.lnp.scenario04.entity.finance.FinancialTransaction;
import org.os890.jawelte.tests.lnp.scenario04.entity.finance.Invoice;
import org.os890.jawelte.tests.lnp.scenario04.entity.finance.InvoiceLine;
import org.os890.jawelte.tests.lnp.scenario04.entity.finance.TransactionType;
import org.os890.jawelte.tests.lnp.scenario04.entity.hr.Department;
import org.os890.jawelte.tests.lnp.scenario04.entity.hr.Employee;
import org.os890.jawelte.tests.lnp.scenario04.entity.hr.LeaveRequest;
import org.os890.jawelte.tests.lnp.scenario04.entity.hr.LeaveType;
import org.os890.jawelte.tests.lnp.scenario04.entity.hr.OfficeLocation;
import org.os890.jawelte.tests.lnp.scenario04.entity.hr.Project;
import org.os890.jawelte.tests.lnp.scenario04.entity.hr.ProjectAssignment;
import org.os890.jawelte.tests.lnp.scenario04.entity.hr.Salary;
import org.os890.jawelte.tests.lnp.scenario04.entity.hr.Skill;
import org.os890.jawelte.tests.lnp.scenario04.entity.hr.SkillLevel;
import org.os890.jawelte.tests.lnp.scenario04.entity.inventory.Bin;
import org.os890.jawelte.tests.lnp.scenario04.entity.inventory.PurchaseOrder;
import org.os890.jawelte.tests.lnp.scenario04.entity.inventory.PurchaseOrderLine;
import org.os890.jawelte.tests.lnp.scenario04.entity.inventory.PurchaseOrderStatus;
import org.os890.jawelte.tests.lnp.scenario04.entity.inventory.StockItem;
import org.os890.jawelte.tests.lnp.scenario04.entity.inventory.StockTransfer;
import org.os890.jawelte.tests.lnp.scenario04.entity.inventory.StockTransferLine;
import org.os890.jawelte.tests.lnp.scenario04.entity.inventory.Supplier;
import org.os890.jawelte.tests.lnp.scenario04.entity.inventory.Warehouse;
import org.os890.jawelte.tests.lnp.scenario04.entity.logistics.Carrier;
import org.os890.jawelte.tests.lnp.scenario04.entity.logistics.DeliveryAttempt;
import org.os890.jawelte.tests.lnp.scenario04.entity.logistics.DeliveryZone;
import org.os890.jawelte.tests.lnp.scenario04.entity.logistics.FreightRate;
import org.os890.jawelte.tests.lnp.scenario04.entity.logistics.PackageDimension;
import org.os890.jawelte.tests.lnp.scenario04.entity.logistics.ReturnItem;
import org.os890.jawelte.tests.lnp.scenario04.entity.logistics.ReturnRequest;
import org.os890.jawelte.tests.lnp.scenario04.entity.logistics.Route;
import org.os890.jawelte.tests.lnp.scenario04.entity.logistics.Shipment;
import org.os890.jawelte.tests.lnp.scenario04.entity.logistics.ShipmentItem;
import org.os890.jawelte.tests.lnp.scenario04.entity.logistics.ShippingLabel;
import org.os890.jawelte.tests.lnp.scenario04.entity.logistics.TrackingEvent;
import org.os890.jawelte.tests.lnp.scenario04.entity.marketing.AbTest;
import org.os890.jawelte.tests.lnp.scenario04.entity.marketing.AbTestVariant;
import org.os890.jawelte.tests.lnp.scenario04.entity.marketing.AdPlacement;
import org.os890.jawelte.tests.lnp.scenario04.entity.marketing.Campaign;
import org.os890.jawelte.tests.lnp.scenario04.entity.marketing.CampaignChannel;
import org.os890.jawelte.tests.lnp.scenario04.entity.marketing.ClickTracking;
import org.os890.jawelte.tests.lnp.scenario04.entity.marketing.Coupon;
import org.os890.jawelte.tests.lnp.scenario04.entity.marketing.CouponUsage;
import org.os890.jawelte.tests.lnp.scenario04.entity.marketing.LandingPage;
import org.os890.jawelte.tests.lnp.scenario04.entity.marketing.LeadScore;
import org.os890.jawelte.tests.lnp.scenario04.entity.marketing.NewsletterSubscription;
import org.os890.jawelte.tests.lnp.scenario04.entity.marketing.Promotion;
import org.os890.jawelte.tests.lnp.scenario04.entity.support.ChatMessage;
import org.os890.jawelte.tests.lnp.scenario04.entity.support.ChatSession;
import org.os890.jawelte.tests.lnp.scenario04.entity.support.EscalationRule;
import org.os890.jawelte.tests.lnp.scenario04.entity.support.FaqEntry;
import org.os890.jawelte.tests.lnp.scenario04.entity.support.KnowledgeArticle;
import org.os890.jawelte.tests.lnp.scenario04.entity.support.SatisfactionSurvey;
import org.os890.jawelte.tests.lnp.scenario04.entity.support.SlaPolicy;
import org.os890.jawelte.tests.lnp.scenario04.entity.support.SlaViolation;
import org.os890.jawelte.tests.lnp.scenario04.entity.support.SupportAgent;
import org.os890.jawelte.tests.lnp.scenario04.entity.support.Ticket;
import org.os890.jawelte.tests.lnp.scenario04.entity.support.TicketCategory;
import org.os890.jawelte.tests.lnp.scenario04.entity.support.TicketComment;

/**
 * Populates all entity tables with at least 100 rows each.
 * Call {@link #populate(EntityManager)} inside an active transaction.
 */
public class TestDataPopulator {

    private TestDataPopulator() {}

    private static void flushBatch(EntityManager em, int i) {
        if (i > 0 && i % 50 == 0) {
            em.flush();
            em.clear();
        }
    }

    public static PopulatedData populate(EntityManager em) {
        PopulatedData d = new PopulatedData();

        

        populateECommerce(em, d);
        populateHr(em, d);
        populateContent(em, d);
        populateFinance(em, d);
        populateInventory(em, d);
        populateLogistics(em, d);
        populateMarketing(em, d);
        populateSupport(em, d);
        populateCrm(em, d);
        populateAnalytics(em, d);

        return d;
    }

    private static void populateECommerce(EntityManager em, PopulatedData d) {
        // 20 categories
        for (int i = 0; i < 20; i++) {
            Category cat = new Category();
            cat.setName("Category-" + i);
            em.persist(cat);
            d.categories.add(cat);
        }

        // 100 customers
        for (int i = 0; i < 100; i++) {
            Customer c = new Customer();
            c.setName("Customer-" + i);
            c.setEmail("customer" + i + "@test.com");
            Address addr = new Address();
            addr.setStreet("Street " + i);
            addr.setCity("City " + (i % 10));
            addr.setZipCode("1" + String.format("%04d", i));
            addr.setCountry("Country-" + (i % 5));
            c.setAddress(addr);
            em.persist(c);
            d.customers.add(c);
            flushBatch(em, i);
        }
        em.flush();

        // 100 products
        for (int i = 0; i < 100; i++) {
            Product p = new Product();
            p.setName("Product-" + i);
            p.setSku("SKU-" + i);
            p.setPrice(new BigDecimal("9.99").add(BigDecimal.valueOf(i)));
            p.setStatus(i % 5 == 0 ? ProductStatus.DISCONTINUED : ProductStatus.ACTIVE);
            p.setCategories(new HashSet<>(List.of(d.categories.get(i % d.categories.size()))));
            em.persist(p);
            d.products.add(p);
            flushBatch(em, i);
        }
        em.flush();

        // 100 orders with 2 items each = 200 order items
        for (int i = 0; i < 100; i++) {
            CustomerOrder o = new CustomerOrder();
            o.setCustomer(em.merge(d.customers.get(i % d.customers.size())));
            o.setOrderDate(LocalDate.now().minusDays(i));
            o.setStatus(OrderStatus.values()[i % OrderStatus.values().length]);
            o.setTotalAmount(BigDecimal.ZERO);
            em.persist(o);

            BigDecimal total = BigDecimal.ZERO;
            for (int j = 0; j < 2; j++) {
                OrderItem item = new OrderItem();
                item.setOrder(o);
                Product prod = em.merge(d.products.get((i * 2 + j) % d.products.size()));
                item.setProduct(prod);
                item.setQuantity(j + 1);
                item.setUnitPrice(prod.getPrice());
                em.persist(item);
                o.getItems().add(item);
                total = total.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            }
            o.setTotalAmount(total);
            d.orders.add(o);
            flushBatch(em, i);
        }
        em.flush();

        // 100 payments (for first 100 orders)
        for (int i = 0; i < 100; i++) {
            Payment pay = new Payment();
            pay.setOrder(em.merge(d.orders.get(i)));
            pay.setAmount(d.orders.get(i).getTotalAmount());
            pay.setPaymentDate(LocalDateTime.now().minusDays(i));
            pay.setMethod(PaymentMethod.values()[i % PaymentMethod.values().length]);
            em.persist(pay);
            flushBatch(em, i);
        }
        em.flush();

        // 100 reviews
        for (int i = 0; i < 100; i++) {
            Review r = new Review();
            r.setProduct(em.merge(d.products.get(i % d.products.size())));
            r.setCustomer(em.merge(d.customers.get(i % d.customers.size())));
            r.setRating(1 + (i % 5));
            r.setComment("Review comment " + i);
            r.setCreatedAt(LocalDateTime.now().minusHours(i));
            em.persist(r);
            flushBatch(em, i);
        }
        em.flush();
    }

    private static void populateHr(EntityManager em, PopulatedData d) {
        // 20 departments
        for (int i = 0; i < 20; i++) {
            Department dept = new Department();
            dept.setName("Department-" + i);
            em.persist(dept);
            d.departments.add(dept);
        }

        // 20 skills
        for (int i = 0; i < 20; i++) {
            Skill skill = new Skill();
            skill.setName("Skill-" + i);
            skill.setLevel(SkillLevel.values()[i % SkillLevel.values().length]);
            em.persist(skill);
            d.skills.add(skill);
        }
        em.flush();

        // 100 employees
        for (int i = 0; i < 100; i++) {
            Employee emp = new Employee();
            emp.setFirstName("First-" + i);
            emp.setLastName("Last-" + i);
            emp.setEmail("emp" + i + "@test.com");
            emp.setHireDate(LocalDate.now().minusYears(i % 15));
            emp.setDepartment(em.merge(d.departments.get(i % d.departments.size())));
            emp.setSkills(new HashSet<>(List.of(em.merge(d.skills.get(i % d.skills.size())))));
            em.persist(emp);
            d.employees.add(emp);
            flushBatch(em, i);
        }
        em.flush();

        // Set managers
        for (int i = 0; i < d.departments.size(); i++) {
            Department dept = em.merge(d.departments.get(i));
            dept.setManager(em.merge(d.employees.get(i)));
        }

        // 30 projects with 100 assignments total
        for (int i = 0; i < 30; i++) {
            Project proj = new Project();
            proj.setName("Project-" + i);
            proj.setStartDate(LocalDate.now().minusMonths(i));
            proj.setEndDate(i % 2 == 0 ? LocalDate.now().plusMonths(6) : null);
            em.persist(proj);
            d.projects.add(proj);

            for (int j = 0; j < 3; j++) {
                ProjectAssignment pa = new ProjectAssignment();
                pa.setProject(proj);
                pa.setEmployee(em.merge(d.employees.get((i * 3 + j) % d.employees.size())));
                pa.setRole("Role-" + j);
                pa.setAssignedDate(LocalDate.now().minusDays(j));
                em.persist(pa);
            }
            flushBatch(em, i);
        }
        em.flush();

        // 100 salary records
        for (int i = 0; i < 100; i++) {
            Salary sal = new Salary();
            sal.setEmployee(em.merge(d.employees.get(i % d.employees.size())));
            sal.setAmount(new BigDecimal("50000").add(BigDecimal.valueOf(i * 500)));
            sal.setEffectiveDate(LocalDate.now().minusMonths(i % 24));
            em.persist(sal);
            flushBatch(em, i);
        }
        em.flush();

        // 100 leave requests
        for (int i = 0; i < 100; i++) {
            LeaveRequest lr = new LeaveRequest();
            lr.setEmployee(em.merge(d.employees.get(i % d.employees.size())));
            lr.setStartDate(LocalDate.now().plusDays(i));
            lr.setEndDate(LocalDate.now().plusDays(i + 3));
            lr.setType(LeaveType.values()[i % LeaveType.values().length]);
            lr.setApproved(i % 2 == 0);
            em.persist(lr);
            flushBatch(em, i);
        }
        em.flush();

        // 20 office locations
        for (int i = 0; i < 20; i++) {
            OfficeLocation loc = new OfficeLocation();
            loc.setName("Office-" + i);
            Address oAddr = new Address();
            oAddr.setStreet("Office Street " + i);
            oAddr.setCity("Office City " + (i % 5));
            oAddr.setZipCode("2" + String.format("%04d", i));
            oAddr.setCountry("Country-" + (i % 3));
            loc.setAddress(oAddr);
            loc.setCapacity(50 + i * 10);
            em.persist(loc);
        }
        em.flush();
    }

    private static void populateContent(EntityManager em, PopulatedData d) {
        // 20 authors
        for (int i = 0; i < 20; i++) {
            Author author = new Author();
            author.setName("Author-" + i);
            author.setBio("Bio of author " + i);
            em.persist(author);
            d.authors.add(author);
        }

        // 20 tags
        for (int i = 0; i < 20; i++) {
            Tag tag = new Tag();
            tag.setName("Tag-" + i);
            em.persist(tag);
            d.tags.add(tag);
        }
        em.flush();

        // 100 articles + 100 comments + 100 content settings
        for (int i = 0; i < 100; i++) {
            Article art = new Article();
            art.setTitle("Article-" + i);
            art.setBody("Body of article " + i + " with text content for testing.");
            art.setPublishedAt(LocalDateTime.now().minusDays(i));
            art.setAuthor(em.merge(d.authors.get(i % d.authors.size())));
            art.setTags(new HashSet<>(List.of(em.merge(d.tags.get(i % d.tags.size())))));
            em.persist(art);
            d.articles.add(art);

            Comment comment = new Comment();
            comment.setArticle(art);
            comment.setAuthorName("Commenter-" + i);
            comment.setBody("Comment on article " + i);
            comment.setCreatedAt(LocalDateTime.now().minusHours(i));
            em.persist(comment);

            ContentSettings cs = new ContentSettings();
            cs.setArticle(art);
            cs.setAllowComments(true);
            cs.setFeatured(i % 5 == 0);
            em.persist(cs);
            flushBatch(em, i);
        }
        em.flush();

        // 100 content categories (some with parents for hierarchy)
        List<ContentCategory> contentCats = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            ContentCategory cc = new ContentCategory();
            cc.setName("ContentCat-" + i);
            if (i > 10) {
                cc.setParent(contentCats.get(i % 10));
            }
            em.persist(cc);
            contentCats.add(cc);
            flushBatch(em, i);
        }
        em.flush();

        // 100 media + 100 ratings + 100 content versions
        for (int i = 0; i < 100; i++) {
            Media media = new Media();
            media.setFileName("file-" + i + ".jpg");
            media.setMimeType(i % 3 == 0 ? "image/png" : "image/jpeg");
            media.setSize(1024L * (i + 1));
            media.setUploadedAt(LocalDateTime.now().minusDays(i));
            em.persist(media);

            Rating rating = new Rating();
            rating.setArticle(em.merge(d.articles.get(i % d.articles.size())));
            rating.setScore(1 + (i % 5));
            rating.setVoterName("Voter-" + i);
            em.persist(rating);

            ContentVersion cv = new ContentVersion();
            cv.setArticle(em.merge(d.articles.get(i % d.articles.size())));
            cv.setVersionNumber(i + 1);
            cv.setContent("Version " + i + " content text.");
            cv.setCreatedAt(LocalDateTime.now().minusHours(i));
            em.persist(cv);
            flushBatch(em, i);
        }
        em.flush();
    }

    private static void populateFinance(EntityManager em, PopulatedData d) {
        // 100 accounts with 200 transactions
        for (int i = 0; i < 100; i++) {
            Account acc = new Account();
            acc.setAccountNumber("ACC-" + i);
            acc.setName("Account-" + i);
            acc.setBalance(new BigDecimal("10000").add(BigDecimal.valueOf(i * 100)));
            acc.setType(AccountType.values()[i % AccountType.values().length]);
            em.persist(acc);
            d.accounts.add(acc);

            for (int j = 0; j < 2; j++) {
                FinancialTransaction ft = new FinancialTransaction();
                ft.setAccount(acc);
                ft.setAmount(new BigDecimal("100").add(BigDecimal.valueOf(j * 50)));
                ft.setTransactionDate(LocalDateTime.now().minusDays(j));
                ft.setDescription("Tx " + j + " for account " + i);
                ft.setType(TransactionType.values()[j % TransactionType.values().length]);
                em.persist(ft);
            }
            flushBatch(em, i);
        }
        em.flush();

        // 10 currencies + 100 exchange rates
        String[] codes = {"USD", "EUR", "GBP", "JPY", "CHF", "CAD", "AUD", "NZD", "SEK", "NOK"};
        String[] names = {"US Dollar", "Euro", "British Pound", "Japanese Yen", "Swiss Franc",
                "Canadian Dollar", "Australian Dollar", "New Zealand Dollar", "Swedish Krona", "Norwegian Krone"};
        for (int i = 0; i < 10; i++) {
            Currency cur = new Currency();
            cur.setCode(codes[i]);
            cur.setName(names[i]);
            em.persist(cur);
            d.currencies.add(cur);
        }
        em.flush();

        for (int i = 0; i < 100; i++) {
            ExchangeRate er = new ExchangeRate();
            er.setFromCurrency(em.merge(d.currencies.get(i % d.currencies.size())));
            er.setToCurrency(em.merge(d.currencies.get((i + 1) % d.currencies.size())));
            er.setRate(new BigDecimal("0.80").add(BigDecimal.valueOf(i).multiply(new BigDecimal("0.01"))));
            er.setEffectiveDate(LocalDate.now().minusDays(i));
            em.persist(er);
            flushBatch(em, i);
        }
        em.flush();

        // 10 budgets with 100 budget lines total
        for (int i = 0; i < 10; i++) {
            Budget budget = new Budget();
            budget.setName("Budget-" + (2020 + i));
            budget.setYear(2020 + i);
            em.persist(budget);

            for (int j = 0; j < 10; j++) {
                BudgetLine bl = new BudgetLine();
                bl.setBudget(budget);
                bl.setCategory("BudgetCat-" + j);
                bl.setPlannedAmount(new BigDecimal("5000").add(BigDecimal.valueOf(j * 1000)));
                bl.setActualAmount(new BigDecimal("4500").add(BigDecimal.valueOf(j * 900)));
                em.persist(bl);
            }
        }
        em.flush();

        // 20 invoices with 100 invoice lines
        for (int i = 0; i < 20; i++) {
            Invoice inv = new Invoice();
            inv.setInvoiceNumber("INV-" + String.format("%03d", i));
            inv.setAccount(em.merge(d.accounts.get(i % d.accounts.size())));
            inv.setIssueDate(LocalDate.now().minusDays(30 + i));
            inv.setDueDate(LocalDate.now().minusDays(i));
            em.persist(inv);
            for (int j = 0; j < 5; j++) {
                InvoiceLine il = new InvoiceLine();
                il.setInvoice(inv);
                il.setDescription("Line " + j + " of invoice " + i);
                il.setQuantity(j + 1);
                il.setUnitPrice(new BigDecimal("25.00").add(BigDecimal.valueOf(j * 10)));
                em.persist(il);
                inv.getLines().add(il);
            }
        }
        em.flush();
    }

    private static void populateInventory(EntityManager em, PopulatedData d) {
        // 10 warehouses with 100 stock items and 100 bins
        for (int i = 0; i < 10; i++) {
            Warehouse wh = new Warehouse();
            wh.setName("Warehouse-" + i);
            Address whAddr = new Address();
            whAddr.setStreet("Warehouse Street " + i);
            whAddr.setCity("WH City " + (i % 3));
            whAddr.setZipCode("3" + String.format("%04d", i));
            whAddr.setCountry("Country-0");
            wh.setAddress(whAddr);
            em.persist(wh);
            d.warehouses.add(wh);

            for (int j = 0; j < 10; j++) {
                StockItem si = new StockItem();
                si.setWarehouse(wh);
                si.setProductSku("SKU-" + (i * 10 + j));
                si.setQuantity(100 + j * 10);
                si.setLastUpdated(LocalDateTime.now());
                em.persist(si);
            }

            for (int j = 0; j < 10; j++) {
                Bin bin = new Bin();
                bin.setWarehouse(wh);
                bin.setLabel("Bin-" + i + "-" + j);
                bin.setBarcode("BC-" + i + "-" + j);
                em.persist(bin);
            }
        }
        em.flush();

        // 20 suppliers with 100 PO lines
        for (int i = 0; i < 20; i++) {
            Supplier sup = new Supplier();
            sup.setName("Supplier-" + i);
            sup.setContactEmail("supplier" + i + "@test.com");
            Address sAddr = new Address();
            sAddr.setStreet("Supplier Street " + i);
            sAddr.setCity("Supplier City " + (i % 5));
            sAddr.setZipCode("4" + String.format("%04d", i));
            sAddr.setCountry("Country-1");
            sup.setAddress(sAddr);
            em.persist(sup);

            PurchaseOrder po = new PurchaseOrder();
            po.setSupplier(sup);
            po.setOrderDate(LocalDate.now().minusDays(i * 3));
            po.setStatus(PurchaseOrderStatus.values()[i % PurchaseOrderStatus.values().length]);
            em.persist(po);

            for (int j = 0; j < 5; j++) {
                PurchaseOrderLine pol = new PurchaseOrderLine();
                pol.setPurchaseOrder(po);
                pol.setProductSku("SKU-" + (i * 5 + j) % 100);
                pol.setQuantity(10 + j * 5);
                pol.setUnitCost(new BigDecimal("5.00").add(BigDecimal.valueOf(j)));
                em.persist(pol);
            }
        }
        em.flush();

        // 20 stock transfers with 100 transfer lines
        for (int i = 0; i < 20; i++) {
            StockTransfer st = new StockTransfer();
            st.setFromWarehouse(em.merge(d.warehouses.get(i % d.warehouses.size())));
            st.setToWarehouse(em.merge(d.warehouses.get((i + 1) % d.warehouses.size())));
            st.setTransferDate(LocalDate.now().minusDays(i));
            em.persist(st);

            for (int j = 0; j < 5; j++) {
                StockTransferLine stl = new StockTransferLine();
                stl.setTransfer(st);
                stl.setProductSku("SKU-" + (i * 5 + j) % 100);
                stl.setQuantity(5 + j * 3);
                em.persist(stl);
            }
        }
        em.flush();
    }

    private static void populateLogistics(EntityManager em, PopulatedData d) {
        for (int i = 0; i < 10; i++) {
            Shipment sh = new Shipment();
            sh.setTrackingNumber("TRK-" + i);
            sh.setOrigin("Origin-" + (i % 5));
            sh.setDestination("Dest-" + (i % 5));
            sh.setShipDate(LocalDate.now().minusDays(i));
            sh.setShipmentStatus(i % 2 == 0 ? "IN_TRANSIT" : "DELIVERED");
            em.persist(sh);
        }
        for (int i = 0; i < 10; i++) {
            ShipmentItem si = new ShipmentItem();
            si.setProductSku("SKU-SHIP-" + i);
            si.setQuantity(i + 1);
            si.setWeight(new BigDecimal("2.5").add(BigDecimal.valueOf(i)));
            em.persist(si);
        }
        for (int i = 0; i < 5; i++) {
            Carrier c = new Carrier();
            c.setName("Carrier-" + i);
            c.setContactPhone("555-010" + i);
            c.setWebsite("https://carrier" + i + ".example.com");
            c.setActive(true);
            em.persist(c);
        }
        for (int i = 0; i < 5; i++) {
            Route r = new Route();
            r.setOriginHub("Hub-" + i);
            r.setDestinationHub("Hub-" + (i + 1));
            r.setEstimatedDays(i + 1);
            r.setDistanceKm(new BigDecimal("100").add(BigDecimal.valueOf(i * 50)));
            em.persist(r);
        }
        for (int i = 0; i < 5; i++) {
            DeliveryZone dz = new DeliveryZone();
            dz.setZoneName("Zone-" + i);
            dz.setZoneCode("DZ-" + i);
            dz.setRegion("Region-" + (i % 3));
            dz.setBaseFee(new BigDecimal("5.00").add(BigDecimal.valueOf(i)));
            em.persist(dz);
        }
        for (int i = 0; i < 10; i++) {
            TrackingEvent te = new TrackingEvent();
            te.setTrackingNumber("TRK-" + (i % 5));
            te.setEventType(i % 2 == 0 ? "PICKUP" : "DELIVERY");
            te.setLocation("Location-" + i);
            te.setEventTime(LocalDateTime.now().minusHours(i));
            em.persist(te);
        }
        for (int i = 0; i < 5; i++) {
            PackageDimension pd = new PackageDimension();
            pd.setLengthCm(new BigDecimal("30").add(BigDecimal.valueOf(i)));
            pd.setWidthCm(new BigDecimal("20").add(BigDecimal.valueOf(i)));
            pd.setHeightCm(new BigDecimal("10").add(BigDecimal.valueOf(i)));
            pd.setWeightKg(new BigDecimal("1.5").add(BigDecimal.valueOf(i)));
            em.persist(pd);
        }
        for (int i = 0; i < 5; i++) {
            FreightRate fr = new FreightRate();
            fr.setZoneCode("DZ-" + i);
            fr.setRatePerKg(new BigDecimal("1.50").add(new BigDecimal("0.25").multiply(BigDecimal.valueOf(i))));
            fr.setFlatFee(new BigDecimal("10.00"));
            fr.setServiceLevel(i % 2 == 0 ? "STANDARD" : "EXPRESS");
            em.persist(fr);
        }
        for (int i = 0; i < 5; i++) {
            DeliveryAttempt da = new DeliveryAttempt();
            da.setTrackingNumber("TRK-" + i);
            da.setAttemptTime(LocalDateTime.now().minusDays(i));
            da.setSuccessful(i % 2 == 0);
            da.setNotes("Attempt note " + i);
            em.persist(da);
        }
        for (int i = 0; i < 5; i++) {
            ReturnRequest rr = new ReturnRequest();
            rr.setOrderReference("ORD-RET-" + i);
            rr.setReason("Reason " + i);
            rr.setRequestDate(LocalDate.now().minusDays(i));
            rr.setReturnStatus(i % 2 == 0 ? "PENDING" : "APPROVED");
            em.persist(rr);
        }
        for (int i = 0; i < 5; i++) {
            ReturnItem ri = new ReturnItem();
            ri.setProductSku("SKU-RET-" + i);
            ri.setQuantity(i + 1);
            ri.setItemCondition(i % 2 == 0 ? "NEW" : "USED");
            em.persist(ri);
        }
        for (int i = 0; i < 5; i++) {
            ShippingLabel sl = new ShippingLabel();
            sl.setLabelCode("LBL-" + i);
            sl.setCarrierName("Carrier-" + (i % 3));
            sl.setPrintedDate(LocalDate.now().minusDays(i));
            sl.setRecipientName("Recipient-" + i);
            em.persist(sl);
        }
        em.flush();
    }

    private static void populateMarketing(EntityManager em, PopulatedData d) {
        for (int i = 0; i < 5; i++) {
            Campaign camp = new Campaign();
            camp.setName("Campaign-" + i);
            camp.setStartDate(LocalDate.now().minusMonths(i));
            camp.setEndDate(LocalDate.now().plusMonths(1));
            camp.setBudgetAmount(new BigDecimal("10000").add(BigDecimal.valueOf(i * 5000)));
            camp.setCampaignStatus(i % 2 == 0 ? "ACTIVE" : "PAUSED");
            em.persist(camp);
        }
        for (int i = 0; i < 5; i++) {
            CampaignChannel cc = new CampaignChannel();
            cc.setChannelName("Channel-" + i);
            cc.setChannelType(i % 2 == 0 ? "EMAIL" : "SOCIAL");
            cc.setAllocatedBudget(new BigDecimal("2000").add(BigDecimal.valueOf(i * 500)));
            em.persist(cc);
        }
        for (int i = 0; i < 5; i++) {
            Promotion promo = new Promotion();
            promo.setPromoCode("PROMO-" + i);
            promo.setDescription("Promotion " + i);
            promo.setDiscountPercent(new BigDecimal("5").add(BigDecimal.valueOf(i * 5)));
            promo.setValidFrom(LocalDate.now().minusDays(30));
            promo.setValidUntil(LocalDate.now().plusDays(30));
            em.persist(promo);
        }
        for (int i = 0; i < 5; i++) {
            Coupon cp = new Coupon();
            cp.setCouponCode("CPN-" + i);
            cp.setDiscountAmount(new BigDecimal("10.00").add(BigDecimal.valueOf(i * 5)));
            cp.setMaxUses(100 + i * 50);
            cp.setExpiryDate(LocalDate.now().plusDays(60));
            em.persist(cp);
        }
        for (int i = 0; i < 5; i++) {
            CouponUsage cu = new CouponUsage();
            cu.setCouponCode("CPN-" + (i % 3));
            cu.setCustomerEmail("customer" + i + "@test.com");
            cu.setUsedAt(LocalDateTime.now().minusDays(i));
            em.persist(cu);
        }
        for (int i = 0; i < 5; i++) {
            NewsletterSubscription ns = new NewsletterSubscription();
            ns.setEmail("subscriber" + i + "@test.com");
            ns.setSubscriberName("Subscriber-" + i);
            ns.setSubscribedDate(LocalDate.now().minusDays(i * 10));
            ns.setActive(true);
            em.persist(ns);
        }
        for (int i = 0; i < 5; i++) {
            AdPlacement ap = new AdPlacement();
            ap.setPlacementName("Placement-" + i);
            ap.setPageLocation(i % 2 == 0 ? "HEADER" : "SIDEBAR");
            ap.setCostPerClick(new BigDecimal("0.50").add(new BigDecimal("0.10").multiply(BigDecimal.valueOf(i))));
            ap.setImpressionCount(1000 + i * 500);
            em.persist(ap);
        }
        for (int i = 0; i < 5; i++) {
            ClickTracking ct = new ClickTracking();
            ct.setAdReference("AD-" + i);
            ct.setSourceUrl("https://example.com/page" + i);
            ct.setClickTime(LocalDateTime.now().minusHours(i));
            ct.setIpAddress("192.168.1." + i);
            em.persist(ct);
        }
        for (int i = 0; i < 5; i++) {
            AbTest ab = new AbTest();
            ab.setTestName("Test-" + i);
            ab.setDescription("AB test " + i);
            ab.setStartDate(LocalDate.now().minusDays(i * 7));
            ab.setActive(i % 2 == 0);
            em.persist(ab);
        }
        for (int i = 0; i < 5; i++) {
            AbTestVariant atv = new AbTestVariant();
            atv.setVariantName("Variant-" + i);
            atv.setVariantDescription("Variant description " + i);
            atv.setTrafficPercent(20 + i * 10);
            atv.setConversionCount(50 + i * 25);
            em.persist(atv);
        }
        for (int i = 0; i < 5; i++) {
            LandingPage lp = new LandingPage();
            lp.setPageName("LandingPage-" + i);
            lp.setSlug("landing-page-" + i);
            lp.setHeadline("Welcome to page " + i);
            lp.setPublished(i % 2 == 0);
            em.persist(lp);
        }
        for (int i = 0; i < 5; i++) {
            LeadScore ls = new LeadScore();
            ls.setContactEmail("lead" + i + "@test.com");
            ls.setScore(10 + i * 15);
            ls.setScoreCategory(i % 2 == 0 ? "HOT" : "WARM");
            ls.setLastUpdated(LocalDate.now().minusDays(i));
            em.persist(ls);
        }
        em.flush();
    }

    private static void populateSupport(EntityManager em, PopulatedData d) {
        for (int i = 0; i < 10; i++) {
            Ticket t = new Ticket();
            t.setSubject("Ticket-" + i);
            t.setDescription("Ticket description " + i);
            t.setPriority(i % 3 == 0 ? "HIGH" : i % 3 == 1 ? "MEDIUM" : "LOW");
            t.setTicketStatus(i % 2 == 0 ? "OPEN" : "CLOSED");
            t.setCreatedAt(LocalDateTime.now().minusDays(i));
            em.persist(t);
        }
        for (int i = 0; i < 5; i++) {
            TicketComment tc = new TicketComment();
            tc.setAuthorName("Agent-" + i);
            tc.setBody("Comment body " + i);
            tc.setPostedAt(LocalDateTime.now().minusHours(i));
            em.persist(tc);
        }
        for (int i = 0; i < 5; i++) {
            TicketCategory tcat = new TicketCategory();
            tcat.setCategoryName("TicketCat-" + i);
            tcat.setDescription("Category description " + i);
            em.persist(tcat);
        }
        for (int i = 0; i < 5; i++) {
            KnowledgeArticle ka = new KnowledgeArticle();
            ka.setTitle("KB-Article-" + i);
            ka.setContent("Knowledge content " + i);
            ka.setArticleStatus(i % 2 == 0 ? "PUBLISHED" : "DRAFT");
            ka.setPublishedAt(LocalDateTime.now().minusDays(i));
            em.persist(ka);
        }
        for (int i = 0; i < 5; i++) {
            SlaPolicy sp = new SlaPolicy();
            sp.setPolicyName("SLA-" + i);
            sp.setResponseTimeHours(1 + i);
            sp.setResolutionTimeHours(4 + i * 2);
            sp.setPriority(i % 2 == 0 ? "HIGH" : "LOW");
            em.persist(sp);
        }
        for (int i = 0; i < 5; i++) {
            SlaViolation sv = new SlaViolation();
            sv.setTicketReference("TKT-V-" + i);
            sv.setViolationType(i % 2 == 0 ? "RESPONSE" : "RESOLUTION");
            sv.setViolatedAt(LocalDateTime.now().minusHours(i));
            sv.setOverdueMinutes(30 + i * 15);
            em.persist(sv);
        }
        for (int i = 0; i < 5; i++) {
            ChatSession cs2 = new ChatSession();
            cs2.setSessionToken("SESS-" + i);
            cs2.setCustomerEmail("chat" + i + "@test.com");
            cs2.setStartedAt(LocalDateTime.now().minusHours(i * 2));
            cs2.setEndedAt(LocalDateTime.now().minusHours(i));
            em.persist(cs2);
        }
        for (int i = 0; i < 5; i++) {
            ChatMessage cm = new ChatMessage();
            cm.setSenderName("Sender-" + i);
            cm.setMessageText("Message text " + i);
            cm.setSentAt(LocalDateTime.now().minusMinutes(i * 10));
            cm.setFromAgent(i % 2 == 0);
            em.persist(cm);
        }
        for (int i = 0; i < 5; i++) {
            FaqEntry fe = new FaqEntry();
            fe.setQuestion("FAQ question " + i + "?");
            fe.setAnswer("FAQ answer " + i);
            fe.setCategory("General");
            fe.setViewCount(100 + i * 50);
            em.persist(fe);
        }
        for (int i = 0; i < 5; i++) {
            SupportAgent sa = new SupportAgent();
            sa.setAgentName("Agent-" + i);
            sa.setEmail("agent" + i + "@test.com");
            sa.setDepartment("Support-" + (i % 3));
            sa.setAvailable(i % 2 == 0);
            em.persist(sa);
        }
        for (int i = 0; i < 5; i++) {
            EscalationRule er2 = new EscalationRule();
            er2.setRuleName("Rule-" + i);
            er2.setTriggerCondition("condition-" + i);
            er2.setEscalateAfterMinutes(30 + i * 15);
            er2.setTargetTeam("Team-" + (i % 3));
            em.persist(er2);
        }
        for (int i = 0; i < 5; i++) {
            SatisfactionSurvey ss = new SatisfactionSurvey();
            ss.setTicketReference("TKT-S-" + i);
            ss.setRating(1 + (i % 5));
            ss.setFeedback("Feedback " + i);
            ss.setSubmittedAt(LocalDateTime.now().minusDays(i));
            em.persist(ss);
        }
        em.flush();
    }

    private static void populateCrm(EntityManager em, PopulatedData d) {
        for (int i = 0; i < 10; i++) {
            Contact cont = new Contact();
            cont.setFullName("Contact-" + i);
            cont.setEmail("contact" + i + "@test.com");
            cont.setPhone("555-020" + i);
            cont.setCompanyName("Company-" + (i % 5));
            em.persist(cont);
        }
        for (int i = 0; i < 5; i++) {
            ContactGroup cg = new ContactGroup();
            cg.setGroupName("Group-" + i);
            cg.setDescription("Contact group " + i);
            cg.setMemberCount(10 + i * 5);
            em.persist(cg);
        }
        for (int i = 0; i < 5; i++) {
            Opportunity opp = new Opportunity();
            opp.setOpportunityName("Opportunity-" + i);
            opp.setEstimatedValue(new BigDecimal("50000").add(BigDecimal.valueOf(i * 10000)));
            opp.setStageName("Stage-" + (i % 3));
            opp.setExpectedCloseDate(LocalDate.now().plusMonths(i + 1));
            em.persist(opp);
        }
        for (int i = 0; i < 5; i++) {
            OpportunityStage os = new OpportunityStage();
            os.setStageName("OppStage-" + i);
            os.setDisplayOrder(i + 1);
            os.setProbability(new BigDecimal("0.20").add(new BigDecimal("0.15").multiply(BigDecimal.valueOf(i))));
            em.persist(os);
        }
        for (int i = 0; i < 5; i++) {
            Activity act = new Activity();
            act.setActivityType(i % 2 == 0 ? "CALL" : "EMAIL");
            act.setSubject("Activity-" + i);
            act.setActivityDate(LocalDateTime.now().minusDays(i));
            act.setNotes("Activity notes " + i);
            em.persist(act);
        }
        for (int i = 0; i < 5; i++) {
            ActivityType at = new ActivityType();
            at.setTypeName("Type-" + i);
            at.setDescription("Activity type " + i);
            at.setActive(true);
            em.persist(at);
        }
        for (int i = 0; i < 5; i++) {
            Note note = new Note();
            note.setTitle("Note-" + i);
            note.setContent("Note content " + i);
            note.setCreatedAt(LocalDateTime.now().minusDays(i));
            em.persist(note);
        }
        for (int i = 0; i < 5; i++) {
            Pipeline pl = new Pipeline();
            pl.setPipelineName("Pipeline-" + i);
            pl.setDescription("Pipeline description " + i);
            pl.setActive(i % 2 == 0);
            em.persist(pl);
        }
        for (int i = 0; i < 5; i++) {
            Deal deal = new Deal();
            deal.setDealName("Deal-" + i);
            deal.setDealValue(new BigDecimal("25000").add(BigDecimal.valueOf(i * 5000)));
            deal.setDealStage("Stage-" + (i % 3));
            deal.setExpectedCloseDate(LocalDate.now().plusMonths(i + 1));
            em.persist(deal);
        }
        for (int i = 0; i < 5; i++) {
            DealProduct dp = new DealProduct();
            dp.setProductName("DealProd-" + i);
            dp.setQuantity(i + 1);
            dp.setUnitPrice(new BigDecimal("100").add(BigDecimal.valueOf(i * 50)));
            dp.setDiscount(new BigDecimal("5").add(BigDecimal.valueOf(i)));
            em.persist(dp);
        }
        for (int i = 0; i < 5; i++) {
            CrmCampaign crmc = new CrmCampaign();
            crmc.setCampaignName("CrmCampaign-" + i);
            crmc.setCampaignType(i % 2 == 0 ? "OUTBOUND" : "INBOUND");
            crmc.setStartDate(LocalDate.now().minusMonths(i));
            crmc.setBudgetAmount(new BigDecimal("5000").add(BigDecimal.valueOf(i * 2000)));
            em.persist(crmc);
        }
        for (int i = 0; i < 5; i++) {
            Interaction inter = new Interaction();
            inter.setInteractionType(i % 2 == 0 ? "PHONE" : "EMAIL");
            inter.setSummary("Interaction summary " + i);
            inter.setOccurredAt(LocalDateTime.now().minusDays(i));
            inter.setContactEmail("contact" + i + "@test.com");
            em.persist(inter);
        }
        em.flush();
    }

    private static void populateAnalytics(EntityManager em, PopulatedData d) {
        for (int i = 0; i < 10; i++) {
            PageView pv = new PageView();
            pv.setPageUrl("/page/" + i);
            pv.setVisitorId("visitor-" + (i % 5));
            pv.setViewedAt(LocalDateTime.now().minusHours(i));
            pv.setDurationSeconds(30 + i * 10);
            em.persist(pv);
        }
        for (int i = 0; i < 10; i++) {
            EventLog el = new EventLog();
            el.setEventName("Event-" + i);
            el.setEventCategory(i % 2 == 0 ? "CLICK" : "SCROLL");
            el.setOccurredAt(LocalDateTime.now().minusHours(i));
            el.setPayload("{\"key\":\"value" + i + "\"}");
            em.persist(el);
        }
        for (int i = 0; i < 5; i++) {
            UserSession us = new UserSession();
            us.setSessionId("session-" + i);
            us.setUserId("user-" + i);
            us.setStartedAt(LocalDateTime.now().minusHours(i * 2));
            us.setEndedAt(LocalDateTime.now().minusHours(i));
            em.persist(us);
        }
        for (int i = 0; i < 5; i++) {
            Funnel fn = new Funnel();
            fn.setFunnelName("Funnel-" + i);
            fn.setDescription("Funnel description " + i);
            fn.setActive(i % 2 == 0);
            em.persist(fn);
        }
        for (int i = 0; i < 5; i++) {
            FunnelStep fs = new FunnelStep();
            fs.setStepName("Step-" + i);
            fs.setStepOrder(i + 1);
            fs.setEnteredCount(100 + i * 20);
            fs.setCompletedCount(80 + i * 15);
            em.persist(fs);
        }
        for (int i = 0; i < 5; i++) {
            Metric m = new Metric();
            m.setMetricName("Metric-" + i);
            m.setMetricValue(new BigDecimal("42.5").add(BigDecimal.valueOf(i)));
            m.setRecordedDate(LocalDate.now().minusDays(i));
            m.setUnit(i % 2 == 0 ? "COUNT" : "PERCENT");
            em.persist(m);
        }
        for (int i = 0; i < 5; i++) {
            Dashboard dash = new Dashboard();
            dash.setDashboardName("Dashboard-" + i);
            dash.setOwnerName("Owner-" + i);
            dash.setPublished(i % 2 == 0);
            em.persist(dash);
        }
        for (int i = 0; i < 5; i++) {
            Widget w = new Widget();
            w.setWidgetName("Widget-" + i);
            w.setWidgetType(i % 2 == 0 ? "CHART" : "TABLE");
            w.setConfiguration("{\"type\":\"bar\"}");
            w.setDisplayOrder(i + 1);
            em.persist(w);
        }
        for (int i = 0; i < 5; i++) {
            Report rep = new Report();
            rep.setReportName("Report-" + i);
            rep.setReportType(i % 2 == 0 ? "SUMMARY" : "DETAIL");
            rep.setGeneratedAt(LocalDateTime.now().minusDays(i));
            rep.setOutputFormat(i % 2 == 0 ? "PDF" : "CSV");
            em.persist(rep);
        }
        for (int i = 0; i < 5; i++) {
            ReportSchedule rs = new ReportSchedule();
            rs.setReportName("Report-" + i);
            rs.setCronExpression("0 0 " + i + " * * ?");
            rs.setRecipientEmail("report" + i + "@test.com");
            rs.setEnabled(i % 2 == 0);
            em.persist(rs);
        }
        for (int i = 0; i < 5; i++) {
            DataExport de = new DataExport();
            de.setExportName("Export-" + i);
            de.setExportFormat(i % 2 == 0 ? "CSV" : "JSON");
            de.setRequestedAt(LocalDateTime.now().minusDays(i));
            de.setExportStatus(i % 2 == 0 ? "COMPLETED" : "PENDING");
            em.persist(de);
        }
        em.flush();
    }


    /** Holds references to persisted entities for assertions. */
    public static class PopulatedData {
        public final List<Customer> customers = new ArrayList<>();
        public final List<Product> products = new ArrayList<>();
        public final List<CustomerOrder> orders = new ArrayList<>();
        public final List<Category> categories = new ArrayList<>();
        public final List<Department> departments = new ArrayList<>();
        public final List<Employee> employees = new ArrayList<>();
        public final List<Skill> skills = new ArrayList<>();
        public final List<Project> projects = new ArrayList<>();
        public final List<Author> authors = new ArrayList<>();
        public final List<Tag> tags = new ArrayList<>();
        public final List<Article> articles = new ArrayList<>();
        public final List<Account> accounts = new ArrayList<>();
        public final List<Currency> currencies = new ArrayList<>();
        public final List<Warehouse> warehouses = new ArrayList<>();
    }
}
