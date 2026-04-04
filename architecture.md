# Architecture

## Overview

jakwelte is structured in layers. The core layer provides the CDI-based foundation. The integration layer plugs additional Jakarta and third-party technologies into that foundation. The user-facing API sits on top of both and exposes a minimal, JUnit-native interface.

```
┌─────────────────────────────────────────┐
│              User Test Code             │  JUnit 5 @Test
├─────────────────────────────────────────┤
│               jakwelte API              │  Annotations, extensions, assertions
├─────────────────────────────────────────┤
│           Integration Layer             │  JPA/JTA · JAX-RS · MicroProfile
│                                         │  DB-Unit · H2 · Mockito · WireMock
├─────────────────────────────────────────┤
│               Core Layer                │  JUnit 5 · CDI · MicroProfile Config · SPI
└─────────────────────────────────────────┘
```

---

## Core Layer

The core is the heart of jakwelte and the only mandatory dependency. It is responsible for:

- **JUnit 5 extension** — jakwelte registers itself as a JUnit 5 `Extension`, implementing `BeforeAllCallback`, `AfterAllCallback`, `BeforeEachCallback`, `AfterEachCallback` and `InvocationInterceptor` to take full control of the test lifecycle; a single `@ExtendWith(JakwelteExtension.class)` — or the composed `@JakwelteTest` annotation — is all the user needs
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
| `jakwelte-jpa` | JPA + JTA | Managed persistence context, transaction lifecycle |
| `jakwelte-jaxrs` | JAX-RS | Embedded REST container for endpoint testing |
| `jakwelte-microprofile` | MicroProfile | Config, Health, Metrics, OpenAPI support |
| `jakwelte-dbunit` | DB-Unit | Dataset-based database state management |
| `jakwelte-h2` | H2 | In-memory database for fast persistence tests |
| `jakwelte-mockito` | Mockito | CDI-aware mock injection |
| `jakwelte-wiremock` | WireMock | HTTP stub server lifecycle management |

New integrations follow the same pattern and require no changes to the core.

---

## Hexagonal Architecture

jakwelte is structured around the hexagonal architecture (ports and adapters). The core contains the domain logic of the test framework and defines **ports** — technology-agnostic interfaces that describe what the framework needs. Each integration or extension provides an **adapter** that fulfills one or more of those ports for a specific technology.

A port may be realized as a Java SPI (`ServiceLoader`), a CDI extension point, or a plain interface — the port definition is what matters, not the mechanism behind it.

Each port represents the integration boundary between jakwelte's core and an external technology container. A technology container is any runtime that manages its own lifecycle and resources — a JPA persistence provider, a JTA transaction manager, a JAX-RS runtime, an H2 database engine, a WireMock HTTP server. The port defines what jakwelte needs from that container; the adapter wires it up.

**Core ports (examples):**

- `JpaContainerPort` — integrates a JPA persistence provider; manages `EntityManagerFactory`, `EntityManager` and persistence context lifecycle around tests
- `JtaContainerPort` — integrates a JTA transaction manager; controls transaction boundaries, commit and rollback per test
- `JaxRsContainerPort` — integrates an embedded JAX-RS runtime; starts and stops the server, registers resources and providers
- `DatasetContainerPort` — integrates a dataset engine (e.g. DB-Unit); loads, resets and verifies database state around tests
- `HttpStubContainerPort` — integrates an HTTP stub server (e.g. WireMock); manages server lifecycle and stub registration
- `ConfigContainerPort` — integrates a configuration runtime (e.g. MicroProfile Config); provides config values to the core and to test code

**Adapters (examples):**

| Port | Adapter | Technology container |
|---|---|---|
| `JpaContainerPort` | `EclipseLinkAdapter` / `HibernateAdapter` | JPA provider |
| `JtaContainerPort` | `NarayanaAdapter` / `AtomikosAdapter` | JTA transaction manager |
| `JpaContainerPort` + `JtaContainerPort` | `JpaJtaAdapter` | JPA + JTA combined |
| `JaxRsContainerPort` | `JerseyAdapter` / `RestEasyAdapter` | JAX-RS runtime |
| `DatasetContainerPort` | `DbUnitAdapter` | DB-Unit |
| `DatasetContainerPort` + `JpaContainerPort` | `H2Adapter` | H2 in-memory database |
| `HttpStubContainerPort` | `WireMockAdapter` | WireMock |
| `ConfigContainerPort` | `MicroProfileConfigAdapter` | MicroProfile Config |

New integrations are simply new adapters — the core remains untouched.

---

## Configuration

jakwelte uses MicroProfile Config as its single configuration API. Config sources are resolved in standard MicroProfile priority order, with jakwelte providing sensible defaults at the lowest priority.

---

## Test Lifecycle

```
JUnit discovers test class
        │
        ▼
jakwelte JUnit Extension activated
        │
        ▼
CDI container bootstrapped (once per class or per method)
        │
        ▼
ContainerStartedEvent fired → adapters initialize their containers
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
ContainerStoppingEvent fired → adapters tear down their containers
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
- **No magic** — behavior is traceable; every decision jakwelte makes can be understood by reading its own tests and documentation
- **Self-tested** — jakwelte uses itself to test its own integrations wherever possible
- **Minimal surface area** — the public API is as small as possible; complexity lives in the SPI and integration modules, not in user-facing code