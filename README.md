# jawelte

A JUnit 6 test framework for Jakarta EE 11 applications. Each test class gets its own CDI
SE container, and the platform's own annotations — `@DataSourceDefinition`, `@Resource`,
`@Transactional`, `persistence.xml` — mean in a test what they mean in production, so an
application's wiring runs unchanged.

Everything beyond the core is opt-in. You add the module for the technology you are
testing and nothing else; a module that is not on the classpath changes nothing, and most
modules are inert until you use their entry-point annotation.

---

## The minimal setup

Two jawelte artifacts, a CDI runtime, the Jakarta APIs and JUnit. Nothing else is required.

```mermaid
flowchart TB
    test["<b>Your test class</b><br/>@EnableTestBeans"]

    subgraph required["required"]
        direction TB
        core["<b>jawelte-core</b><br/>api + impl<br/><i>JUnit extension, TestContext, SPI lookup</i>"]
        cdi["<b>jawelte-cdi-module</b><br/>api + impl<br/><i>boots the CDI SE container per test class</i>"]
    end

    subgraph runtime["pick exactly one CDI runtime"]
        direction LR
        owb["OpenWebBeans SE"]
        weld["Weld SE"]
    end

    junit["JUnit 6"]

    test --> cdi
    cdi --> core
    core -.discovered by.-> junit
    cdi -.starts.-> owb
    cdi -.or.-> weld
```

```xml
<dependency>
    <groupId>org.os890.jawelte</groupId>
    <artifactId>jawelte-cdi-module-impl</artifactId>
    <version>${jawelte.version}</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.os890.jawelte</groupId>
    <artifactId>jawelte-core-impl</artifactId>
    <version>${jawelte.version}</version>
    <scope>test</scope>
</dependency>
```

Both are needed: `jawelte-cdi-module-impl` brings `cdi-module-api` and `core-api` transitively,
but deliberately not `core-impl` — an `-impl` jar never depends on another module's `-impl`.
Without `jawelte-core-impl` the container does not start.

The Jakarta APIs, Mockito, JUnit and the CDI runtime are declared `provided` or `test` inside
jawelte, and neither scope is transitive — so a consuming project declares those itself too.
`skill/references/setup.md` carries a complete, verified minimal POM.

Add `jawelte-scope-module` when you want `@TestMethodScoped` / `@TestClassScoped`; without it the
framework falls back to `@Dependent` / `@RequestScoped` cleanly.

---

## Use case: persistence against the data source your application declares

A test that runs the real schema migration, the real persistence unit, and the real
transaction manager — with the database redirected to memory for the run.

```mermaid
flowchart TB
    test["<b>Your test class</b><br/>@DataSourceDefinition(...)<br/>persistence.xml with &lt;jta-data-source&gt;"]

    subgraph base["base"]
        core["jawelte-core"]
        cdi["jawelte-cdi-module"]
    end

    jndi["<b>jawelte-jndi-module</b><br/><i>one naming tree, one writable root</i>"]
    ds["<b>jawelte-datasource-module</b><br/><i>@DataSourceDefinition to a real DataSource</i>"]
    jpa["<b>jawelte-jpa-module</b><br/><i>persistence unit, per-method cleanup</i>"]
    jta["<b>jawelte-jta-module</b><br/><i>real transaction manager</i>"]
    mig["<b>jawelte-db-migration-module</b><br/><i>keeps Flyway/Liquibase history out of cleanup</i>"]

    test --> ds
    test --> jpa
    ds --> jndi
    jpa --> jndi
    jta --> jpa
    jta --> jndi
    mig -.excludes tables.-> jpa
    ds --> core
    jpa --> core
    cdi --> core
```

The point of this subset: the `<jta-data-source>` name in `persistence.xml` resolves to the
data source the annotation declares, so schema migration through a plain `DataSource` and
reads through JPA hit **one** database — and the per-method row cleanup reaches both.

---

## Use case: a REST endpoint with its downstream stubbed

```mermaid
flowchart TB
    test["<b>Your test class</b><br/>@EnableJaxRs @EnableWireMock"]

    subgraph base["base"]
        core["jawelte-core"]
        cdi["jawelte-cdi-module"]
    end

    jaxrs["<b>jawelte-jaxrs-module</b><br/><i>embedded REST container (CXF or RESTEasy)</i>"]
    wm["<b>jawelte-wiremock-module</b><br/><i>HTTP stub server per endpoint qualifier</i>"]
    diff["<b>jawelte-content-diff-module</b><br/><i>semantic JSON / XML comparison</i>"]

    test --> jaxrs
    test --> wm
    jaxrs --> diff
    jaxrs --> core
    wm --> core
    cdi --> core
```

Your endpoint runs in a real REST container, its outbound calls hit a WireMock server
injected by qualifier, and the response is compared semantically rather than by string
equality.

---

## Use case: Spring Data repositories over JPA

```mermaid
flowchart TB
    test["<b>Your test class</b><br/>@Inject OrderRepository"]

    subgraph base["base"]
        core["jawelte-core"]
        cdi["jawelte-cdi-module"]
    end

    jpa["<b>jawelte-jpa-module</b>"]
    sd["<b>jawelte-spring-data-module</b><br/><i>discovers repository interfaces, registers real proxies</i>"]
    seed["<b>jawelte-db-testdata-module</b><br/><i>DbSeed fixtures, DbDiff verification</i>"]
    tc["<b>jawelte-testcontrol-module</b><br/><i>@TestControl(testData = ...)</i>"]

    test --> sd
    test --> tc
    sd --> jpa
    seed --> jpa
    tc --> seed
    jpa --> core
    cdi --> core
```

---

## All modules

Everything currently shipped. `core` and `cdi-module` are the base; every other box is
optional and inert until used.

```mermaid
flowchart TB
    core["<b>jawelte-core</b> &nbsp;+&nbsp; <b>jawelte-cdi-module</b><br/><i>every module below builds on these two</i>"]

    subgraph naming["naming and declarative resources"]
        direction LR
        ds["datasource-module<br/><i>@DataSourceDefinition</i>"]
        res["resource-module<br/><i>@Resource(lookup)</i>"]
        jndi["jndi-module<br/><i>shared naming tree</i>"]
    end

    subgraph persistence["persistence"]
        direction LR
        jta["jta-module<br/><i>real transaction manager</i>"]
        jpa["jpa-module<br/><i>persistence unit, cleanup</i>"]
        seed["db-testdata-module<br/><i>DbSeed / DbDiff</i>"]
        sd["spring-data-module<br/><i>repository proxies</i>"]
        mig["db-migration-module<br/><i>migration bookkeeping</i>"]
    end

    subgraph integration["integration surfaces"]
        direction LR
        jaxrs["jaxrs-module<br/><i>embedded REST</i>"]
        diff["content-diff-module<br/><i>JSON / XML diff</i>"]
        wm["wiremock-module<br/><i>HTTP stubs</i>"]
        batch["batch-module<br/><i>Jakarta Batch jobs</i>"]
        ejb["ejb-module<br/><i>@Stateless / @Singleton</i>"]
    end

    subgraph control["scopes, control and assertions"]
        direction LR
        scope["scope-module<br/><i>@TestMethodScoped</i>"]
        tc["testcontrol-module<br/><i>@TestControl</i>"]
        flow["flow-assert-module<br/><i>CDI call-flow assertions</i>"]
    end

    core --- naming
    core --- persistence
    core --- integration
    core --- control

    ds --> jndi
    res --> jndi
    jpa --> jndi
    jta --> jpa
    seed --> jpa
    sd --> jpa
    jaxrs --> diff
    tc --> seed
```

| Module | Covers |
| --- | --- |
| `jawelte-core` | JUnit 6 extension, `TestContext`, prioritized SPI lookup, configuration |
| `jawelte-cdi-module` | CDI SE container per test class, `@TestBean`, auto-mocking |
| `jawelte-scope-module` | `@TestMethodScoped` / `@TestClassScoped` |
| `jawelte-jndi-module` | the in-process naming tree every binding module shares |
| `jawelte-datasource-module` | `@DataSourceDefinition` — builds, binds and injects declared data sources |
| `jawelte-resource-module` | `@Resource(lookup = ...)` on application beans |
| `jawelte-jpa-module` | persistence unit, transaction lifecycle, per-method DB cleanup |
| `jawelte-jta-module` | a real transaction manager (Geronimo, Narayana, Atomikos) |
| `jawelte-db-migration-module` | keeps Flyway / Liquibase bookkeeping tables out of cleanup |
| `jawelte-db-testdata-module` | `DbSeed` fixtures and `DbDiff` verification |
| `jawelte-spring-data-module` | discovers Spring Data repository interfaces, registers real proxies |
| `jawelte-jaxrs-module` | embedded Jakarta REST container for endpoint tests |
| `jawelte-wiremock-module` | WireMock lifecycle, one stub server per endpoint qualifier |
| `jawelte-batch-module` | Jakarta Batch job execution with typed results |
| `jawelte-ejb-module` | `@Stateless` / `@Singleton` mapped onto CDI scopes |
| `jawelte-content-diff-module` | semantic JSON / XML comparison |
| `jawelte-testcontrol-module` | `@TestControl` — per-method scope filtering and DB fixtures |
| `jawelte-flow-assert-module` | records a test's CDI call-flow and asserts it against a diagram |

Every module ships as `-api` (ports and annotations) plus `-impl` (adapters), except
`db-migration-module` and `spring-data-module`, which have no ports of their own and are
single artifacts.

---

## Supported runtimes

Every module is verified against **OpenWebBeans** and **Weld**; the JTA layer additionally
against **Geronimo** and **Narayana**. See `architecture.md` for the port/adapter design and
`verify-all.sh` for the full matrix.
