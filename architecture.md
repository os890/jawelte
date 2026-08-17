# Architecture

## Overview

jawelte is structured in layers. The core layer provides the CDI-based foundation. The integration layer plugs additional Jakarta and third-party technologies into that foundation. The user-facing API sits on top of both and exposes a minimal, JUnit-native interface.

```
┌─────────────────────────────────────────┐
│              User Test Code             │  JUnit 6 @Test
├─────────────────────────────────────────┤
│               jawelte API               │  Annotations, extensions, assertions
├─────────────────────────────────────────┤
│           Integration Layer             │  JPA/JTA · JAX-RS · MicroProfile
│                                         │  DB-Unit · H2 · Mockito · WireMock
├─────────────────────────────────────────┤
│               Core Layer                │  JUnit 6 · CDI · MicroProfile Config · SPI
└─────────────────────────────────────────┘
```

---

## Core Layer

The core is the heart of jawelte and the only mandatory dependency. It is responsible for:

- **JUnit 6 extension** — jawelte registers itself as a JUnit 6 `Extension`, implementing `BeforeAllCallback`, `BeforeEachCallback`, `TestInstancePostProcessor`, `AfterEachCallback` and `AfterAllCallback` to take full control of the test lifecycle; a single `@EnableTestBeans` annotation on the test class is all the user needs
- **CDI container lifecycle** — bootstrapping and shutting down the CDI container around each test run
- **CDI beans and events** — all internal communication between components happens through CDI events; integrations listen and react without tight coupling
- **MicroProfile Config** — drives all configuration, both internal and user-facing; every default can be overridden via config sources (properties file, environment, system properties)
- **SPI** — a well-defined set of extension points that allows integrations and user code to participate in the test lifecycle

The core has no dependency on any integration. It must remain lean, fast and self-contained.

---

## Integration Layer

Each integration is an independent module that hooks into the core via the SPI and CDI events. Integrations are fully opt-in — adding a dependency on the module is all that is required to activate it.

| Module | Technology | Purpose |
|---|---|---|
| `jawelte-jndi-module` | JNDI | The in-process naming tree every binding module shares — one provider, one writable root |
| `jawelte-datasource-module` | JDBC (`@DataSourceDefinition`) | Builds, binds and injects the data sources a test or a bean declares |
| `jawelte-jpa-module` | JPA + JTA | Managed persistence context, transaction lifecycle, per-method DB cleanup |
| `jawelte-ejb-module` | EJB session-bean annotations | Maps `@jakarta.ejb.Singleton` / `@jakarta.ejb.Stateless` to CDI scopes plus an implicit class-level `@jakarta.transaction.Transactional` |
| `jawelte-jaxrs-module` | Jakarta REST | Embedded REST container for endpoint testing |
| `jawelte-microprofile` | MicroProfile | Config, Health, Metrics, OpenAPI support |
| `jawelte-dbunit` | DB-Unit | Dataset-based database state management |
| `jawelte-h2` | H2 | In-memory database for fast persistence tests |
| `jawelte-mockito` | Mockito | CDI-aware mock injection |
| `jawelte-wiremock-module` | WireMock | HTTP stub server lifecycle management |
| `jawelte-batch-module` | Jakarta Batch (jBatch) | CDI event-driven job execution with synchronous polling and pluggable timeout policy |
| `jawelte-flow-assert-module` | cdi-flow recorder (`dynamic-cdi-flow-renderer`) | Records the CDI call-flow of a test method and asserts it against an expected sequence-diagram |

New integrations follow the same pattern and require no changes to the core.

---

## Hexagonal Architecture

jawelte is structured around the hexagonal architecture (ports and adapters). The core contains the domain logic of the test framework and defines **ports** — technology-agnostic interfaces that describe what the framework needs. Each integration or extension provides an **adapter** that fulfills one or more of those ports for a specific technology.

A port may be realized as a Java SPI (`ServiceLoader`), a CDI extension point, a CDI-injectable bean, or a plain interface — the port definition is what matters, not the mechanism behind it.

Each port represents the integration boundary between jawelte's core and an external technology container. A technology container is any runtime that manages its own lifecycle and resources — a CDI SE container, a JPA persistence provider, a JTA transaction manager, a JAX-RS runtime, an H2 database engine, a WireMock HTTP server. The port defines what jawelte needs from that container; the adapter wires it up.

**Core ports (in `core/api`):**

- `TestBeansExtension` — JUnit-side driving port; the proxy meta-extension on `@EnableTestBeans` resolves the single SPI provider and forwards every JUnit lifecycle callback to it
- `TestBeanContainerPort` — manages the bean container lifecycle around the test class (`beforeAll` / `afterAll`) and around each test method (`beforeEach` / `afterEach` / `postProcessTestInstance`); exactly one implementation must be on the classpath
- `TestModuleLifecyclePort` — additional driven port that feature modules implement to participate in the lifecycle (zero-or-more, sorted by `@Priority`)
- `TestContext` — in-flight test-state holder owned by the framework; offers `getTestClass()`, typed metadata binding, the static `get()` accessor (active only inside the bootstrap window), and the `loadService(...)` helper that resolves SPI instances via MicroProfile Config + `ServicePriorityResolver`
- `ServicePriorityResolver` — drives prioritized SPI selection (lowest `@Priority` wins; missing `@Priority` sorts last; class-name tiebreak)
- `ConfigResolver` — single-method SPI for raw `String` config-key lookup; the dot-then-underscore fallback lives here

**Module ports (in `cdi-module/api`):**

- `ExcludedPackageFilter` — auto-mock exclude policy; the default impl reads its prefix list from MicroProfile Config and is overridable via `@Priority`
- `WhitelistFilter` — `limitToTestBeans=true` allow policy; the default impl combines the framework allowlist with the per-test `@TestBean` targets and is overridable via `@Priority`
- `BeanTypeContribution` — the bean types a module supplies itself in the container being built, so nothing stands in for them. Unlike the policy ports above, **every** provider is consulted and the results are unioned, so discovery is plain `ServiceLoader` and `@Priority` plays no part — each module knows only its own contribution, and there is nothing for a winner to decide. It exists because the container cannot be asked: `addBean()` registrations are invisible to `BeanManager.getBeans(...)` for the whole of `AfterBeanDiscovery`, and `ProcessSyntheticBean` is not fired until every `AfterBeanDiscovery` observer has run (measured on OpenWebBeans 4.1.0 and Weld alike), at any observer priority. Auto-mocking is the consumer today: without the contribution it registers a competing bean of the same type and the deployment fails with `AmbiguousResolutionException` (#124). Making the module's bean win as a `@Priority`-selected alternative was tried and rejected — the ambiguity goes away, but resolution then depends on two extensions agreeing about priority numbers, and the losing case silently injects the mock instead of the real object. The signature takes a `TestContext`, not a `BeanManager`: `cdi-module/api` carries ports only and depends on nothing but `core/api`, so a contributing extension publishes what it discovered through `TestContext.bindMetadata(...)` and the provider reads it back.

**scope-module additions (in `scope-module/api`):**

- `@TestMethodScoped` — CDI normal scope (`@NormalScope(passivating=false)`); follows `@ApplicationScoped` semantics with a per-test-method bean lifetime. Contexts always report `isActive() == true`; `@PreDestroy` runs in `afterEach`.
- `@TestClassScoped` — CDI normal scope (`@NormalScope(passivating=false)`); same shape, with a per-test-class bean lifetime. `@PreDestroy` runs after `@AfterAll`, before the CDI container shuts down.

scope-module ships **no new ports of its own**. It implements the existing `TestModuleLifecyclePort` and the standard CDI `Extension` SPI. The cross-module scope-override contract uses two mechanisms — both keep consumer modules free of a compile-time dep on scope-module: (1) the `BeanScopeMapper` SPI in `core/api/port` (TICKET-001) — scope-module ships SL-registered providers (`@TestBean` → `@TestClassScoped`, `@SessionScoped` → `@TestMethodScoped`, `@ConfigBean` → `@TestClassScoped`) that `core/impl`'s `ScopeRemapCdiExtension` walks at `ProcessAnnotatedType` time; (2) MP Config keys defaulted in scope-module's `microprofile-config.properties`, read reflectively by consumer modules to resolve scope-annotation FQCNs (cdi-module's auto-mock scope, wiremock-module's registry remap, ejb-module's `@Singleton` mapping). When scope-module is absent at runtime the SPI providers don't exist and the MP Config defaults aren't shipped — consumer modules degrade cleanly to their `@Dependent` / `@RequestScoped` / `@ApplicationScoped` fallbacks.

**jndi-module additions (in `jndi-module/api`):**

- `JndiContextProvider` — hands out the single writable JNDI root every binding module shares. The root is built with xbean's `supportReferenceable` **off**, so the tree holds what was put into it: xbean's default replaces a bound `Referenceable` with its `Reference` at bind time and rebuilds a fresh object on every lookup, which would mean two lookups of one name disagreeing and a pooled `DataSource` getting one pool per lookup. An EE container's tree behaves the way this one now does — what was deployed is what resolves. `writableRoot()` returning `null` is the documented "no naming provider in this JVM" answer rather than a failure, because callers disagree about whether that is fatal: jta-module cannot work without naming (its vendor integrations resolve artifacts by name), while a module that only publishes names alongside another resolution path can carry on.

It is a module rather than a core port because **the core never looks anything up by name**. The core's ports describe what the test framework needs — a lifecycle, a test context, prioritized SPI lookup, configuration; naming is what individual *integrations* need. Sharing one root is mandatory rather than tidy: installing an in-process provider installs a *fresh* writable root, so a second module doing it independently would discard whatever the first had already bound, and the two would wipe each other out depending on boot order.

jta-module depends on jndi-module (api at compile scope, impl at runtime scope — it codes against the port, and an adapter has to be present for a tree to exist). Nothing else does, so a project not using JTA never pulls it in. `jndi-module/api` depends on `core/api` for `TestContext.loadService(...)` and the dependency points that way only.

**datasource-module additions (in `datasource-module/api`):**

- `DataSourceFactory` — turns a `@DataSourceDefinition` into a `javax.sql.DataSource`. There is no common configuration interface for data sources, so the shipped implementation applies the annotation's attributes as JavaBean setters, trying several candidate names per attribute because drivers disagree (H2 has `setURL` *and* `setUrl`, PostgreSQL only the latter). Pool-sizing, `transactional` and `isolationLevel` are documented as ignored rather than half-applied; registering a pooling factory at a lower priority is the supported way to honour them.

datasource-module ships **no entry-point annotation of its own** — the platform's `@DataSourceDefinition` is the trigger, so a test declares only standard Jakarta API. It depends on neither jpa-module nor jta-module, and carries no JDBC-driver dependency: the vendor class the annotation names is loaded from the test's own classpath, which is the one place reflection is unavoidable here. It binds through jndi-module's `JndiContextProvider`, so its entries and jta-module's transaction artifacts share one naming tree.

**jpa-module additions (in `jpa-module/api`):**

- `@PersistenceConfig` — class-level JPA configuration (`fileMode`, `filePath`, `persistenceUnits`).
- `@ReadOnly` — `@InterceptorBinding` modifier for a `@Transactional` method or type that discards writes (flush mode `COMMIT` + rollback-only).
- `TransactionStrategy` — pluggable transaction-management facade; the default impl drives RESOURCE_LOCAL, a future jta-module substitutes JTA via `@Priority`.
- `DbCleanupStrategy` — pluggable per-method database cleanup; the default impl issues JPQL `DELETE` per resolved entity.
- `EntityResolver`, `PersistenceUnitConnectionResolver`, `PersistencePropertyResolver` — supporting SPIs for cleanup, JDBC unwrap, and per-PU EMF property contributions.
- `TransactionStarted` / `TransactionBeforeCompletion` / `TransactionCommitted` / `TransactionRolledBack` — CDI events fired by the active strategy at the documented points.

`@Transactional` and `@TransactionScoped` are reused from `jakarta.transaction-api` — jpa-module registers an `@InterceptorBinding` for the former and a CDI `Context` for the latter; neither annotation is redeclared.

**Adapters:**

| Port | Adapter | Technology container | Module |
|---|---|---|---|
| `TestBeansExtension` | `DelegatingJUnitExtension` | JUnit 6 | `core/impl` |
| `TestContext` | `TestContextImpl` | (in-process holder) | `core/impl` |
| `ServicePriorityResolver` | `DefaultServicePriorityResolver` | (in-process) | `core/impl` |
| `ConfigResolver` | `ConfigResolverAdapter` | MicroProfile Config | `core/impl` |
| `TestBeanContainerPort` | `CdiTestBeanContainer` (+ `TestBeansCdiExtension`) | CDI SE (OpenWebBeans / Weld) | `cdi-module/impl` |
| `ExcludedPackageFilter` | `DefaultExcludedPackageFilter` | MicroProfile Config | `cdi-module/impl` |
| `WhitelistFilter` | `DefaultWhitelistFilter` | (in-process) | `cdi-module/impl` |
| `TestModuleLifecyclePort` | `ScopeLifecycleAdapter` (`@Priority(100)`) + `TestScopeCdiExtension` | CDI runtime (`BeanManager` from `SeContainer` on `TestContext`) | `scope-module/impl` |
| `JndiContextProvider` | `DefaultJndiContextProvider` (`@Priority(Integer.MAX_VALUE)`) | JNDI naming provider (xbean-naming at `provided` scope — compiled against, not transitive; absence answered with `null` after a `Class.forName` probe) | `jndi-module/impl` |
| `TestModuleLifecyclePort` | `DataSourceLifecycleAdapter` (`@Priority(150)`) + `DataSourceDefinitionCdiExtension` | CDI runtime + whichever JDBC vendor class the annotation names | `datasource-module/impl` |
| `DataSourceFactory` | `DefaultDataSourceFactory` (`@Priority(Integer.MAX_VALUE)`) | (reflective JavaBean configuration of the vendor class) | `datasource-module/impl` |
| `BeanTypeContribution` | `DataSourceBeanTypeContribution` (contributes `javax.sql.DataSource`, but only once a `@DataSourceDefinition` was discovered) | (in-process, reads the extension off `TestContext`) | `datasource-module/impl` |
| `TestModuleLifecyclePort` | `JpaLifecycleAdapter` (`@Priority(200)`) + `JpaCdiExtension` | CDI runtime + JPA provider (Hibernate) + JDBC driver (H2) | `jpa-module/impl` |
| `TransactionStrategy` | `DefaultResourceLocalTransactionStrategy` (`@Priority(Integer.MAX_VALUE)`) | (in-process) | `jpa-module/impl` |
| `DbCleanupStrategy` | `JpqlDeleteDbCleanupStrategy` (`@Priority(Integer.MAX_VALUE)`) | (in-process; calls JPA) | `jpa-module/impl` |
| `EntityResolver` | `JpaMetamodelEntityResolver` (`@Priority(Integer.MAX_VALUE)`) | JPA metamodel | `jpa-module/impl` |
| `PersistenceUnitConnectionResolver` | `DefaultPersistenceUnitConnectionResolver` (`@Priority(Integer.MAX_VALUE)`) | JDBC | `jpa-module/impl` |
| `EjbAnnotationMapper` | `DefaultEjbAnnotationMapper` (`@Priority(Integer.MAX_VALUE)`) + `EjbAnnotationExtension` | CDI runtime + xbean-finder classpath scan | `ejb-module/impl` |
| `TestModuleLifecyclePort` | `WireMockLifecycleAdapter` (`@Priority(75)`) + `WireMockCdiExtension` + `WireMockRegistryScopeRemap` (`BeanScopeMapper` SPI provider) | CDI runtime + WireMock library (`WireMockServer`, `WireMockRuntimeInfo`) | `wiremock-module/impl` |
| `TimeoutHandler` | `ThrowingTimeoutHandler` (`@Priority(Integer.MAX_VALUE)`) plus opt-in `PopulateLatestSnapshotTimeoutHandler` (`@Priority(Integer.MAX_VALUE - 100)`, ships in the same jar but not pre-registered) | CDI runtime + any jBatch implementation (BatchEE, JBeret) discovered via `BatchRuntime.getJobOperator()` | `batch-module/impl` |
| `TestModuleLifecyclePort` | `FlowAssertLifecycleAdapter` (`@Priority(Integer.MAX_VALUE)`) + `FlowRecordingConfigSource` (MicroProfile `ConfigSource` SPI provider, ordinal 250) + `CapturingFlowSink` (`FlowSink` SPI of the recorder) | cdi-flow recorder (portable CDI extension attaching a recording interceptor) | `flow-assert-module/impl` |
| `FlowDialect`, `FlowDiffEngine`, `FlowRecordingPort` | `MermaidFlowDialect` / `PlantUmlFlowDialect`, `AlignmentFlowDiffEngine`, `StaticFlowRecordingPort` (all `@Priority(Integer.MAX_VALUE)`) | (in-process; renders through the recorder) | `flow-assert-module/impl` |

**ejb-module additions (in `ejb-module/api`):**

- `EjbAnnotationMapper` — pluggable mapping from EJB session-bean annotations to CDI scopes plus interceptor bindings. The default impl maps `@jakarta.ejb.Singleton` → `@ApplicationScoped` (upgraded to the scope configured under MP Config key `org.os890.jawelte.module.ejb.singleton.default-scope` — scope-module ships `@TestClassScoped` as the default; FQCN loaded reflectively so ejb-module has no compile-time dep on scope-module) plus an implicit class-level `@jakarta.transaction.Transactional`, and `@jakarta.ejb.Stateless` → `@Dependent` plus the same `@Transactional`. `@Stateful`, `@MessageDriven`, `@Lock`, `@AccessTimeout`, `@Startup`, `@DependsOn`, `@Schedule`, `@Asynchronous`, `@TransactionAttribute` are silently ignored by the default; a custom additional mapper at lower `@Priority` can claim any of them.

`ejb-module/impl` scans the classpath for `@Singleton` / `@Stateless` types during `BeforeBeanDiscovery` and feeds them to `addAnnotatedType` — neither OpenWebBeans nor Weld treats `BeforeBeanDiscovery.addStereotype(...)`-registered annotations as bean-defining for type discovery (the CDI 4.0 spec only encourages this), so the scan is required to keep `bean-discovery-mode="annotated"` working for plain EJB-annotated classes.

**batch-module additions (in `batch-module/api`):**

- `BatchExecution` — concrete CDI event class carrying both the request (jobName, parameters, timeout) and, after the synchronous observer in `batch-module/impl` has driven the job to a terminal `BatchStatus`, the result (executionId, `JobExecution`, status, exit-status). Test code fires it via `Event<BatchExecution>` and reads the result accessors on the same instance once `fire(...)` unblocks.
- `TimeoutHandler` — pluggable policy for "polling loop exceeded `BatchExecution.getTimeout()` while the job is still in a non-terminal status." Resolved via `TestContext.loadService(TimeoutHandler.class)` once per JVM. The default `ThrowingTimeoutHandler` raises `IllegalStateException`; consumers swap behaviour by registering an alternative impl with a lower numeric `@Priority`. `batch-module/impl` ships the opt-in `PopulateLatestSnapshotTimeoutHandler` (populates the event with the latest snapshot and returns without throwing) but does not pre-register it.

`batch-module/impl` ships no `TestModuleLifecyclePort` adapter — it is purely CDI-driven. Two `@ApplicationScoped` beans (the observer that drives the polling loop, and a `@Produces JobOperator` bridge that delegates to `BatchRuntime.getJobOperator()`) handle everything; the CDI runtime discovers them via `beans.xml` with `bean-discovery-mode="annotated"`.

**flow-assert-module additions (in `flow-assert-module/api`):**

- `@EnableFlowAssert` — class-level switch (meta-annotated `@EnableTestBeans`) whose attributes are the recording's configuration; `@ExpectedFlow` — per-method assertion against an expected diagram, resolved by convention when it names no resource.
- `FlowDialect` — one diagram notation, selected by the **file extension of the expected resource**: rendering a recording and parsing a diagram back into the canonical `FlowStep` model. Built-in Mermaid and PlantUML; a custom notation is a new provider.
- `FlowDiffEngine` — the comparison itself, notation-agnostic because it works on `FlowStep`s. One active implementation per JVM, so a custom dialect inherits the built-in comparison instead of bringing one.
- `FlowRecordingPort` — what the running test method recorded; the boundary between the user-facing api and the sink that collects the recorder's output.

Two integration properties are worth stating at this level. First, **the expected file decides the notation**, not configuration: the recording is rendered in whichever notation the expectation is written in, so the recorder's own `cdi-flow.output-format` never takes part in a comparison. Second, **the recorder is configured through MicroProfile Config rather than through an API** — it reads its keys while the container boots, inside the bootstrap window where `TestContext.get()` resolves, which is why a per-test-class annotation can drive a library that only knows about configuration keys. A `ConfigSource` at ordinal 250 performs that translation and answers `cdi-flow.enabled=false` for any test class without the annotation, so a module every consumer pulls in transitively instruments nothing it was not asked to.

**Planned (forward-looking, not yet shipped):** `DatasetContainerPort` (e.g. DB-Unit). Each will land as its own module under `modules/` and follow the same shape as `cdi-module`.

New integrations are simply new adapters — the core remains untouched.

---

## Configuration

jawelte uses MicroProfile Config as its single configuration API. Config sources are resolved in standard MicroProfile priority order, with jawelte providing sensible defaults at the lowest priority.

---

## Test Lifecycle

```
JUnit discovers test class
        │
        ▼
jawelte JUnit extension activated
        │
        ▼
CDI container bootstrapped (once per class or per method)
        │
        ▼
ContainerStarted event fired → adapters initialize their containers
        │
        ▼
── per test method ──────────────────────────────────────────────
        │
        ▼
CDI request scope activated
        │
        ▼
Transaction started (if @Transactional or transactional mode active)
        │
        ▼
Test data loaded → shared dataset + test-method-specific dataset
(referenced via annotation on the test method)
        │
        ▼
Test instance injected as CDI bean
        │
        ▼
@Test method executes
        │
        ▼
Assert phase executes (DB-Unit dataset verification, etc.)
        │
        ▼
EntityManager flushed → changes visible in DB for assertion
        │
        ├── rollback mode (default, fast):
        │       transaction rolled back → DB state restored automatically
        │
        └── commit mode (alternative):
                transaction committed → in-memory DB reset to initial state
        │
        ▼
CDI request scope destroyed
── end per test method ──────────────────────────────────────────
        │
        ▼
container shutdown event (TBD) fired → adapters tear down their containers
        │
        ▼
CDI container shut down
```

### Key lifecycle properties

- **Request scope per test method** — each test method runs in its own CDI request context, giving it isolated bean instances and clean state
- **Shared transaction** — the framework setup (data loading), the test method itself, and the assert phase all share one transaction; this guarantees a consistent view of the data across all three phases
- **Flush before assert** — the framework flushes the `EntityManager` before the assert phase so that changes are written to the DB and can be verified, without committing
- **Rollback mode** — the default; the shared transaction is rolled back after the assert, leaving the DB in its original state with no cleanup required; this is the fastest execution mode
- **Commit mode** — an alternative for cases where a real commit is needed; the transaction is committed and the in-memory DB is reset to its initial state afterwards
- **Test data** — datasets are defined externally (e.g. DB-Unit XML) and referenced per test method via an annotation; a dataset can be shared across all methods in a class or specific to a single method, and both can be combined

---

## Design Principles

- **CDI-first** — prefer CDI beans, producers and events over static state or proprietary APIs
- **Convention over configuration** — every integration works out of the box; configuration is only needed to override defaults
- **No magic** — behavior is traceable; every decision jawelte makes can be understood by reading its own tests and documentation
- **Self-tested** — jawelte uses itself to test its own integrations wherever possible
- **Minimal surface area** — the public API is as small as possible; complexity lives in the SPI and integration modules, not in user-facing code