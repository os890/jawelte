# Setup

## Repository

jawelte is published to a plain Maven repository served over GitHub Pages. A `<repositories>`
entry is **not** inherited through a dependency's POM, so every consuming project needs its own:

```xml
<repositories>
    <repository>
        <id>os890</id>
        <url>https://os890.github.io/os890-maven-repo/</url>
        <releases><enabled>true</enabled></releases>
        <snapshots><enabled>false</enabled></snapshots>
    </repository>
</repositories>
```

## Minimal working POM

Verified end to end against the published **0.3.0** artifacts: this exact POM, built from an
empty local repository, resolves and runs the documented tests.

```xml
<properties>
    <jawelte.version>0.3.0</jawelte.version>
</properties>

<dependencies>
    <!-- jawelte: impl jars carry the adapters, api jars the annotations and ports -->
    <dependency>
        <groupId>org.os890.jawelte</groupId>
        <artifactId>jawelte-cdi-module-impl</artifactId>
        <version>${jawelte.version}</version>
        <scope>test</scope>
    </dependency>
    <!-- required explicitly: cdi-module-impl deliberately does NOT depend on core-impl -->
    <dependency>
        <groupId>org.os890.jawelte</groupId>
        <artifactId>jawelte-core-impl</artifactId>
        <version>${jawelte.version}</version>
        <scope>test</scope>
    </dependency>

    <!-- provided in jawelte-parent, therefore not transitive: declare them here -->
    <dependency>
        <groupId>jakarta.enterprise</groupId>
        <artifactId>jakarta.enterprise.cdi-api</artifactId>
        <version>4.1.0</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>jakarta.annotation</groupId>
        <artifactId>jakarta.annotation-api</artifactId>
        <version>3.0.0</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>jakarta.inject</groupId>
        <artifactId>jakarta.inject-api</artifactId>
        <version>2.0.1</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.eclipse.microprofile.config</groupId>
        <artifactId>microprofile-config-api</artifactId>
        <version>3.1</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.smallrye.config</groupId>
        <artifactId>smallrye-config</artifactId>
        <version>3.10.0</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-core</artifactId>
        <version>5.14.2</version>
        <scope>test</scope>
    </dependency>

    <!-- exactly one CDI runtime -->
    <dependency>
        <groupId>org.apache.openwebbeans</groupId>
        <artifactId>openwebbeans-se</artifactId>
        <version>4.1.0</version>
        <scope>test</scope>
    </dependency>

    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>6.0.0</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <version>3.27.3</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

No surefire configuration, JVM argument or custom runner is needed. `mvn test` is the whole story.

## CDI runtime: pick exactly one

Every module is verified against both.

| Runtime | Coordinates | Version verified |
| --- | --- | --- |
| OpenWebBeans | `org.apache.openwebbeans:openwebbeans-se` | 4.1.0 |
| Weld | `org.jboss.weld.se:weld-se-shaded` | 6.0.4.Final |

Error messages differ between them; when reading a failure, note which one is on the classpath.
Ambiguous resolution reads `AmbiguousResolutionException` on OpenWebBeans and `WELD-001409` on
Weld, for example.

## Required test resources

### `src/test/resources/META-INF/beans.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="https://jakarta.ee/xml/ns/jakartaee"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/beans_4_0.xsd"
       version="4.0"
       bean-discovery-mode="annotated"/>
```

### `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`

```
mock-maker-subclass
```

One line, no other content. Required in **every** module whose tests rely on auto-mocking.
Without it Mockito's inline mock maker cannot self-attach its agent under a modern JDK inside
surefire; it then refuses every type and auto-mocking is silently off. The framework reports the
refusal rather than swallowing it, so the symptom is an error naming the type it could not mock.

Setting `-Dmockito.mock.maker=subclass` on the Maven command line or via `argLine` does **not**
work — Mockito 5.14.2 honours only the classpath resource here.

## Packages of the public types

The package does **not** always follow the module: the entry-point annotations live in core, not
in cdi-module. Guessing here is a common way to produce a file that will not compile.

| Package | Types |
| --- | --- |
| `org.os890.jawelte.core.api` | `@EnableTestBeans`, `@TestBean`, `@ConfigBean`, `TestContext` |
| `org.os890.jawelte.module.scope.api` | `@TestClassScoped`, `@TestMethodScoped` |
| `org.os890.jawelte.module.jpa.api` | `@PersistenceConfig`, `@ReadOnly` (events in `…jpa.api.event`) |
| `org.os890.jawelte.module.testcontrol.api` | `@TestControl` |
| `org.os890.jawelte.module.dbtestdata.api` | `DbSeed`, `DbDiff` |
| `org.os890.jawelte.module.jaxrs.api` | `@EnableJaxRs`, `TestUrl`, `ResponseDiff` |
| `org.os890.jawelte.module.wiremock.api` | `@EnableWireMock`, `@WireMockEndpoint` |
| `org.os890.jawelte.module.contentdiff.api` | `ContentDiff` |
| `org.os890.jawelte.module.batch.api` | `BatchExecution` |
| `org.os890.jawelte.module.flowassert.api` | `@EnableFlowAssert`, `@ExpectedFlow`, `RecordedFlows`, `FlowDiff` |

## Per-module dependencies

Add the `-api` and `-impl` pair for each module you use, plus that module's own third-party
requirements. All jawelte artifacts share `${jawelte.version}`.

The versions in the third column are the ones jawelte is built and verified against. Nothing
enforces them, but a mismatch in Hibernate or the Jakarta APIs is a likely cause of a failure that
looks like a jawelte bug.

| Module | Add these artifacts | You must also supply |
| --- | --- | --- |
| scope | `jawelte-scope-module-api`, `-impl` | — |
| jndi | `jawelte-jndi-module-api`, `-impl` | `org.apache.xbean:xbean-naming` 4.30 (`provided`, so not transitive) |
| datasource | `jawelte-datasource-module-api`, `-impl` | a JDBC driver / `DataSource` implementation for the class the `@DataSourceDefinition` names (`com.h2database:h2` 2.3.232 for the examples here), **plus `jawelte-jndi-module-impl` and `org.apache.xbean:xbean-naming`** — see the note below |
| resource | `jawelte-resource-module-api`, `-impl` | `jawelte-jndi-module-impl` (only the api comes along) plus `xbean-naming` |
| jpa | `jawelte-jpa-module-api`, `-impl` | `jakarta.persistence-api` 3.2.0, `jakarta.transaction-api` 2.0.1, `org.hibernate.orm:hibernate-core` 7.0.4.Final, a JDBC driver (`com.h2database:h2` 2.3.232 by default), `jawelte-jndi-module-impl` |
| jta | `jawelte-jta-module-api`, `-impl` | everything jpa needs, plus one transaction manager: `org.apache.geronimo.components:geronimo-transaction` 4.0.0, Narayana 7.0.0.Final or Atomikos 6.0.1 |
| db-migration | `jawelte-db-migration-module` (single artifact) | Flyway or Liquibase, and everything jpa needs |
| db-testdata | `jawelte-db-testdata-module-api`, `-impl` | everything jpa needs (DBUnit comes transitively) |
| testcontrol | `jawelte-testcontrol-module-api`, `-impl` | `jawelte-db-testdata-module-impl` (only the api comes along) |
| spring-data | `jawelte-spring-data-module` (single artifact) | everything jpa needs, plus `org.springframework.data:spring-data-jpa` 4.0.5 |
| ejb | `jawelte-ejb-module-api`, `-impl` | `jakarta.ejb-api` 4.0.1 (`provided`, so not transitive) |
| jaxrs | `jawelte-jaxrs-module-api`, `-impl` | a Jakarta REST implementation — CXF 4.1.2 or RESTEasy 7.0.0.Final |
| wiremock | `jawelte-wiremock-module-api`, `-impl` | — (WireMock comes transitively) |
| content-diff | `jawelte-content-diff-module-api`, `-impl` | — (Jackson comes transitively) |
| batch | `jawelte-batch-module-api`, `-impl` | `jakarta.batch:jakarta.batch-api` 2.1.1 (**not transitive**) plus a runtime: `org.apache.batchee:batchee-jbatch` 2.0.0 needs nothing else, JBeret 3.1.0.Final additionally needs `jakarta.transaction-api` and `org.wildfly.core:wildfly-security-manager` |
| flow-assert | `jawelte-flow-assert-module-api`, `-impl` | — (the cdi-flow recorder comes transitively at `compile` scope) |

One rule explains most of this column: a library reaches a consumer only if the module needs it
at `compile` scope. DBUnit, WireMock, Jackson and the cdi-flow recorder are `compile` and arrive
on their own. The Jakarta APIs, Mockito, Hibernate, xbean-naming and **Spring Data JPA** are
`provided` (some of them managed at `test` in `jawelte-parent` and raised to `provided` by the
module that needs them), and neither scope is transitive — so the consumer declares them.

**Anything that binds a name needs the naming tree, and the naming tree needs two artifacts.**
`datasource-module` and `resource-module` both bind into JNDI, and both fail quietly without
`jawelte-jndi-module-impl` *and* `org.apache.xbean:xbean-naming`. Quietly is the problem: with
neither of them present a `@DataSourceDefinition` is still built and still injects, so the test
looks wired up — but the name is never bound, and `new InitialContext().lookup(...)` fails with
`NoInitialContextException`. `@Resource(lookup = ...)` and a `<jta-data-source>` in
`persistence.xml` resolve through that same tree, so they silently do not work either.

An `-impl` jar depends only on the `-api` of the modules it integrates with, never on their
`-impl`, except where the table says otherwise. So adding `jpa-module-impl` does not pull
`jndi-module-impl`, and a persistence test without it has no naming tree.

`db-migration-module` and `spring-data-module` have no ports of their own and ship as single
artifacts; every other module is an `-api` / `-impl` pair.

## Troubleshooting startup

| Symptom | Cause |
| --- | --- |
| `No TestBeansExtension found via ServiceLoader. Add core-impl to the test classpath.` | `jawelte-core-impl` missing |
| `Multiple TestBeansExtension implementations found` | two core-impl versions on the classpath |
| Nothing resolves from the repository | missing `<repositories>` entry |
| An error naming a type that could not be mocked | missing `mockito-extensions` mock-maker file |
| `AmbiguousResolutionException` / `WELD-001409` on an auto-mocked type | a pre-0.3.0 defect: the same unsatisfied type injected into both an application bean and the test class produced two mocks. Upgrade — see `core-testing.md` |
| Beans are not discovered at all | missing or wrongly-moded `beans.xml` |
| A collateral failure names an unrelated test class | a `beforeAll` container-start failure; the message names the class that really failed |
