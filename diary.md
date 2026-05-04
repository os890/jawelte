## 2026-05-04 — Doc alignment for project name and JUnit version

Reviewed mission.md and architecture.md against TICKET-001 and reconciled the project name and JUnit version. Project canonical name is **jawelte** (no `k`); the prior `jakwelte` spelling in both docs was wrong. Target JUnit version is **6** (per TICKET-001), bumped from `JUnit 5` references in architecture.md.

Changes (all on main):
- mission.md: 4× `jakwelte` → `jawelte`.
- architecture.md: all `jakwelte` → `jawelte`; layer diagram and core-layer line bumped to `JUnit 6`; Section "Core Layer / JUnit 5 extension" rewritten to use `@EnableTestBeans` (TICKET-001's user-facing API) and the correct JUnit callback list (`BeforeAllCallback`, `BeforeEachCallback`, `TestInstancePostProcessor`, `AfterEachCallback`, `AfterAllCallback`); `JakwelteExtension` and `@JakwelteTest` references removed (impl-detail names should not appear in this overview); `ContainerStartedEvent` → `ContainerStarted` (matching TICKET-001's published event class); `ContainerStoppingEvent` replaced with the descriptive placeholder `container shutdown event (TBD)` because no ticket has defined a stop event yet; module-table artifact prefixes `jakwelte-*` → `jawelte-*`.

Reviewed and explicitly approved by os890 before applying. Edits intentionally minimal and high-level — architecture.md is meant to be a high-level overview that evolves per real ticket, not a per-ticket spec.

## 2026-05-04 — TICKET-001 scaffold: root pom + Maven Wrapper + core/ aggregator

Task #5 of TICKET-001 implementation. Created on branch `1-ticket-001-core-junit-adapter-and-spi-foundation`.

- **Root `pom.xml`** (`org.os890.jawelte:jawelte-parent:0.1.0-SNAPSHOT`, packaging=pom): aggregator + parent. Pinned versions for every plugin and dependency in `<pluginManagement>` / `<dependencyManagement>` (per decision: all plugins pinned at root). Properties include the coverage thresholds (line 80%, branch 70%) for use by the future coverage aggregator. Active build plugins at parent level: maven-enforcer-plugin (requireJavaVersion[25,), requireMavenVersion[3.9,), dependencyConvergence, bannedDependencies `javax.*:*`) and apache-rat-plugin (with the agreed exclude list — `**/target/**`, IDE folders, mvnw, `.gitignore`, `**/*.md`, `logo/`, `docs/`, `errors/`, `tickets/`, the local-only docs, `**/META-INF/services/**`). Other gate plugins (Checkstyle, JaCoCo, Javadoc) declared in `<pluginManagement>` only — they will be activated per-module in subsequent tasks where Java sources / coverage actually exist.
- **Maven Wrapper** generated via `mvn -N wrapper:wrapper -Dmaven=3.9.14`: `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`. Pin: Maven 3.9.14, distribution from repo.maven.apache.org.
- **`core/pom.xml`** (`jawelte-core`, packaging=pom): aggregator listing `api` + `impl` submodules.
- **`core/api/pom.xml`** (`jawelte-core-api`, packaging=jar): provided dependencies `junit-jupiter-api`, `jakarta.enterprise.cdi-api`, `jakarta.annotation-api`. No source files yet (added in task #6).
- **`core/impl/pom.xml`** (`jawelte-core-impl`, packaging=jar): depends on `jawelte-core-api` (compile), provided `junit-jupiter-api` + `jakarta.enterprise.cdi-api` + `jakarta.annotation-api`. No source files yet (added in task #7).

Verification: `./mvnw validate` succeeds; reactor walks all 4 modules; enforcer rules pass on each (Java 25.0.2 ✓, Maven 3.9.14 ✓, `javax.*` banned ✓, dependency convergence ✓).


## 2026-05-04 — TICKET-001 task #6: core/api implementation

Wrote the complete public API of jawelte-core-api per TICKET-001's API + SPI sections (10 source files, ~520 LOC of code + Javadoc).

Package layout (deliberately kept matching TICKET-001 line for line):

- `org.os890.jawelte.core.api`
    - `EnableTestBeans` — annotation (TYPE, RUNTIME), `@ExtendWith(EnableTestBeans.Proxy.class)` meta-annotated. Two attributes: `limitToTestBeans` (default false), `manageContainer` (default true). Carries the nested `Proxy` class — JUnit's no-arg-instantiated bridge that ServiceLoader-loads the single `TestBeansExtension` provider on first callback and delegates every JUnit lifecycle method (`beforeAll`, `beforeEach`, `postProcessTestInstance`, `afterEach`, `afterAll`) to it. Zero / multiple providers throw `IllegalStateException` with the exact messages mandated by TICKET-001's SPI section. `core/api` has no compile-time dependency on `core/impl`.
    - `TestBean` — annotation (TYPE + ANNOTATION_TYPE + FIELD, RUNTIME, `@Repeatable(TestBeans.class)`), attributes `bean` and `beanProducer` defaulting to `void.class`.
    - `TestBeans` — repeatable container annotation.
- `org.os890.jawelte.core.api.port`
    - `TestContext` — interface with `getTestClass()` and the typed `bindMetadata` / `getMetadata` / `unbindMetadata` API.
    - `TestBeansExtension` — bootstrap SPI; extends the five JUnit 6 callbacks (`BeforeAllCallback`, `BeforeEachCallback`, `TestInstancePostProcessor`, `AfterEachCallback`, `AfterAllCallback`).
    - `TestBeanContainerPort` — single-impl SPI for the CDI runtime adapter; takes `TestContext`, no JUnit API.
    - `TestModuleLifecyclePort` — multi-impl SPI for feature modules; default no-op methods.
- `org.os890.jawelte.core.api.event`
    - `ContainerStarted(Class<?> testClass)`.
    - `BeforeScopeStarted(Class<? extends Annotation> scope)` with `veto()` / `isVetoed()`.
    - `AfterTestTransaction(boolean committed, String testMethodName)`.

All public types / members carry full Javadoc (every parameter, every return, every state). Apache 2.0 license header on every file. No `final` modifier on classes or methods (per project rule — final-class / final-method blocks CDI proxying). 4-space indent. No JPMS module-info.

Verification: `./mvnw -pl core/api compile -am` succeeds with `release=25`. Reactor builds parent → core aggregator → core/api in 1.1s. Enforcer (Java 25, Maven 3.9, dependency convergence, banned `javax.*`) passes for every module.

Tests for these types live in `tests/core/scenario-*` per TICKET-001 task #8 — none in `core/api/src/test` itself.

