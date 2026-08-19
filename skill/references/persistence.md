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

Add `jta-module` plus exactly one transaction manager — `org.apache.geronimo.components:geronimo-transaction`,
Narayana or Atomikos — and set the persistence unit to JTA:

```xml
<persistence-unit name="testJtaPU" transaction-type="JTA">
    <provider>org.hibernate.jpa.HibernatePersistenceProvider</provider>
    <exclude-unlisted-classes>false</exclude-unlisted-classes>
</persistence-unit>
```

Nothing else changes in the test — the strategy is auto-selected from whichever manager is on the
classpath, and the same `@Transactional` service as above now commits through JTA:

```java
@EnableTestBeans
class CustomerServiceJtaTest {

    @Inject
    CustomerService customerService;                 // unchanged from the resource-local test

    @Test
    void transactionalCommitsTheJtaPersist() {
        assertThat(customerService.createCustomer("Alice")).isNotNull();
        assertThat(customerService.countCustomers()).isEqualTo(1L);
    }
}
```

A resource-local unit is switched to JTA automatically when the module is present,
`UserTransaction` becomes the JTA-provided one, and multi-unit XA flushes, rollbacks and
read-only semantics behave as in a container.

Vendor internals (`com.arjuna.*`, `org.apache.geronimo.transaction.*`) are excluded from
auto-mocking, so their presence does not confuse bean resolution.

## Data sources

`@DataSourceDefinition` — the platform annotation — is what declares a data source. Put it on the
test class or on any bean class; the module builds it, binds it in JNDI under its declared name,
and makes it injectable. It is isolated per test class and closed afterwards.

```java
@EnableTestBeans
@DataSourceDefinition(
        name = "java:comp/env/jdbc/OrdersDS",
        className = "org.h2.jdbcx.JdbcDataSource",
        url = "jdbc:h2:mem:orders;DB_CLOSE_DELAY=-1",
        user = "sa",
        password = "")
class OrderRepositoryTest {

    @Inject
    DataSource unqualified;                          // works while there is exactly one definition

    @Inject
    @Named("java:comp/env/jdbc/OrdersDS")
    DataSource byName;                               // always works; required with several

    @Test
    void theDefinitionIsBoundAndInjectable() throws NamingException {
        assertThat(byName).isSameAs(unqualified);
        assertThat(new InitialContext().lookup("java:comp/env/jdbc/OrdersDS")).isSameAs(byName);
    }
}
```

Declaring it on an `@ApplicationScoped` bean instead of the test class is the shape to use when
production code owns the declaration:

```java
@ApplicationScoped
@DataSourceDefinition(name = "java:app/jdbc/AppDS", className = "org.h2.jdbcx.JdbcDataSource",
        url = "jdbc:h2:mem:app;DB_CLOSE_DELAY=-1", user = "sa", password = "")
public class AppDataSourceDeclaration {
}
```

The `url` of a declared definition can be redirected through MicroProfile Config with
`org.os890.jawelte.module.datasource.<definitionName>.url`, which is what lets a production
definition point at an in-memory database for the run without editing it.

The payoff: a `<jta-data-source>` name in `persistence.xml` resolves to the data source the
annotation declares, so migration through a plain `DataSource` and reads through JPA hit **one**
database, and per-method cleanup reaches both.

## `@Resource(lookup = ...)`

`resource-module` resolves `@Resource(lookup = ...)` on application beans against the same naming
tree, so a bean written for a container works unchanged:

```java
@ApplicationScoped
public class OrderRepository {

    @Resource(lookup = "java:app/jdbc/AppDS")
    private DataSource declared;                     // resolved by resource-module

    @Inject
    @Named("java:app/jdbc/AppDS")
    private DataSource injected;                     // the very same object
}
```

Both idioms reach one instance. A bare `@Resource` with no `lookup` is left alone, and an unbound
name produces an error naming the field rather than a null.

## Naming

`jndi-module` owns the single in-process naming tree the binding modules share. Add its `-impl`
explicitly — other modules depend only on its `-api` — plus `org.apache.xbean:xbean-naming`.

Tests normally never touch it directly; `@DataSourceDefinition` and `@Resource` are the interface.
When you do need the root, it is a port:

```java
JndiContextProvider provider = TestContext.loadService(JndiContextProvider.class);
Context root = provider.writableRoot();              // null when no naming provider is present
```

`writableRoot()` returning `null` means "no naming implementation on this classpath" rather than
an error, because callers disagree about whether that is fatal.

## Database migration

`db-migration-module` is a single artifact with no API: adding it keeps migration bookkeeping
tables out of the per-method cleanup, so Flyway or Liquibase history survives while test rows do
not. It ships these names by default —

```
flyway_schema_history, schema_version, DATABASECHANGELOG, DATABASECHANGELOGLOCK
```

— under `org.os890.jawelte.module.dbmigration.cleanup.exclude-tables`, which is aliased onto
`org.os890.jawelte.module.jpa.cleanup.exclude-tables`. Override the key in a higher-ordinal config
source to add your own.

---

EJB, Spring Data and Jakarta Batch live in `enterprise.md`.
