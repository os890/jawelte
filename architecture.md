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
| `jawelte-jpa` | JPA + JTA | Managed persistence context, transaction lifecycle |
| `jawelte-jaxrs` | JAX-RS | Embedded REST container for endpoint testing |
| `jawelte-microprofile` | MicroProfile | Config, Health, Metrics, OpenAPI support |
| `jawelte-dbunit` | DB-Unit | Dataset-based database state management |
| `jawelte-h2` | H2 | In-memory database for fast persistence tests |
| `jawelte-mockito` | Mockito | CDI-aware mock injection |
| `jawelte-wiremock` | WireMock | HTTP stub server lifecycle management |

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

**scope-module additions (in `scope-module/api`):**

- `@TestMethodScoped` — CDI normal scope (`@NormalScope(passivating=false)`); follows `@ApplicationScoped` semantics with a per-test-method bean lifetime. Contexts always report `isActive() == true`; `@PreDestroy` runs in `afterEach`.
- `@TestClassScoped` — CDI normal scope (`@NormalScope(passivating=false)`); same shape, with a per-test-class bean lifetime. `@PreDestroy` runs after `@AfterAll`, before the CDI container shuts down.

scope-module ships **no new ports of its own**. It implements the existing `TestModuleLifecyclePort` and the standard CDI `Extension` SPI. Two small `record` types in `core/api/port` (`TestBeanDefaultScope`, `AutoMockDefaultScope`) provide a cross-module override mechanism: scope-module binds them on `TestContext` during `BeforeBeanDiscovery`; cdi-module reads them in `AfterBeanDiscovery` to pick the scope of `@TestBean` static-field synthetic beans and auto-mocks. The records carry no behaviour — just a `Class<? extends Annotation>` token — so neither module compile-depends on the other.

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

**Planned (forward-looking, not yet shipped):** `JpaContainerPort` / `JtaContainerPort` (persistence + transactions), `JaxRsContainerPort` (embedded REST runtime), `DatasetContainerPort` (e.g. DB-Unit), `HttpStubContainerPort` (e.g. WireMock). Each will land as its own module under `modules/` and follow the same shape as `cdi-module`.

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