# Persistence: JPA, JTA, data sources, EJB, Spring Data, batch

The guiding rule: use the platform's annotations. `@Transactional`, `@Resource`,
`@DataSourceDefinition` and `persistence.xml` behave in a test as they do in production.

## JPA

`persistence.xml` needs no JDBC properties — jpa-module generates an in-memory H2 URL by default.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<persistence xmlns="https://jakarta.ee/xml/ns/persistence"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="https://jakarta.ee/xml/ns/persistence
                                 https://jakarta.ee/xml/ns/persistence/persistence_3_2.xsd"
             version="3.2">
    <persistence-unit name="testPU" transaction-type="RESOURCE_LOCAL">
        <provider>org.hibernate.jpa.HibernatePersistenceProvider</provider>
        <exclude-unlisted-classes>false</exclude-unlisted-classes>
    </persistence-unit>
</persistence>
```

Entities are auto-discovered; `<exclude-unlisted-classes>false</exclude-unlisted-classes>` is
what enables that. Framework-protected packages are excluded from the scan.

```java
@EnableTestBeans
class CustomerServiceTest {

    @Inject
    CustomerService customerService;   // @ApplicationScoped, @Inject EntityManager, @Transactional

    @Test
    void transactionalMethodCommits() {
        assertThat(customerService.createCustomer("Alice")).isNotNull();
        assertThat(customerService.countCustomers()).isEqualTo(1L);
    }
}
```

`EntityManager` and `EntityManagerFactory` are injectable. `@PersistenceContext` and
`@PersistenceUnit` are rewritten onto CDI injection, named variants included. `UserTransaction`
is injectable too.

### Transaction semantics

`@Transactional` works on an application bean **and** directly on a test method. Rollback on a
`RuntimeException` or an `Error`, commit on a checked exception — the platform's rules. Nested
transactions are supported in every commit/rollback combination. An orphaned transaction is
rolled back by a safety net rather than leaking.

`@ReadOnly` (an interceptor binding, on a type or a method) discards writes at the end — useful
for asserting that a read path does not mutate. It works with or without `@Transactional`, and
an inner `@ReadOnly` inside a writable outer transaction rolls back only its own writes.

CDI events fire around the transaction, so a test can observe the outcome instead of inferring
it. All extend `PersistenceUnitTransactionEvent`, which carries the persistence unit name:
`TransactionStarted`, `TransactionBeforeCompletion`, `TransactionCommitted` and
`TransactionRolledBack` in `org.os890.jawelte.module.jpa.api.event`. The core adds
`AfterTestTransaction` for the per-method boundary.

```java
void onCommit(@Observes TransactionCommitted committed) {
    assertThat(committed.getPersistenceUnitName()).isEqualTo("testPU");
}
```

### `@PersistenceConfig`

On the test class, `@Inherited`.

| Attribute | Effect |
| --- | --- |
| `persistenceUnitName` | pick one unit by name |
| `persistenceUnits` | restrict which units are bootstrapped |
| `fileMode` | `true` puts H2 on disk instead of in memory |
| `filePath` | where, when `fileMode` is on |

### Multiple persistence units

With more than one unit, injection must be qualified by name — an unqualified `EntityManager`
fails. Cross-unit writes, per-unit routing and XA behaviour are all supported.

### Per-method cleanup

Rows written by a test method are cleaned up afterwards. `db-migration-module` keeps Flyway /
Liquibase bookkeeping tables out of that cleanup, so migration history survives while test rows
do not.

## JTA

Add `jta-module` and a transaction manager; the strategy is auto-selected. Geronimo, Narayana
and Atomikos are each verified. A resource-local unit is switched to JTA automatically when the
module is present, `UserTransaction` becomes the JTA-provided one, and multi-unit XA flushes,
rollbacks and read-only semantics behave as in a container.

Vendor internals (`com.arjuna.*`, `org.apache.geronimo.transaction.*`) are excluded from
auto-mocking, so their presence does not confuse bean resolution.

## Data sources and naming

`jndi-module` provides the one in-process naming tree every binding module shares. Add its
`-impl` explicitly — other modules depend only on its `-api`.

`datasource-module` turns `@DataSourceDefinition` — on the test class or on a bean class — into a
real, bound, injectable `DataSource`, isolated per test class and closed afterwards. Multiple
definitions are distinguished by name. A declared URL can be redirected through MicroProfile
Config, which is what lets a production definition point at an in-memory database for the run.

The payoff: a `<jta-data-source>` name in `persistence.xml` resolves to the data source the
annotation declares, so migration through a plain `DataSource` and reads through JPA hit **one**
database, and per-method cleanup reaches both.

With no naming provider on the classpath the modules degrade with an explanatory message rather
than a `NullPointerException`.

`resource-module` honours `@Resource(lookup = ...)` on application beans, including feeding a
producer. A bare `@Resource` with no lookup is left alone.

## EJB

`ejb-module` maps `@Singleton` and `@Stateless` onto CDI scopes and gives them implicit
transactionality. `@Stateful` and `@MessageDriven` are ignored, as are `@TransactionAttribute`,
`@Lock`, `@AccessTimeout` and `@Startup`. A user-declared scope or a user-declared
`@Transactional` on a session bean is preserved.

## Spring Data

`spring-data-module` discovers repository interfaces and registers real proxies — derived query
methods, `@Query` (JPQL and native), paging and sorting all work, and the injected repository is
a real Spring Data proxy rather than a mock. If you produce a repository yourself, the module
backs off. With no repository interface present it does nothing.

## Jakarta Batch

`batch-module` runs a job and gives you a typed result.

```java
BatchExecution execution = new BatchExecution("importJob")
        .param("file", "customers.csv")
        .timeout(Duration.ofSeconds(30));
```

Default timeout 60s, polled with exponential backoff starting at 50ms and capped at 5s. A
timeout does not cancel the job, it fails the assertion. `getExecutionId()` before the job has
been fired throws. A custom `TimeoutHandler` can be supplied as an `@Alternative`.
