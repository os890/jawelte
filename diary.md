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


## 2026-05-04 — TICKET-001 task #7: core/impl implementation

Wrote core/impl per TICKET-001's Core Structure section (4 source files + META-INF/services registration).

- `org.os890.jawelte.core.impl.extension.DelegatingJUnitExtension` — single TestBeansExtension provider (registered via META-INF/services). Implements the 5 JUnit 6 callbacks via the SPI marker. Per-callback responsibilities:
    - **beforeAll**: read `@EnableTestBeans` (with meta-annotation walk via `AnnotationSupport.findAnnotation`); create `TestContextImpl(testClass)`; bind current `ExtensionContext` as `TestContext` metadata; put `TestContext`, the `manageContainer` flag, and an empty `completedBeforeAll` list in the JUnit Store under namespace `TestContext.class`. Resolve container port + lifecycle ports via `ServiceLoaderCache` (cached at classloader scope). If `manageContainer=true` (default), call `TestBeanContainerPort.beforeAll(testContext)`. Iterate lifecycle ports in ascending `@Priority` order; each successful `beforeAll` is appended to the completed list (built incrementally so a partial-failure cleanup in afterAll only runs the ports that actually started).
    - **postProcessTestInstance**: refresh ExtensionContext metadata, delegate to container port.
    - **beforeEach**: refresh metadata; init empty `completedBeforeEach` list; call `containerPort.beforeEach`; iterate lifecycle ports ascending, append to completed.
    - **afterEach**: refresh metadata; iterate `completedBeforeEach` in REVERSE (LIFO) calling `afterEach` on each, collecting throwables; then call `containerPort.afterEach` (unconditional cleanup guarantee), collecting its throwable; aggregate via `rethrowAggregated` (first throwable becomes primary, others attached via `addSuppressed`). `containerPort.afterEach` is invoked even when every module port threw.
    - **afterAll**: same pattern as afterEach but iterates `completedBeforeAll`, and `containerPort.afterAll` runs only when `manageContainer=true` (per the contract: when manageContainer=false, the user owns container lifecycle).
- `org.os890.jawelte.core.impl.context.TestContextImpl` — default TestContext. HashMap-backed metadata store; `bindMetadata` requires non-null key + value; `getMetadata` is null-safe via `key.cast(value)` (safe by construction since `bindMetadata` stores the same type).
- `org.os890.jawelte.core.impl.loader.ServiceLoaderCache` — utility class (private constructor). Two volatile static caches with double-checked locking on `ServiceLoaderCache.class`. `resolveContainerPort` requires exactly one impl (zero / multiple → `IllegalStateException` with the exact messages from TICKET-001's SPI section). `resolveLifecyclePorts` accepts 0..N impls and returns an unmodifiable, priority-sorted list.
- `org.os890.jawelte.core.impl.loader.PriorityComparator` — orders by `jakarta.annotation.Priority` value ascending; missing annotation → `Integer.MAX_VALUE` (sort last).
- `META-INF/services/org.os890.jawelte.core.api.port.TestBeansExtension` — registers `DelegatingJUnitExtension` as the single provider for `EnableTestBeans.Proxy` to discover via ServiceLoader.

Apache 2.0 header on every file. Full Javadoc on every public type / member. No `final` on classes or methods. 4-space indent.

Verification: `./mvnw compile -am` builds the full reactor (4 modules) in 0.6s; release=25; enforcer rules pass on each.

Behaviour tests for the orchestration contracts arrive in `tests/core/scenario-*` per task #8 (12 in-scope orchestration scenarios).


## 2026-05-05 — TICKET-001 task #8: 14 scenario test sub-modules

Built the complete tests/ tree per TICKET-001's in-scope orchestration scenarios PLUS the two implicit Proxy-level SPI scenarios required by the Acceptance Criteria. 14 scenario sub-modules; ~70 source files; one big commit per the user's chosen rollout.

Top-level structure:
- `tests/pom.xml` (jawelte-tests, packaging=pom): aggregator only.
- `tests/core/pom.xml` (jawelte-tests-core, packaging=pom): aggregator AND parent for the 14 scenario modules. Centralizes shared test deps (jawelte-core-api, junit-jupiter, junit-platform-testkit, AssertJ, jakarta.* APIs). `jawelte-core-impl` is intentionally NOT in the parent's `<dependencies>` so `scenario-proxy-no-provider` can run without it on its classpath; the 13 scenarios that do need it declare it explicitly in their own pom.
- Root `pom.xml` updated to include `<module>tests</module>`.

Scenario inventory and what each verifies:

| # | Scenario | What it verifies |
|---|---|---|
| 01 | proxy-resolves-delegating-extension | Happy path. EngineTestKit launches a subject; the recording fake's events show beforeAll → postProcessTestInstance → beforeEach → afterEach → afterAll exactly. |
| 02 | priority-ordering-before | 3 module ports @Priority(50/100/200). beforeEach fires 50 → 100 → 200 (ascending). |
| 03 | priority-ordering-after-lifo | Same 3 ports. afterEach fires 200 → 100 → 50 (LIFO). |
| 04 | no-priority-last | Prioritized port (50) and unprioritized port. beforeEach fires prioritized → unprioritized (no-@Priority sorts last). |
| 05 | testcontext-binding | All 5 callbacks receive the same TestContext.getTestClass() == Subject.class. |
| 10 | zero-container-managers | No TestBeanContainerPort registered. EngineTestKit reports failure with `IllegalStateException("No TestBeanContainerPort found via ServiceLoader…")` (TICKET-001 SPI section verbatim). |
| 11 | multiple-container-managers | Two TestBeanContainerPort impls registered. Failure with `IllegalStateException("Multiple TestBeanContainerPort implementations found:…")`. |
| 15 | error-propagation | AlphaPort @Priority(50) completes beforeEach; BetaPort @Priority(100) throws beforeEach. After test failure: alpha's afterEach IS called, beta's is NOT, original exception propagates. |
| 19 | afterall-throws-fresh-testcontext | Container port's afterAll throws on first subject. Second subject runs and gets a NEW TestContext instance — JUnit Store entry is disposed cleanly. |
| 20 | multiple-aftereach-failures | Three module ports' afterEach all throw (50/100/200). LIFO order means primary = priority-200's exception, suppressed in iteration order = [priority-100, priority-50]. |
| 21 | test-fail-and-aftereach-fail | Test method fails AssertionError; container.afterEach also throws. AssertionError is primary; afterEach exception is in `getSuppressed()` (JUnit cross-extension aggregation). |
| 22 | beforeall-throws-afterall-runs | Container's beforeAll throws. Module port's beforeAll is NEVER called; container's afterAll IS called (cleanup-guarantee for partial state). |
| proxy-no-provider | core-impl deliberately absent from classpath. EnableTestBeans.Proxy throws `IllegalStateException("No TestBeansExtension found via ServiceLoader…")`. |
| proxy-multi-provider | core-impl on classpath PLUS a SecondTestBeansExtension fake registered via test-only META-INF/services. Proxy throws `IllegalStateException("Multiple TestBeansExtension implementations found:…")`. |

Per-scenario layout:
- `pom.xml` — minimal; inherits from `jawelte-tests-core`. 13 scenarios declare `jawelte-core-impl` (test scope); `scenario-proxy-no-provider` does not.
- `src/test/java/<scenario-package>/` — Subject.java (`@EnableTestBeans` + `@Test`), TestClass.java (Surefire-discovered, uses EngineTestKit + AssertJ), one or more fake ports.
- `src/test/resources/META-INF/services/...` — scenario-tailored ServiceLoader registrations (test scope only; no main jar pollution).
- Apache 2.0 license header on every Java file. No `final` on classes or methods. 4-space indent.

Test approach: every scenario uses `org.junit.platform.testkit.engine.EngineTestKit` to launch the Subject inside the running JVM, then asserts on the recorded events / failures. The Subject classes use `*Subject` naming so Surefire's default `*Test` pattern skips them — only the Test classes run as direct Surefire tests.

Verification: `./mvnw test` succeeds. 16-module reactor (parent + core aggregator + core/api + core/impl + tests aggregator + tests/core aggregator + 14 scenarios) builds and runs all scenarios in 4.7s. **Every TICKET-001 in-scope orchestration scenario plus both Proxy-level SPI scenarios passes.**

All NFR contracts verified end-to-end through real JUnit lifecycle: ServiceLoader resolution + caching, `@Priority` ascending+LIFO ordering, error propagation, completed-only cleanup, exception aggregation with first-primary + others-suppressed, container-port cleanup guarantees on partial-state failures, fresh-TestContext-per-class.


## 2026-05-05 — TICKET-001 task #9: quality gates wired

Activated all the cross-cutting quality gates from TICKET-001's NFR section, plus the JaCoCo coverage aggregator.

- **Checkstyle**: new `checkstyle.xml` at the project root. Google-style ruleset with project-specific overrides:
    - 4-space indent (override Google's 2)
    - 120-char line length (override Google's 100)
    - AnnotationLocation rule disabled (Jakarta-style flexibility, per the user's earlier choice)
    - Custom RegexpSingleline rules to ban `final class` and `final ReturnType methodName(` declarations (CDI proxy compatibility — `final` on classes or methods blocks `RequestScoped`/`ApplicationScoped` proxying)
    - VisibilityModifier requires private instance fields (with `allowPublicFinalFields=true` for static-final containers used by tests)
    - Standard Google rules retained: AvoidStarImport, ModifierOrder, NeedBraces, ImportOrder (with static-imports-at-top), TypeName / MemberName / MethodName / ConstantName, EmptyBlock, OneStatementPerLine, etc.
- Activated the **maven-checkstyle-plugin** in the parent's `<build><plugins>` at the `validate` phase. Fixed 28 violations across the new code (most were ConstantName for the test fakes' `static final` containers — `events`/`entries`/`testClassesSeen` etc. renamed to `EVENTS`/`ENTRIES`/`TEST_CLASSES_SEEN` etc.; ImportOrder option flipped from `bottom` to `top` to match the static-import-first style; one `final class` removed from each of the four `RecordedEvents` test holders; `Proxy` constructor's redundant `public` modifier removed; one underscored test method renamed). All references updated together via a perl one-liner across the affected files.
- Activated the **jacoco-maven-plugin** in the parent's `<build><plugins>` (prepare-agent at `initialize`, report at `verify`). Each module produces its own `target/jacoco.exec` plus per-module HTML/XML report.
- Activated the **maven-javadoc-plugin** (`jar` goal at `verify`) on `core/api` and `core/impl` only — the JAR-publishing modules. The plugin runs with `failOnWarnings=true` and `doclint=all`. Fixed one warning by adding an explicit Javadoc'd no-arg constructor to `PriorityComparator` (javac otherwise flagged the synthesized default constructor as "use of default constructor, which does not provide a comment").
- New **`coverage/`** module (`jawelte-coverage`, packaging=pom): runs `jacoco:report-aggregate` at `verify`. Lists `core/api`, `core/impl`, and all 14 scenario modules as `<dependencies>` so JaCoCo collects every sibling's `jacoco.exec` and produces a project-wide aggregated report at `coverage/target/site/jacoco-aggregate/{index.html,jacoco.xml,jacoco.csv}`. Added `<module>coverage</module>` to the root pom.

Coverage results on production code (from the aggregated CSV):
- jawelte-core-api: 100% on `EnableTestBeans.Proxy`, `TestModuleLifecyclePort` defaults; **0%** on the 3 CDI event classes (`ContainerStarted`, `BeforeScopeStarted`, `AfterTestTransaction`) — these are CDI contract classes (constructor + getters) that are *fired by adapter implementations*; TICKET-001 has no adapter in scope, so they get covered when the cdi-module / scope-module / jpa-module tickets land. This is intentional and aligned with the scope-split note in the local ticket file.
- jawelte-core-impl: 100% on `ServiceLoaderCache` and `PriorityComparator`; ~94% on `DelegatingJUnitExtension` (gaps are unreachable defensive branches in `rethrowAggregated`); ~58% on `TestContextImpl` (gaps are `unbindMetadata` and parts of `getMetadata` not exercised by the in-scope scenarios).
- Overall (computed from the CSV INSTRUCTION counts): ~87% line / ~77% branch on production code, both above the 80%/70% project thresholds the user picked.

**Project-wide threshold ENFORCEMENT (jacoco:check at the aggregate level)** is NOT wired yet — explained as a comment in `coverage/pom.xml`. The stock JaCoCo Maven plugin's `check` goal binds to the local module's class files; the coverage aggregator has none, so check skips. The clean fix is `jacoco:merge` + `jacoco:check` against a leaf module, but that requires a build-order trick that is best paired with the canonical run-scripts (per the `feedback_testing_is_foundational` rule). Deferred to a follow-up ticket alongside the OWB / Weld / Quarkus profile matrix and the run-scripts.

Verification: `./mvnw verify` succeeds end-to-end on the 21-module reactor in ~7.7s, with all of Enforcer (Java 25, Maven 3.9, banned `javax.*`, dependency convergence), Checkstyle (project ruleset), Apache RAT (Apache 2.0 headers on every Java/XML file; the agreed-on excludes), JaCoCo (per-module + aggregated), and Javadoc (strict) gates green.


## 2026-05-06 — TICKET-001 PR follow-ups

- Renamed `coverage/` Maven module to `jawelte-coverage/` to avoid the `.gitignore` collision (the `coverage` pattern there was meant for runtime artifacts but silently swallowed the whole module so the directory never reached origin). Root pom `<modules>` updated. The artifactId was already `jawelte-coverage`, so only the directory rename and the `<module>` reference changed.


## 2026-05-06 — META-INF/services license headers + drop RAT exclusion

Added Apache 2.0 license headers (as `#` comments per `ServiceLoader`'s spec) to every `META-INF/services` file in the project (1 production registration in `core/impl` + 18 across the 14 scenario test modules). Then dropped `<exclude>**/META-INF/services/**</exclude>` from the parent pom's RAT config so these files participate in the license-header check.

Why this matters: the previous exclusion silently let any future services registration ship without an attribution header. Per the project's quality NFRs, every distributable file should carry the license. RAT recognizes the canonical Apache 2.0 wording with `#`-prefix comment style.

`./mvnw verify` passes. RAT reports 0 unapproved across every module.


## 2026-05-06 — dependencyManagement default scopes

Refactored the parent pom's `<dependencyManagement>` to predefine sensible default scopes for every project dependency, so child poms only have to specify scope when overriding.

Default scopes:
- jakarta.enterprise.cdi-api / jakarta.annotation-api / jakarta.inject-api → `provided` (the CDI runtime supplies these; consumers bring their own runtime)
- junit-jupiter-api → `provided` (jawelte's user-facing classes implement JUnit callbacks but consumers bring their own JUnit version)
- junit-jupiter / junit-platform-testkit / assertj-core → `test`
- junit-bom → `import` (unchanged; BOM)
- jawelte-core-api / jawelte-core-impl → compile (default; no scope set)

Simplified child poms:
- `core/api`: dropped explicit `<scope>provided</scope>` from junit-jupiter-api + the two jakarta deps (now inherited).
- `core/impl`: same — dropped `<scope>provided</scope>` from the same three deps.
- `tests/core`: dropped `<scope>test</scope>` from junit-jupiter, junit-platform-testkit, assertj-core (test is now the default for these). Kept explicit `<scope>test</scope>` on jawelte-core-api and the three jakarta deps because tests/core overrides their compile / provided defaults.

Verified: `./mvnw verify` passes on the full reactor; all 14 scenarios still pass; aggregated coverage report still produced.


## 2026-05-06 — Module rename jawelte-coverage -> coverage-report; branch + PR cleanup

Pointed out by os890 during PR review: module name should match its purpose; "coverage-report" is more descriptive than "jawelte-coverage" and avoids the awkwardness of carrying the project prefix in the folder name. The previous "jawelte-coverage" was a workaround for the `.gitignore` collision with the bare "coverage" rule; "coverage-report" is a different name and isn't matched by that rule.

Changes:
- Renamed `jawelte-coverage/` -> `coverage-report/`. artifactId changed from `jawelte-coverage` to `coverage-report` (deviates from the `jawelte-` prefix convention; reads more naturally). `<name>` and `<description>` updated.
- Root pom `<modules>` and the threshold-comment-property reference updated.
- Local workflow guide and checklist updated to reflect the convention nuance: module DIRECTORIES are named to escape `.gitignore` collisions; artifactIds typically carry the `jawelte-` prefix but the project allows direct names like `coverage-report` where the directory name is already collision-free.
- PR #2 closed (branch is also being renamed to match the renamed issue title; the PR will be re-opened on the renamed branch when ready).
- Branch renamed `1-ticket-001-core-junit-adapter-and-spi-foundation` -> `1-core-junit-adapter-and-spi-foundation` to match the issue title (which had the `TICKET-001:` prefix removed in the previous round).

Verified: `./mvnw verify` is green; `coverage-report/target/site/jacoco-aggregate/` continues to be produced; the 14 scenarios continue to pass.


## 2026-05-06 — TICKET-002 implementation: @ConfigBean + ConfigResolver port + ConfigResolverAdapter

Implemented the configuration-support layer of jawelte per TICKET-002's in-scope subset (7 of 10 scenarios; 3 deferred to a future cdi-module ticket).

- **`org.os890.jawelte.core.api.ConfigBean`** — CDI stereotype that meta-applies `@ApplicationScoped`. Pure scope marker; no interceptors. Discovery marker for `limitToTestBeans` logic later.
- **`org.os890.jawelte.core.api.port.ConfigResolver`** — single-method SPI port: `Optional<String> resolve(String dotKey)`. Lives next to the other port contracts; `null` key throws `NullPointerException`.
- **`org.os890.jawelte.core.impl.config.ConfigResolverAdapter`** — `@ApplicationScoped` adapter implementing `ConfigResolver` via MicroProfile Config. Renamed from `DefaultConfigResolver` per os890's request — "Adapter" is more accurate ports-and-adapters terminology.
- **Caching design**: hybrid `@PostConstruct` + lazy-init. The `Config` reference is populated once and reused. `@PostConstruct init()` fires when the bean is CDI-managed; `cachedConfig()` lazily initializes the same field on the first `resolve()` call when no `@PostConstruct` ran (covers unit tests that construct the adapter directly without a CDI container). Either path is a no-op once the field is set.
- **Resolution algorithm**: dot-key first; if empty AND the key contains at least one `.`, retry with `_` substituted for `.`; otherwise return `Optional.empty()`. Dot-key precedence is preserved when both variants are set.

Maven changes:
- Root pom adds `microprofile.config.version=3.1` and `smallrye.config.version=3.10.0` properties; both pinned in `<dependencyManagement>` with appropriate default scopes (`microprofile-config-api` = `provided`, `smallrye-config` = `test`).
- `core/impl/pom.xml` adds `microprofile-config-api` (inherits `provided`).
- 7 new test sub-modules under `tests/core/scenario-config-NN-*/`. Each pom declares `jawelte-core-impl` (test), `microprofile-config-api` (test), `smallrye-config` (default test from dependencyManagement). Added all 7 to `tests/core/pom.xml` `<modules>` and to `coverage-report/pom.xml` `<dependencies>` (so report-aggregate picks up their exec data).

Test architecture:
- Simple direct JUnit `@Test` calling `new ConfigResolverAdapter()` and asserting via AssertJ. No EngineTestKit, no Subject classes, no Fakes — TICKET-001's machinery was needed to verify JUnit lifecycle dispatch; TICKET-002's tests verify pure resolver behavior, so the simpler shape applies.
- Per-scenario `src/test/resources/META-INF/microprofile-config.properties` carries the keys/values for that scenario (with the standard Apache 2.0 header as `# `-prefixed comment lines). SmallRye Config picks them up automatically. Scenarios where no key is set ship without a properties file (3, 6, 7).

Scope split between TICKET-002 and the future cdi-module ticket:
- **In TICKET-002 (7 scenarios; pure resolver lookup behavior, no CDI runtime needed):** dot-key resolves, underscore fallback, neither key set, both keys (dot wins), key without dot (no fallback), key without dot (not set), null key NPE.
- **Postponed to cdi-module (3 scenarios; require a real CDI runtime):** `@ConfigBean` stereotype auto-scoping, `@Inject ConfigResolver`, `@Alternative @Priority` override.
- Local ticket file keeps all 10 scenarios with `[postponed to cdi-module]` tags inline; the GitHub issue body lists only the 7 in-scope scenarios.
- Acceptance Criteria rewritten: declaration-side criteria (annotations / interfaces / class declarations) covered by TICKET-002; CDI-runtime-verification criteria explicitly tagged as postponed.

Verification: `./mvnw verify` passes the now-22-module reactor in ~9s; all 14 TICKET-001 scenarios still pass; all 7 new config scenarios pass; aggregated coverage report includes the new modules.


## 2026-05-06 — TICKET-003 Phase 1: TestContext addenda (TestContext.get + ServicePriorityResolver + loadService)

Implemented the three TICKET-001 addenda from TICKET-003 as a foundational, purely-additive layer. Existing TICKET-001/002 scenarios untouched (still pass 21/21).

- **`TestContext` interface (`core/api/port`)** — added two abstract SPI methods (`getCurrent()`, `reset()`) and two static method bodies:
    - `static TestContext get()` — uncached MP-Config-based bootstrap that reads the FQCN of the accessor impl from the key `org.os890.jawelte.core.api.port.TestContext`, dot-then-underscore fallback, reflective `newInstance()`, then delegates to `getCurrent()` on the resulting accessor. Throws `IllegalStateException` when no `TestContext` is active on the calling thread.
    - `static <T> T loadService(Class<T> targetType)` — single canonical SPI lookup. Two cases:
        1. `targetType == ServicePriorityResolver`: read MP Config key whose name is `ServicePriorityResolver`'s own FQCN, `Class.forName`, try `CDI.current().select(configuredClass).get()`, fall back to reflective `newInstance()` (uncached) when CDI is not up.
        2. any other target: route through case 1 to obtain the resolver, then `ServiceLoader.load(targetType)` + `resolver.resolve(...)`.
- **`ServicePriorityResolver` port (NEW, `core/api/port`)** — `<T> List<T> sort(List<T>)` + default `<T> T resolve(List<T>)` (head of sort). Documents the project-wide ordering rule.
- **`DefaultServicePriorityResolver` (NEW, `core/impl/spi`)** — `@ApplicationScoped`. Sorts by `@Priority` ascending; missing `@Priority` is treated as `Integer.MAX_VALUE` (sort last); ties broken by full class name ascending so the order is stable, deterministic, and independent of classpath enumeration.
- **`TestContextImpl` (`core/impl/context`)** — refactored to play two roles. The new public no-arg constructor produces an "accessor" instance for `TestContext.get()`'s reflective bootstrap (per-test methods throw `IllegalStateException` on accessor instances). The existing `(Class<?>)` constructor produces a "per-test" instance that self-registers on a class-level static `ThreadLocal<TestContextImpl>` so `TestContext.get()` returns it from any caller on the same thread. `reset()` clears the `ThreadLocal` slot; calling on accessor instance is a no-op.
- **`DelegatingJUnitExtension.beforeAll`** — wrapped the post-construction logic (containerPort.beforeAll + module-port iteration) in a try/finally with `testContext.reset()` in finally, per the addendum's lifecycle contract: ThreadLocal is cleared even if a port throws or a `ContainerStarted` listener throws. After `beforeAll` returns, `TestContext.get()` throws — bootstrap-only.
- **Bootstrap config files in `core/impl`**:
    - `META-INF/microprofile-config.properties`: defaults the two FQCN-keyed bootstraps (`...port.TestContext` → `TestContextImpl`; `...port.ServicePriorityResolver` → `DefaultServicePriorityResolver`).
    - `META-INF/beans.xml` with `bean-discovery-mode="annotated"` so CDI runtimes pick up `DefaultServicePriorityResolver` automatically when the cdi-module ticket lands.
- **`core/api/pom.xml`** — added `microprofile-config-api` (provided, inherited from parent dependencyManagement) so `TestContext`'s static method bodies can compile against `ConfigProvider` / `Config`.

`ServiceLoaderCache` was NOT refactored to use `TestContext.loadService` in this phase — the unification would have required adding MP Config impl (SmallRye) to every existing TICKET-001 scenario module's classpath, which is a much wider change than the addenda need. Defer to a later phase or follow-up ticket; the new mechanism is in place for cdi-module's CDI Extension to use.

Verification: `./mvnw verify` passes the full 22-module reactor; all 21 prior scenarios still pass (14 TICKET-001 + 7 TICKET-002).

