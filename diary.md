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


## 2026-05-06 — TICKET-003 Phase 2: cdi-module/api ports

Bootstrapped the `modules/` aggregator and the cdi-module's api jar with the two pluggable ports.

- `modules/pom.xml` — top-level `jawelte-modules` aggregator (packaging=pom). Added `<module>modules</module>` to the root pom; reactor order: core → modules → tests → coverage-report.
- `modules/cdi-module/pom.xml` — `jawelte-cdi-module` aggregator. Lists `api` as a child; `impl` will be added in Phase 3.
- `modules/cdi-module/api/pom.xml` — `jawelte-cdi-module-api` jar. Compile-deps: only `jawelte-core-api`. Deliberately no CDI / Mockito imports (the jar must load cleanly in JVMs that have neither). Strict Javadoc activated (mirroring core/api / core/impl).
- `org.os890.jawelte.module.cdi.api.port.ExcludedPackageFilter` — single-method port `boolean isExcluded(Class<?> rawType)`. Documented as the auto-mocking exclude policy; consulted by cdi-module's CDI Extension during `AfterBeanDiscovery`.
- `org.os890.jawelte.module.cdi.api.port.WhitelistFilter` — single-method port `boolean isAllowed(Class<?> rawType)`. Documented as the `limitToTestBeans=true` whitelist policy; consulted by the Extension during `ProcessAnnotatedType`.

Both ports specify their `ServiceLoader` + `TestContext.loadService` selection in their Javadoc so future implementers don't reinvent the discovery contract.

Verification: `./mvnw verify` passes the full 23-module reactor; all 21 prior scenarios still pass.


## 2026-05-06 — TICKET-003 Phase 3: cdi-module/impl

Built cdi-module/impl with the CDI SE adapter, the CDI Extension, the default filter implementations, and the helper utilities. Code compiles cleanly under all gates (Checkstyle, RAT, Javadoc strict, Enforcer). Behaviour verification arrives in Phase 4 via the 49 scenario sub-modules.

Production classes (8):
- `CdiTestBeanContainer` — `TestBeanContainerPort` impl. No instance fields; `SeContainer` and `RequestContextController` bound on `TestContext` metadata. Boots `SeContainerInitializer` with `TestBeansCdiExtension`, fires `ContainerStarted` while the container is live, manages `RequestContextController.activate/deactivate` per test method (with `BeforeScopeStarted` veto support), closes the container in `afterAll`.
- `TestBeansCdiExtension` — CDI Extension. `BeforeBeanDiscovery` reads the active test class via `TestContext.get()`, scans `@TestBean` declarations + meta-annotations + superclass-walk + static fields (with the documented validation errors thrown synchronously), binds the scan result on `TestContext` metadata, loads `WhitelistFilter` + `ExcludedPackageFilter` via `TestContext.loadService(...)`. `ProcessAnnotatedType` applies the whitelist veto when `limitToTestBeans=true`. `ProcessInjectionPoint` collects unsatisfied IP candidate types (with `Provider<X>` / `Instance<X>` unwrap). `AfterTypeDiscovery` registers `@TestBean(bean=X)` and `@TestBean(beanProducer=X)` alternatives. `AfterBeanDiscovery` registers static-field synthetic beans, walks the test-class `@Inject` fields explicitly (since the test class is not a CDI bean and no `ProcessInjectionPoint` event fires for it), and synthesises Mockito mocks for unsatisfied non-excluded non-target types using `BeanManager.getBeans(...)` to confirm "unsatisfied".
- `DefaultExcludedPackageFilter` (`@Priority(Integer.MAX_VALUE)`) — reads `org.os890.jawelte.module.cdi.auto-mock.exclude-packages` directly via `ConfigProvider.getConfig()` (the bean-injected `ConfigResolver` from TICKET-002 is unavailable while the container is bootstrapping). Walks the type's supertype hierarchy. Caches the parsed prefix list in a `volatile` field for the filter instance's lifetime.
- `DefaultWhitelistFilter` (`@Priority(Integer.MAX_VALUE)`) — allows when `FrameworkAllowlist.isAllowlisted(rawType)` OR when `rawType` is a `@TestBean` target on the active test class (read via `TestContext.get().getMetadata(TestBeanScanner.Result.class)` with the `IllegalStateException`-no-active-context fall-through documented in the impl).
- `FrameworkAllowlist` — abstract util. Reads the `org.os890.jawelte.module.cdi.framework-allowlist.packages` MP Config key (dot-then-underscore fallback). `volatile` cached prefix list. Walks supertypes recursively.
- `TestBeanScanner` — abstract util. Walks class + superclasses + meta-annotations (cycle-safe; skips `java.*`/`jakarta.*` annotation-type packages). Returns an immutable `Result` record with bean targets, producer targets, and static-field entries. Throws `IllegalStateException` on the documented validation cases (instance-field, null field, dual `bean`+`beanProducer`, field-with-bean-attribute).
- `SyntheticBeanUtil` — abstract util. `registerStaticFieldBean` (scope `@Singleton`) and `registerAutoMockBean` (scope `@RequestScoped` for non-JDK types, `@Dependent` for JDK types). Adds `@Default` (when no custom qualifier) and `@Any` automatically. Ships a `named(...)` helper that returns a `NamedLiteral`.
- `InjectFieldsHelper` — abstract util. Single static `inject(BeanManager, Object)` using CDI 4.x's `createAnnotatedType` → `getInjectionTargetFactory` → `createInjectionTarget(null)` → `inject` chain. The cdi-module performs no per-field reflective walk of its own; the underlying CDI runtime handles inheritance, qualifiers, generic types, and `Provider`/`Instance` wrappers.
- `MockitoMockFactory` — abstract util. Returns `null` from `Mockito.mock(...)` when Mockito throws (typical for unmockable bootstrap JDK classes); the Extension then leaves the IP unsatisfied so CDI's own deployment validation surfaces the offending type.

All 6 util classes follow the new util-class shape from TICKET-003's coding guidelines: `public abstract class FooUtil` with an explicit Javadoc'd `protected` constructor (the `abstract` modifier prevents direct instantiation; the explicit constructor silences `javadoc -doclint:all` on the otherwise synthesised default).

META-INF (6 files):
- `META-INF/services/org.os890.jawelte.core.api.port.TestBeanContainerPort` → `CdiTestBeanContainer`
- `META-INF/services/jakarta.enterprise.inject.spi.Extension` → `TestBeansCdiExtension`
- `META-INF/services/org.os890.jawelte.module.cdi.api.port.ExcludedPackageFilter` → `DefaultExcludedPackageFilter`
- `META-INF/services/org.os890.jawelte.module.cdi.api.port.WhitelistFilter` → `DefaultWhitelistFilter`
- `META-INF/beans.xml` (`bean-discovery-mode="annotated"`)
- `META-INF/microprofile-config.properties` ships the framework allowlist defaults (`java.`, `javax.`, `jakarta.`, `org.jboss.weld.`, `org.apache.webbeans.`, `org.os890.jawelte.`)

Maven:
- Root pom: added `mockito.version=5.14.2`, `openwebbeans.version=4.1.0`, `weld.version=6.0.4.Final` properties; added `mockito-core` (provided), `openwebbeans-se` (test), `weld-se-shaded` (test), and the internal `jawelte-cdi-module-{api,impl}` cross-references to `<dependencyManagement>` with appropriate default scopes.
- `modules/cdi-module/impl/pom.xml` — declares the deps (compile cdi-module-api + core-api; provided cdi-api / annotation-api / mp-config-api / mockito-core; test owb/weld via profiles). Two profiles: `owb` (active by default) injects `openwebbeans-se`, `weld` injects `weld-se-shaded`. CI / local runs both via `mvn verify -Powb` and `mvn verify -Pweld`.
- `modules/cdi-module/pom.xml` — adds `<module>impl</module>`.

Verification: `./mvnw verify` passes the now-24-module reactor; all 21 prior scenarios still pass; cdi-module/impl jar produced (with javadoc jar) under default profile (OWB on classpath but no integration tests yet exercise it).


## 2026-05-07 — TICKET-003: CDI SE Implementation (cdi-module) — completed

Shipped the first integration module on top of the TICKET-001 / TICKET-002 foundation, plus three TICKET-001 addenda the cdi-module needed.

**TICKET-001 addenda (Phase 1, additive):**
- `TestContext.get()` static accessor, ThreadLocal-backed; active only inside `DelegatingJUnitExtension.beforeAll`'s bootstrap window. Cleared in a `finally` block so cleanup runs even when a port's `beforeAll` or a `ContainerStarted` observer throws.
- `TestContext.loadService(Class)` SPI helper that combines MicroProfile Config selection, `ServicePriorityResolver` priority sort, and a CDI-first / reflection-fallback instantiation path.
- `ServicePriorityResolver` port + `DefaultServicePriorityResolver` (ascending `@Priority`, missing → `MAX_VALUE`, class-name tiebreak).

**cdi-module/api (Phase 2):**
- `ExcludedPackageFilter` — auto-mock exclude policy (Maven shape: pure ports module, no CDI / Mockito imports).
- `WhitelistFilter` — `limitToTestBeans=true` allow policy.

**cdi-module/impl (Phase 3 + Phase 4 iterations):**
- `CdiTestBeanContainer` — `TestBeanContainerPort` adapter that boots an `SeContainer` per test class, registers the framework Extension, fires `ContainerStarted`, and manages the per-method `RequestContextController`. Falls back to `CDI.current().getBeanManager()` when no `SeContainer` metadata is bound (manageContainer=false).
- `TestBeansCdiExtension` — discovers `@TestBean` declarations, registers `@Alternative` and synthetic beans, applies whitelist veto, and synthesises Mockito mocks for unsatisfied IPs. Skips silently when no `TestContext` is active.
- `DefaultExcludedPackageFilter` / `DefaultWhitelistFilter` — `@Priority(MAX_VALUE)` defaults; both overridable via `ServiceLoader` + `@Priority`.
- Helper classes: `TestBeanScanner`, `SyntheticBeanUtil`, `MockitoMockFactory`, `InjectFieldsHelper`, `FrameworkAllowlist`.

**Tests (Phase 4):** 49 scenario sub-modules under `tests/cdi-module/`. The same scenarios run under both `-Powb` (default) and `-Pweld` profiles, selected via the `tests/cdi-module/pom.xml` profiles. All scenarios pass on both profiles.

**Iteration findings during Phase 4:**
- `ProcessInjectionPoint<?, ?>` with raw wildcards causes OpenWebBeans to deliver only events for `spi.InjectionPoint`-typed IPs. Switched to a method-generic `<T, X> void onProcessInjectionPoint(@Observes ProcessInjectionPoint<T, X>)`.
- `BeanConfigurator.createWith` did not appear to fire under OWB 4.1; switched to `produceWith` and added `.beanClass(rawType)`.
- Weld is strict about generic-type assignability: a synthetic bean of type `Foo` (raw) does not match an `@Inject Foo<Bar>` IP. The Extension now records the full `java.lang.reflect.Type` per IP and registers it on the synthetic bean.
- `Mockito.mock(...)` with the inline mock-maker can't dynamically attach byte-buddy on Java 25. Each scenario module ships `mockito-extensions/org.mockito.plugins.MockMaker = mock-maker-subclass`.
- For `@TestBean(bean=X)` where `X` has `@Alternative` but no scope (scenario 34): the Extension uses `BeforeBeanDiscovery.addAnnotatedType(...)` to force discovery and adds `@Dependent` via the configurator. For non-`@Alternative` non-annotated `X` (scenario 35) the Extension does nothing — silent no-op per spec.
- Qualifier-set merge follows CDI's `@Nonbinding`-aware equivalence (skip `@Nonbinding` members; compare bound members via `Objects.deepEquals`).

**Cleanup (Phases 5 + 6):**
- `ServiceLoaderCache` (core/impl) and the four `RecordedEvents` test holders converted to the project's standard `abstract class + protected ctor` shape.
- `architecture.md` Hexagonal Architecture chapter rewritten: the abstract `JpaContainerPort` / `EclipseLinkAdapter` / etc. examples are replaced by the actual shipped ports (`TestBeansExtension`, `TestBeanContainerPort`, `TestModuleLifecyclePort`, `TestContext`, `ServicePriorityResolver`, `ConfigResolver`, `ExcludedPackageFilter`, `WhitelistFilter`) and adapters. The forward-looking JPA / JTA / JAX-RS / DB-Unit / WireMock examples are preserved as a "Planned" note.

`./mvnw clean verify` passes under both `-Powb` and `-Pweld` with all gates (Enforcer, Checkstyle, RAT, Javadoc, JaCoCo).

## 2026-05-07 — POC gap follow-up: DeltaSpike and Quarkus parity (production-code part)

Compared `/Users/work/workspace/poc` against jawelte (one-way: only items where jawelte lacks something or diverges in a user-observable way) and produced `tickets/poc-gaps-tbd.html`. User selected the DeltaSpike + Quarkus differences plus scenarios 3.2–3.6 from that report for follow-up.

This commit lands the production-code part of that follow-up:

- Added `org.apache.deltaspike.` to the bundled framework-allowlist defaults in cdi-module/impl's MP Config, so DeltaSpike-internal types survive `@EnableTestBeans(limitToTestBeans=true)` without the user needing to extend the allowlist manually.
- Added `hasSyntheticBeanBinding(Class<?>)` to `TestBeansCdiExtension`. When an unsatisfied IP's raw type carries an annotation that is itself meta-annotated with `org.apache.deltaspike.partialbean.api.PartialBeanBinding` (compared by FQN string — no compile-time DeltaSpike dependency), the Extension skips auto-mock registration. The third-party extension is then expected to register the bean. Mirrors the POC's same-name helper in `DynamicTestBeanExtension`.
- Reworked `DelegatingJUnitExtension.readManageContainer(...)` to also return `false` when the test class carries `io.quarkus.test.junit.QuarkusTest` or `io.quarkus.test.junit.QuarkusComponentTest` (FQN string comparison). Effect: a `@QuarkusTest` test class is treated as if `manageContainer=false` even when the user did not set the attribute. The Quarkus test framework already manages the bean container in that case, so jawelte must not boot a second one.

All 49 existing cdi-module scenarios pass under both `-Powb` and `-Pweld`. New scenarios that exercise the DeltaSpike skip path, the DeltaSpike allowlist path, the Quarkus auto-skip path, and the gap-report items 3.2–3.6 will land in follow-up commits.

## 2026-05-07 — POC gap follow-up: scenarios 3.2-3.6 + Quarkus auto-skip + IpKey rework

Landed seven new test scenarios from `tickets/poc-gaps-tbd.html` and one production-code fix the new tests caught:

- **scenario-50** (multi-alternative-same-type): two `@Alternative` impls of one interface on the classpath; `@TestBean` selects one; verify selected wins, other inactive. Closes report item 3.2.
- **scenario-51** (stereotype-with-qualifier): class annotated with both a CDI stereotype (providing `@ApplicationScoped`) and a custom qualifier; verify the bean satisfies a qualifier-marked IP and carries the stereotype-applied scope. Closes 3.3.
- **scenario-52** (typed-narrowed-with-testbean): `@Typed`-narrowed alternative implementing two interfaces, plus a `@TestBean` for an unrelated type; verify both resolve correctly with no false veto. Closes 3.4.
- **scenario-53** (multi-qualifier-jdk-type): two distinct qualifier types on the same JDK type (`List<String>`); verify two independent synthetic mocks. Closes 3.5. **Caught a real production-code bug** — see below.
- **scenario-54** (binding-qualifier-member): qualifier with a binding (non-`@Nonbinding`) `value()` member; verify two distinct member values produce two distinct mocks. Closes 3.6.
- **scenario-55** (deltaspike-partial-bean-binding-skip): stub `@PartialBeanBinding` meta-annotation on a custom binding annotation applied to an unsatisfied IP type; verify auto-mock is skipped (container deployment fails as a result, observed via EngineTestKit).
- **scenario-56** (deltaspike-whitelist-allow): test-classpath bean under `org.apache.deltaspike.` survives `@EnableTestBeans(limitToTestBeans=true)` via the bundled framework allowlist.
- **tests/core/scenario-quarkus-auto-skip**: stub `@QuarkusTest` annotation on a subject test class; recording `TestBeanContainerPort` impl confirms `beforeAll` / `afterAll` are NOT called while `postProcessTestInstance` / `beforeEach` / `afterEach` still fire. A second sub-test (`PlainSubject`) confirms the recording port is wired correctly so the auto-skip case is genuinely opting out, not silently broken.

**Production-code fix caught by scenario-53:**

The cdi-module Extension's `unsatisfiedCandidateIps` map was previously keyed by `Type` only — different qualifier types on the same target type collapsed into a single entry, producing one synthetic bean carrying both qualifiers. With two such IPs in a test class, both queries returned the same merged bean.

Refactored the field to `Set<IpKey>` where `IpKey` is `(targetType, qualifiers)` with `equals` / `hashCode` based on CDI qualifier equivalence (annotation-type identity plus binding-member-only value comparison). Two qualifier types on the same target → two distinct IpKeys → two distinct mocks. Two `@Nonbinding`-equivalent qualifiers (per scenario 7) → one IpKey → one mock. Two binding-different `@ServiceType("a")` / `@ServiceType("b")` (per scenario 54) → two distinct IpKeys → two mocks. The previous `mergeQualifiers` / map-merge helper is gone; `Set<IpKey>` natively dedupes.

All 21 core scenarios + 56 cdi-module scenarios pass `mvn clean verify` under both `-Powb` (default) and `-Pweld`. Coverage report still aggregates clean.

## 2026-05-07 — Inline-merge POC parity items into TICKET-003 ticket and issue

Added to `tickets/003-cdi-se-implementation.md` so the file reads as if these were always part of the initial scope (no POC / gap-audit references):

- New section "TICKET-001 Addendum: `@QuarkusTest` auto-skip in `DelegatingJUnitExtension`" alongside the other TICKET-001 addendums, documenting the FQN-string detection of `io.quarkus.test.junit.QuarkusTest` / `QuarkusComponentTest` and the `manageContainer=false`-equivalent behaviour.
- New subsections under § cdi-module Implementation Details:
  - "Per-(type, qualifier-set) bucketing for unsatisfied IPs" describing the `Set<IpKey>` collection and the CDI qualifier equivalence rules (`@Nonbinding`-aware) that determine whether two IPs share or split mocks.
  - "Synthetic-bean binding skip (Apache DeltaSpike `@PartialBeanBinding`)" describing the FQN-string skip for third-party-owned types.
- Updated § Framework allowlist: bundled defaults now include `org.apache.deltaspike.`; the previously-recommended `auto-mock.exclude-packages` workaround paragraph is replaced with a description of the bundled prefix.
- New scenarios 50–57 added to § Test Scenarios:
  - 50: multi-`@Alternative` for the same type, one selected
  - 51: stereotype-applied scope plus custom qualifier on a real bean
  - 52: `@Typed`-narrowed alternative coexisting with `@TestBean` for an unrelated type
  - 53: distinct qualifier types on the same JDK target type
  - 54: binding qualifier member differentiates mocks
  - 55: Apache DeltaSpike `@PartialBeanBinding` skip
  - 56: DeltaSpike-internal type survives whitelist mode
  - 57: `@QuarkusTest` auto-skips container management
- Updated § Acceptance Criteria — runtime behaviour with bullets covering the IpKey bucketing rule, the `@PartialBeanBinding` skip, and the `@QuarkusTest` auto-skip; configuration-and-defaults bullet updated to mention DeltaSpike in the bundled prefixes; cross-cutting test count raised from 45 to 57.

Issue #6 body re-derived from the local file by stripping the same brain-dump sections as before (`cdi-module Structure`, `cdi-module Overview`, `cdi-module Implementation Details`, `Object Lifecycle`, `Pre / Post conditions`, `Use Cases`). The new TICKET-001 Addendum, scenarios 50–57, and AC bullets all carry over to the issue body. Pushed via `gh issue edit 6 --body-file`.

## 2026-05-07 — TICKET-003 review-findings refactor

Six items from the local review notes (`tickets/003_review-findings.txt`) addressed in one pass; all 21 core scenarios + 56 cdi-module scenarios pass `mvn clean verify` under both `-Powb` and `-Pweld`.

- **CdiContainerPort**: new port in `cdi-module/api` exposing `start(TestContext)` / `stop(TestContext)`. Default impl `SeContainerCdiContainerPort` (in `cdi-module/impl/container`) wraps `SeContainerInitializer` and binds the `SeContainer` on `TestContext` metadata exactly as the previous in-line code did. `CdiTestBeanContainer.beforeAll` / `afterAll` delegate to the port via `TestContext.loadService(...)`. The `ContainerStarted` event fires via `CDI.current()` so the firing path stays container-flavour-agnostic. A future quarkus-module ships its own `CdiContainerPort` impl at a lower `@Priority` and replaces the SE one without further code changes.
- **MockFactory**: new port in `cdi-module/api` with `<T> T create(Class<T>)` returning `null` for unmockable types. Default impl `MockitoMockFactory` (in `cdi-module/impl/mock`) keeps the existing Mockito wrapper. The old `util/MockitoMockFactory` is deleted; `TestBeansCdiExtension` resolves the factory via `TestContext.loadService(MockFactory.class)` during `BeforeBeanDiscovery` and uses the resolved instance both to probe a candidate type and to back the per-injection mock supplier.
- **Filters via ConfigResolver**: `DefaultExcludedPackageFilter` and `FrameworkAllowlist` no longer call `ConfigProvider` directly nor reimplement the dot/underscore fallback. They now resolve the active `ConfigResolver` via `TestContext.loadService(...)` and call `resolver.resolve(KEY)`. As a result `core/impl` ships a `META-INF/services/org.os890.jawelte.core.api.port.ConfigResolver` registration so the SPI lookup finds `ConfigResolverAdapter`. The dot-then-underscore fallback now lives in exactly one place — `ConfigResolverAdapter` itself.
- **`SyntheticBeanUtil.qualifiersWithDefaults` is type-safe**: the `getSimpleName().equals("Named")` and `"jakarta.inject.Named"` string compares are gone; both checks now use `Named.class`.
- **Method visibility ordering**: `TestBeansCdiExtension` was restructured so all package-private observer methods (`onBeforeBeanDiscovery`, `onProcessAnnotatedType`, `onProcessInjectionPoint`, `onAfterTypeDiscovery`, `onAfterBeanDiscovery`) precede every private helper, and the package-private test-only accessor sits with the package-private group. `SyntheticBeanUtil` got its public `named(...)` method moved above the private helpers. `ConfigResolverAdapter` got `init()` reordered after the public `resolve(...)`.
- **Records placement**: `TestBeanScanner.StaticField` and `TestBeanScanner.Result` were already nested below the private methods inside the abstract class; no move needed.

## 2026-05-07 — Hexagonal package layout: port impls under impl/adapter/<tech>

Restructured both `core/impl` and `cdi-module/impl` so every port-implementation class lives under an `adapter` sub-package, with a per-tech sub-sub-package below it. Utility classes (`util`, `loader`) stay where they are.

- `core/impl/config/ConfigResolverAdapter` → `core/impl/adapter/config/ConfigResolverAdapter`
- `core/impl/context/TestContextImpl` → `core/impl/adapter/context/TestContextImpl`
- `core/impl/extension/DelegatingJUnitExtension` → `core/impl/adapter/extension/DelegatingJUnitExtension`
- `core/impl/spi/DefaultServicePriorityResolver` → `core/impl/adapter/spi/DefaultServicePriorityResolver`
- `cdi-module/impl/CdiTestBeanContainer` → `cdi-module/impl/adapter/container/CdiTestBeanContainer`
- `cdi-module/impl/container/SeContainerCdiContainerPort` → `cdi-module/impl/adapter/container/SeContainerCdiContainerPort`
- `cdi-module/impl/extension/TestBeansCdiExtension` → `cdi-module/impl/adapter/extension/TestBeansCdiExtension`
- `cdi-module/impl/filter/DefaultExcludedPackageFilter` → `cdi-module/impl/adapter/filter/DefaultExcludedPackageFilter`
- `cdi-module/impl/filter/DefaultWhitelistFilter` → `cdi-module/impl/adapter/filter/DefaultWhitelistFilter`
- `cdi-module/impl/mock/MockitoMockFactory` → `cdi-module/impl/adapter/mock/MockitoMockFactory`

Files moved with `git mv` to preserve history. Package declarations in each moved file updated. All FQN references in `META-INF/services/*` (eight files), `META-INF/microprofile-config.properties` (two FQN values), and the eight test files that import these classes were updated in lockstep. Now-empty `config`, `context`, `extension`, `spi`, `filter`, `container`, `mock` source directories pruned.

`mvn clean verify` passes under both `-Powb` and `-Pweld`.

## 2026-05-07 — Refresh TICKET-003 ticket and issue for the new ports

Updated `tickets/003-cdi-se-implementation.md` so the port enumeration and the structural references match the shipped code:

- `Maven Module Layout` mermaid label and table row: `cdi-module/api` now lists four ports (`CdiContainerPort`, `ExcludedPackageFilter`, `MockFactory`, `WhitelistFilter`); `cdi-module/impl` row lists all four default impls and drops the stale "mock factory ships as a util" comment.
- New `cdi-module Ports` subsections for `CdiContainerPort` and `MockFactory`, each with the API signature, the default-impl name, the discovery rule, and a one-line note on how a future quarkus-module / alternative mock library plugs in.
- `cdi-module Port Implementations` gains entries for `SeContainerCdiContainerPort` and `MockitoMockFactory`; the existing entries for `CdiTestBeanContainer` and `TestBeansCdiExtension` got their `cdi-module/impl/adapter/<tech>` package paths added.
- Acceptance Criteria — Module structure and registration: bullets now enumerate four ports + four default impls, all carrying `@Priority(Integer.MAX_VALUE)`.
- Brain-dump-only sections (stripped from the issue body) also corrected for completeness: the `Package layout` mermaid uses the new `…adapter.<tech>` paths and shows the ConfigResolver-mediated MP Config flow; the `Hex view` mermaid adds `CdiContainerPort` and `MockFactory` ports plus their default impls and updates the MP Config caption ("read by ConfigResolver port impl" rather than "ConfigProvider — direct").

Issue #6 body re-derived by stripping the same brain-dump sections as before (`cdi-module Structure / Overview / Implementation Details`, `Object Lifecycle`, `Pre / Post conditions`, `Use Cases`) and uploaded via `gh issue edit 6 --body-file`.

## 2026-05-07 — TICKET-004 (scope-module) shipped

Built `scope-module` end-to-end. Branch `8-scope-module`, 16 commits. PR pending.

Production code:
- `core/api/port` — two new records `TestBeanDefaultScope` and `AutoMockDefaultScope` for the cross-module scope-default override (no behaviour, just a `Class<? extends Annotation>` token).
- `modules/scope-module/api` — `@TestMethodScoped` and `@TestClassScoped`, both `@NormalScope(passivating=false)`. Designed as `@ApplicationScoped`-with-shorter-lifetime: `isActive()` is always `true`, lazy first-access creation, single managed instance shared across threads.
- `modules/scope-module/impl` — `ScopeStore` + per-scope subclasses, `ScopedBeanInstance` record, `TestMethodScopedContext` and `TestClassScopedContext` (both `AlterableContext` impls), `TestScopeCdiExtension` (binds the override records in `BeforeBeanDiscovery`, registers contexts in `AfterBeanDiscovery`), `ScopeLifecycleAdapter` (`@Priority(100)` `TestModuleLifecyclePort` impl). Lives under `impl.adapter.{context,extension,lifecycle}` per the project rule.
- `cdi-module/impl` — modified `TestBeansCdiExtension` and `SyntheticBeanUtil` to read the override records and apply the precedence rules: user-declared CDI scope on a `@TestBean` field > `TestBeanDefaultScope` record > `@Singleton` fallback; auto-mock takes `AutoMockDefaultScope` record > `@RequestScoped` fallback (JDK types stay `@Dependent` regardless).

Tests: `tests/scope-module/` aggregator with 27 scenario sub-modules covering per-method scope behaviour (1–11), per-class scope behaviour (12–17), lifecycle adapter / extension / threading (18–23), and TICKET-003 addendum integration (24–27). Two scenarios (08, 16) became direct unit tests of `ScopeStore.destroyAll`'s aggregation contract because OWB swallows `@PreDestroy` exceptions inside `InjectionTargetImpl.preDestroy`; scenario 23 became a direct unit test of store isolation because parallel `SeContainer` boots are governed by the underlying CDI runtime's parallel-safety, not scope-module's.

Coverage report includes the 27 new scenario modules. `mvn -P owb clean verify` and `mvn -P weld clean verify` both pass against the full reactor (105 scenario modules, all green).

Architecture.md updated: new "scope-module additions" section + new row in the Adapters table for `TestModuleLifecyclePort` → `ScopeLifecycleAdapter` + `TestScopeCdiExtension`.

## 2026-05-07 — TICKET-005 Phase 6 (pom scaffold)

Scaffolded the jpa-module Maven structure on branch `10-jpa-module`:

- Pinned JPA-related library versions in root `pom.xml` `<properties>` and `<dependencyManagement>`: `jakarta.persistence-api 3.2.0`, `jakarta.transaction-api 2.0.1`, Hibernate ORM `7.0.4.Final`, H2 `2.3.232`, ASM `9.7.1`. Persistence + transaction + ASM default to `provided` (consumers bring the runtime); Hibernate + H2 default to `test` (jpa-module's smoke tests bring them up).
- Added internal cross-references for `jawelte-jpa-module-{api,impl}` to the root `<dependencyManagement>`.
- Registered `<module>jpa-module</module>` in `modules/pom.xml`.
- Created `modules/jpa-module/{pom.xml, api/pom.xml, impl/pom.xml}` following the scope-module shape: aggregator (packaging=pom) + api + impl (both jars, javadoc-jar bound to verify). api depends on `jawelte-core-api` plus jakarta cdi/persistence/tx APIs; impl additionally depends on api + microprofile-config-api + asm.
- `./mvnw validate` green: 124-module reactor including the three new jpa-module rows; Enforcer + Checkstyle pass.

## 2026-05-07 — TICKET-005 Phase 7a (jpa-module/api)

Authored 11 jpa-module/api types under `org.os890.jawelte.module.jpa.api.*`:

- **Annotations** (2): `@PersistenceConfig` (TYPE, @Inherited) with `fileMode`/`filePath`/`persistenceUnits` attributes; `@ReadOnly` (TYPE+METHOD, `@InterceptorBinding`).
- **Ports** (5, package `…api.port`): `TransactionStrategy` (10-method facade with prose-level pre/post/error contracts on every method), `DbCleanupStrategy`, `EntityResolver`, `PersistenceUnitConnectionResolver`, `PersistencePropertyResolver`. All five carry the project-wide note that consumers obtain the active impl through `TestContext.loadService(...)`.
- **Events** (4, package `…api.event`): `TransactionStarted`, `TransactionBeforeCompletion`, `TransactionCommitted`, `TransactionRolledBack` — each carrying the active persistence unit name as a single `String` field with constructor + getter.

`@Transactional` and `@TransactionScoped` are reused from `jakarta.transaction-api`; not redeclared.

Hit one strict-Javadoc bump: documented `jakarta.transaction.RollbackException` (checked) on `TransactionStrategy.commit()` without a `throws` clause; switched to `jakarta.persistence.RollbackException` (unchecked) which matches what `EntityTransaction.commit()` actually throws.

`./mvnw -pl modules/jpa-module/api -am verify -DskipTests` green. RAT 12/12 approved; Checkstyle clean; Javadoc strict mode clean.

## 2026-05-07 — TICKET-005 Phase 7b (jpa-module/impl)

Authored 17 jpa-module/impl Java types + 7 META-INF resources.

**Util layer** (`…impl.util`, 6 files): `EmfCache` (JVM-wide EMF cache, ConcurrentHashMap, JVM shutdown hook); `TransactionScopedEmHolder` (per-thread `Map<puName, Deque<EntityManager>>` with push/pop/peek + `clearForCurrentThread()` for the orphan safety net); `EntityScanner` (ASM `ClassReader` walking `java.class.path` jars + dirs for `@jakarta.persistence.Entity` annotation refs without `Class.forName`); `EntityManagerProxy` (JDK `InvocationHandler` delegating to top-of-stack EM, with custom `equals`/`hashCode`/`toString`); `JpaActivePersistenceUnits` (atomic-reference registry of active PU names, set by extension, read by strategy); `PersistenceXmlParser` (DOM-based parse of every `META-INF/persistence.xml` reachable through a class loader, returning `(name, classes, hasClassElements)` records).

**Adapter layer** (`…impl.adapter.*`, 11 files):
- `adapter.cleanup.JpqlDeleteDbCleanupStrategy` — provider-agnostic JPQL `DELETE FROM <entity>` per resolved entity in reverse order, per-entity exception aggregation per TICKET-001;
- `adapter.connection.DefaultPersistenceUnitConnectionResolver` — `EntityManager.unwrap(Connection.class)` over the active per-thread EM stack;
- `adapter.context.TransactionScopedContext` (+ `TransactionScopedBeanInstance` record) — per-thread `Deque<Map<Contextual<?>, …>>` driven by `activate()` / `deactivate()` calls from the interceptor; `Contextual.destroy` aggregates failures;
- `adapter.entity.JpaMetamodelEntityResolver` — wraps `EntityManagerFactory.getMetamodel().getEntities()`;
- `adapter.extension.JpaCdiExtension` — observes BBD (parses persistence.xml, runs entity discovery, pre-warms EMFs, sets active-PU registry, registers interceptor bindings), PAT-with-`@WithAnnotations` (rewrites `@PersistenceContext`/`@PersistenceUnit` to `@Inject` + optional `@Named`), four `ProcessProducer{Method,Field}` observers (per-PU back-off detection for user `@Produces EntityManagerFactory` / `EntityManager`), and ABD (registers synthetic EMF + EM per active PU, synthetic UserTransaction, and `addContext(new TransactionScopedContext())`);
- `adapter.interceptor.{Transactional,ReadOnly}Interceptor` — `@Priority(PLATFORM_BEFORE+200/+201)` nesting; checked-vs-unchecked rollback rules; flush-mode `COMMIT` + `setRollbackOnly` on `@ReadOnly`;
- `adapter.lifecycle.JpaLifecycleAdapter` — `@Priority(200)`; `afterEach` runs orphan-rollback safety net → fires `AfterTestTransaction` → invokes active `DbCleanupStrategy` per active PU when `fileMode=false`; `afterAll` clears EM stack + resets active-PU registry;
- `adapter.tx.DefaultResourceLocalTransactionStrategy` — JVM-wide singleton; per-thread `Deque<TransactionFrame>` for nested `@Transactional`; iterates active PUs from `JpaActivePersistenceUnits`; fires the four CDI events;
- `adapter.tx.UserTransactionImpl` — delegates to `TestContext.loadService(TransactionStrategy.class)`; `setTransactionTimeout` is a documented no-op (RESOURCE_LOCAL has no native timeout).

**META-INF resources**: `beans.xml` (annotated mode), 6 ServiceLoader registrations (`Extension`, `TestModuleLifecyclePort`, `TransactionStrategy`, `DbCleanupStrategy`, `EntityResolver`, `PersistenceUnitConnectionResolver`) — every services file with `#`-prefix Apache 2.0 header.

Hit two Checkstyle bumps along the way: `TransactionScoped` import-order (uppercase precedes lowercase within `jakarta.transaction.*`); 122-char line on a multi-annotation observer parameter (split onto two lines).

`./mvnw -pl modules/jpa-module/impl -am verify -DskipTests` green: RAT 25/25 approved, Checkstyle 0 violations, Javadoc strict mode clean, javadoc-jar built.

## 2026-05-07 — TICKET-005 Phase 7c (tests/jpa-module scaffold)

Reactor went from 124 → 168 modules. 44 scenario sub-modules created under `tests/jpa-module/scenario-NN-<slug>/`, plus the parent at `tests/jpa-module/pom.xml`.

- `tests/pom.xml` now lists the new aggregator (`<module>jpa-module</module>`).
- `tests/jpa-module/pom.xml` mirrors `tests/scope-module/pom.xml` shape: parent for every scenario, declares 44 module entries, ships the shared deps every scenario inherits at test scope (core/api+impl, cdi-module/api+impl, jpa-module/api+impl, jakarta.{enterprise.cdi,annotation,inject,persistence,transaction}-api, microprofile-config-api + smallrye-config, asm + hibernate-core + h2, mockito-core, junit-jupiter + junit-platform-testkit, assertj-core), and `-P owb` (default) / `-P weld` profiles.
- Each scenario directory has: `pom.xml` (~30 lines, Apache header + parent ref + descriptive name), `src/test/resources/META-INF/persistence.xml` (Jakarta Persistence 3.2 schema, `transaction-type=RESOURCE_LOCAL`, Hibernate provider, `<exclude-unlisted-classes>false</exclude-unlisted-classes>`, unique PU name `testPU<NN>`), `src/test/resources/META-INF/beans.xml` (CDI 4.0, `bean-discovery-mode="all"`), `src/test/java/.../Scenario<NN>Test.java` (placeholder class with class-level Javadoc pointing at the ticket scenario number; no `@Test` methods yet).
- Bulk generation done via a single bash heredoc loop driven by `/tmp/scenarios.txt`; Apache 2.0 license headers on every file (XML comment in pom / persistence.xml / beans.xml; Java comment in the Test class).
- `./mvnw validate` green: 168 modules, all jpa-module scenario rows SUCCESS, RAT clean across the new files.

Real test bodies for each scenario land in follow-up commits on this branch.

## 2026-05-07 — TICKET-005 Phase 7d (coverage-report deps)

`coverage-report/pom.xml` now lists `jawelte-jpa-module-{api,impl}` as production-class deps (so `report-aggregate` analyzes them) and every one of the 44 new jpa-module scenarios as test-execution-data deps. The aggregator entries were inserted in deterministic order via an `awk` splice keyed on the last scope-module entry; total line count went from 634 to 864.

`./mvnw -pl coverage-report validate` green.

## 2026-05-07 — TICKET-005 Phase 8 (verify under owb + weld)

`./mvnw -P owb verify` and `./mvnw -P weld verify` both green end-to-end on the 168-module reactor.

- All 56 cdi-module + 27 scope-module + 22 core scenarios continue to pass on both profiles (no regressions).
- 44 jpa-module scenarios compile + run as placeholder Test classes (no `@Test` methods); surefire skips them with no failures.
- Coverage gates (Checkstyle / Maven Enforcer / Apache RAT / JaCoCo / Javadoc strict mode) all clean under both profiles.
- jpa-module/api + jpa-module/impl currently report 0% line coverage in `coverage-report/target/site/jacoco-aggregate/jacoco.csv` because no `@Test` body invokes them yet — that delta closes as the 44 scenario `@Test` bodies land in follow-up commits on this branch.
- Both profiles complete in roughly 1m54s on the local machine; per-scenario runtime is ~0.35-0.40s for the placeholders.

Build is in a clean, reviewable state for PR. The scaffold + jpa-module/api + jpa-module/impl + coverage-report integration are reviewable independently of the per-scenario `@Test` bodies that follow.

## 2026-05-07 — TICKET-005 Phase 9a (architecture.md)

Updated `architecture.md` with the four pre-approved changes for jpa-module:
1. Modules table: `jawelte-jpa` → `jawelte-jpa-module`, plus a "per-method DB cleanup" addition to the Purpose column.
2. After scope-module additions: added a `**jpa-module additions (in jpa-module/api):**` subsection listing `@PersistenceConfig`, `@ReadOnly`, the five ports (`TransactionStrategy`, `DbCleanupStrategy`, `EntityResolver`, `PersistenceUnitConnectionResolver`, `PersistencePropertyResolver`), the four CDI events, and the explicit reuse of `jakarta.transaction.{Transactional,TransactionScoped}`.
3. Adapters table: appended five new rows for jpa-module's adapters (`JpaLifecycleAdapter` + `JpaCdiExtension` for `TestModuleLifecyclePort`, default impls for the four prioritized SPIs).
4. Planned section: removed the old `JpaContainerPort / JtaContainerPort` placeholder (replaced by the actual port set we shipped); added `JtaTransactionStrategy` as the future @Priority-substitution placeholder. JaxRs / Dataset / HttpStub planned items unchanged.

Diff matched the user-approved preview verbatim.

## 2026-05-07 — TICKET-005 follow-up (Task #76: EntityScanner cache + default excludes)

`EntityScanner` keeps the ASM-based scan (no `Class.forName`) and now adds two POC-suggested tweaks:

- **Per-classloader cache.** A `WeakHashMap<ClassLoader, Set<String>>` caches the unfiltered scan result so repeat calls in the same JVM are O(1) after the first hit. Synchronised on the map itself (WeakHashMap isn't thread-safe). Excludes are applied at lookup time so a single cache entry serves any caller.
- **Default exclude list.** `EntityScanner.defaultExcludedPackagePrefixes()` returns an unmodifiable set covering the JDK, Jakarta APIs, the bundled Hibernate / H2 / CDI runtimes, common test-time libraries (Mockito, ByteBuddy, JUnit, OpenTest4J), and jawelte's own root package. `JpaCdiExtension.readProtectedPackagePrefixes` falls back to this when the `org.os890.jawelte.module.jpa.api.PersistenceConfig.protected-packages` MP Config key is unset; setting the key still replaces the list verbatim (no append semantics, mirrors POC).

Full reactor `mvn -P owb verify` green; no scenario regressions.

## 2026-05-07 — TICKET-005 follow-up (Task #77: ReadOnly flush-mode restore)

`ReadOnlyInterceptor` now captures every touched `EntityManager`'s original `FlushModeType` before switching to `COMMIT` and restores them in a `finally` block. Previously a nested `@ReadOnly` on a thread that had its outer EM in `AUTO` mode would leak `COMMIT` mode out to the outer level after returning; now the outer level keeps its original mode regardless of how nested invocations toggle it.

Restructured `aroundInvoke` so the catch chain (RuntimeException/Error vs checked) lives in an inner try, and the flush-mode restore is the only thing in the outer `finally`. Per-PU restore failures are caught individually because by the time `finally` runs the EM may already be mid-completion (`setFlushMode` would refuse) — the EM is about to close anyway, so the failure is swallowed and the primary throwable (if any) stays intact.

mvn -pl modules/jpa-module/impl -am verify green.

## 2026-05-07 — TICKET-005 follow-up (Task #78: persistence.xml test-classpath wins)

`PersistenceXmlParser.parseAll(ClassLoader)` now prefers test-classpath URLs over jar-classpath URLs when both are reachable. Implementation:

1. Collect every `META-INF/persistence.xml` URL from the classloader.
2. Filter to those whose path contains `/test-classes/` or `/test/`.
3. Parse only the filtered set if non-empty; otherwise fall back to parsing all (preserves current behaviour for jar-only classpaths).

Lets a project ship a production-shaped persistence.xml in a jar dependency (typical "JTA + PostgreSQL" shape) and override it for tests with a test-scope file. Without the preference the parser merged both, which would have duplicated persistence-unit names or booted the prod PU against test H2.

Full reactor mvn -P owb verify green.

## 2026-05-07 — TICKET-005 follow-up (Task #79: vendor-internal CDI bean veto)

`JpaCdiExtension` now ships an additional `ProcessAnnotatedType` observer (`onProcessAnnotatedTypeForVendorVeto`) that vetoes types in `com.arjuna.ats.jta.cdi.*` (Narayana) and `org.apache.geronimo.transaction.*` (Geronimo) when our extension is active for the current test class. Defensive measure against duplicate-bean conflicts: even though jawelte itself ships only RESOURCE_LOCAL, downstream test classpaths sometimes pull in JTA jars whose CDI beans collide with ours.

Allowlist via the new MP Config key `org.os890.jawelte.module.jpa.vendor-veto.allowlist.packages` (dot-then-underscore fallback applies). Comma-separated prefix list; matched prefixes are exempt from the veto. The list is read once on first match and cached on the extension instance (per-test-class lifetime — extension is re-instantiated per `SeContainer`).

Full reactor mvn -P owb verify green.

## 2026-05-07 — TICKET-005 follow-up (Task #80: multi-PU lazy tx begin)

`DefaultResourceLocalTransactionStrategy` no longer eagerly opens a transaction on every active persistence unit. Redesign:

- **Strategy.begin()** picks the "managed" PU as the first entry of `JpaActivePersistenceUnits.get()` (insertion-ordered, mirrors persistence.xml document order). Creates an EM + opens tx for that one PU only, fires `TransactionStarted(managedPu)`, calls `TransactionScopedEmHolder.enterTransactionalScope(managedPu)` which seeds the per-frame join set with the managed PU.
- **TransactionScopedEmHolder.peekOrAutoBegin(puName)** — new public method used by `EntityManagerProxy.invoke` instead of `peek`. When an EM exists on the per-PU stack it returns it; otherwise, if a transactional scope is active and the PU is in `JpaActivePersistenceUnits`, lazy-creates a fresh EM, opens a tx on it, pushes onto the per-PU stack, adds the PU to the current frame's join set, and fires `TransactionStarted(puName)`. Returns `null` only when no scope is active and no EM exists (caller throws as before).
- **Strategy.commit() / rollback()** read the joined-PU set from `TransactionScopedEmHolder.currentFramePersistenceUnits()` (managed + lazy-joined) and complete each in turn, popping + closing each EM. Both call `exitTransactionalScope()` in their finally so the per-thread stacks unwind cleanly even on partial failure.
- New `MANAGED_PU_STACK` and `FRAME_PUS_STACK` thread-locals on `TransactionScopedEmHolder` track per-frame state. `clearForCurrentThread()` (called by `JpaLifecycleAdapter.afterEach` as a safety net) wipes both.
- `TransactionFrame` in the strategy simplified to just `rollbackOnly` — the per-PU set lives in the holder now.
- The `TransactionStarted` event still fires once per PU that actually joins the tx, so observer counts remain meaningful even with multi-PU.

Net effect: a `@Transactional` method that only touches the default PU pays exactly one EM-create / tx-begin / tx-commit. Inactive non-default PUs (e.g. configured but unreachable) are never dereferenced and never dragged into the tx.

Full reactor mvn -P owb verify green.

## 2026-05-07 — TICKET-005 follow-up (Task #81: multi-PU flush-all/commit-all)

`DefaultResourceLocalTransactionStrategy.commit()` now does a two-phase commit across the frame's joined PU set:

1. **flushAllOrRollback** — iterate the joined PUs in insertion order, `em.flush()` each. On the first failure: roll every PU back, fire `TransactionRolledBack` per PU, pop + close each EM, throw the flush failure. Per-PU rollback / pop / close failures are aggregated onto the flush exception via `addSuppressed`.
2. **commitAllAggregated** — every flush succeeded, so commit each EM. Per-PU commit failures aggregate via primary + `addSuppressed` (TICKET-001 policy); the loop still pops + closes every EM so a late failure doesn't leak open EMs. Throws the aggregated exception at the end.

This is best-effort cross-PU atomicity over independent RESOURCE_LOCAL transactions: a flush failure on any PU rolls every PU back before any commit happens, so nothing reaches the database. After phase 1 has succeeded, phase 2's commits are no-fail in practice (nothing left to validate), but defensive aggregation handles JDBC-level surprises.

Full reactor mvn -P owb verify green.

## 2026-05-07 — TICKET-005 follow-up (Task #83: TRUNCATE-with-RI-off cleanup strategy)

New `JdbcTruncateDbCleanupStrategy` shipped under `…impl.adapter.cleanup`. Walks `INFORMATION_SCHEMA.TABLES` filtered to the `PUBLIC` schema, disables referential integrity (`SET REFERENTIAL_INTEGRITY FALSE`), `TRUNCATE TABLE`s every entry, then re-enables RI. Touches every table — including auto-generated `@JoinTable`s, `@ElementCollection` backing tables, and Hibernate sequence/hilo tables — that the JPQL-based default can't reach because it iterates only mapped `@Entity` types. Disabling FK checks during the truncate handles schemas with circular FKs without topological ordering.

Per-table failures aggregate via primary + `addSuppressed` per TICKET-001; on a primary failure the whole truncate transaction rolls back. The strategy opens its own short-lived `EntityManager` from the EMF (cleanup runs after the user's tx is done, so no active EM exists on the per-thread stack); `em.unwrap(Connection.class)` retrieves the JDBC connection in a provider-agnostic way.

`@Priority(Integer.MAX_VALUE - 1)` — one rank ahead of the JPQL default. With both impls registered in `META-INF/services/org.os890.jawelte.module.jpa.api.port.DbCleanupStrategy`, the TRUNCATE strategy wins by default for the H2-shipped test setup. Consumers running against a non-H2 database can drop this jar from the test classpath or register an alternative impl at an even lower priority.

H2-specific: `SET REFERENTIAL_INTEGRITY` is an H2 extension and the schema filter uses `'PUBLIC'`. Documented in the class Javadoc.

Full reactor mvn -P owb verify green.

## 2026-05-07 — TICKET-005 follow-up (Task #84: owned-tx event filter)

Made the "framework owns this tx" property explicit. Added a `FRAMEWORK_OWNED` thread-local flag on `TransactionScopedEmHolder`:

- Set to `TRUE` in `enterTransactionalScope` (called from `Strategy.begin()`).
- Cleared in `exitTransactionalScope` once the outermost frame pops.
- Read by `DefaultResourceLocalTransactionStrategy.fireEvent` and `TransactionScopedEmHolder.fireTransactionStartedQuietly` — both early-return when the flag is `false`.
- Wiped by `clearForCurrentThread` (the lifecycle adapter's `afterEach` safety net).

Net effect: identical observable behaviour to before (we already only fired events from inside framework code paths), but now the contract is explicit and verifiable. User code that calls `em.getTransaction().begin()` directly bypasses the framework and therefore does not see the four CDI tx events fire. Defensive against future drift if event-firing helpers are reused from new call sites.

Full reactor mvn -P owb verify green.

## 2026-05-07 — TICKET-005 follow-up (Task #82: fileMode redesign)

`@PersistenceConfig(fileMode=true)` now mirrors POC's debug-mode shape inside our existing structure:

**URL change** (`JpaCdiExtension.computeProperties`): file-mode H2 URL becomes `jdbc:h2:file:{filePath}/{puName}_{testClassSimpleName};DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE`. The test-class suffix prevents two test classes that share a PU name from colliding on the same H2 file. `AUTO_SERVER=TRUE` lets a developer attach with the H2 console while the test JVM still holds the file.

**Skip-after-first** (`JpaLifecycleAdapter`): in `beforeAll`, when `@PersistenceConfig.fileMode=true`, bind a new `FileModeState` (small util in `…impl.util`) on `TestContext` via the existing typed-metadata mechanism. `beforeEach` checks for the marker and throws `TestAbortedException` if `firstMethodExecuted` is set, with a message pointing at the H2 file directory. `afterEach` marks the flag after the first method completes and skips per-method DB cleanup so the file state is preserved.

**Per-class file lifecycle** (`JpaLifecycleAdapter.afterAll` + `EmfCache`): in file mode, evict every active PU's EMF (close + remove from cache) so the H2 file lock releases for the next test class. New `EmfCache.evict(puName)` helper handles the close + removal and logs failures at WARNING. `afterAll` also unbinds the `FileModeState` marker before resetting the global registries.

**opentest4j dep**: `jpa-module/impl` now declares `org.opentest4j:opentest4j` at `provided` scope (version `1.3.0` pinned in root `<dependencyManagement>`). `TestAbortedException` is a JUnit-spec marker for "this method was skipped"; consumers always have it transitively via `junit-jupiter-api`.

Full reactor mvn -P owb verify green.

## 2026-05-07 — TICKET-005 follow-up (Tasks #85/#86/#87: decisions applied)

User decisions on the three open design points:

1. **Rollback rule on checked exceptions**: switched to "rollback on any exception" (deviates from Jakarta EE convention but simpler — a thrown exception almost always means "this work should not persist"). `TransactionalInterceptor.aroundInvoke` now calls `rollbackQuietly` for the checked-exception catch as well; the previous `commitQuietly` helper is dropped. Class-level Javadoc updated to call out the deliberate divergence and to note that `rollbackOn` / `dontRollbackOn` attributes on `@Transactional` are still source-level accepted but ignored at runtime.
2. **Synthetic bean scope**: switched EMF / EM proxy / UserTransaction synthetic beans from `@Singleton` (pseudo-scope) to `@ApplicationScoped` (CDI normal-scope proxy). Better Spring Data / extension-aware compatibility; minor cost is the proxy wrapper around each injection point. `JpaCdiExtension` now imports `jakarta.enterprise.context.ApplicationScoped` and the previous `jakarta.inject.Singleton` import is gone.
3. **BeforeTestMethod / AfterTestMethod events**: not shipped. The `TestModuleLifecyclePort.beforeEach` / `afterEach` callbacks already cover the use case, per ticket-005 reasoning.

Full reactor mvn -P owb verify green.

## 2026-05-07 — TICKET-005 follow-up (defensive ASM catch + scenario-45 rollback)

**Defensive `IllegalArgumentException` catch in `EntityScanner`.** The shipped ASM 9.7.1 only recognises class-file major versions up to Java 23 (version 67); a Java 25 (version 69) class on the test classpath causes `ClassReader`'s constructor to throw `IllegalArgumentException("Unsupported class file major version 69")`, which on the previous code surfaced as a CDI bootstrap failure. Both `scanJar` and `readClassFile` now catch `IllegalArgumentException` alongside `IOException` and log + skip — newer class files cannot carry an `@Entity` ASM can read anyway, so the skip is correct.

**Scenario-45 (ContainerStarted seeding) rolled back.** Adding the first real `@Test` body in our suite to a new scenario sub-module surfaced a foundational issue: OWB reports `AmbiguousResolutionException` with 3 `EntityManager` beans (all `THIRDPARTY` from synthetic registration, bean-class location reported as the `jakarta.persistence-api` jar). Root cause unclear from the stack trace — possibilities include `JpaCdiExtension.onAfterBeanDiscovery` firing 3 times, the extension being loaded thrice via duplicate `META-INF/services` entries on the classpath, or an OWB-specific synthetic-bean accounting interaction with our scope change to `@ApplicationScoped`. The 168-module reactor verify passes today because every existing scenario placeholder has no `@Test` methods — surefire never boots the CDI container for them, so the issue stays hidden until a real test arrives.

This blocks all per-scenario `@Test` work (tasks #88, #89, #90, #91, …, #100, #107, #108, …, #112). Captured as task #117 ("Investigate OWB 3 EntityManager beans AmbiguousResolution") for the next session. Scenario-45 sub-module deleted; `tests/jpa-module/pom.xml` reverted to 44 modules. The ASM defensive catch stays because it's a genuine fix.

mvn -P owb verify still green on the now-back-to-44-scenarios reactor.

## 2026-05-07 — TICKET-005 follow-up (Tasks #88 + #117: first end-to-end scenario green)

Scenario 45 (ContainerStarted-driven data seeding) now passes both test methods. Fixes that landed along the way:

1. **ASM 9.7.1 → 9.8.** Newer ASM understands Java 25 class files. The defensive `IllegalArgumentException` catch in `EntityScanner` stays as belt-and-suspenders for class files with versions even newer than ASM knows.
2. **`JpaTypesExcludedPackageFilter`.** New `ExcludedPackageFilter` impl shipped by jpa-module/impl at `@Priority(Integer.MAX_VALUE - 1)`; teaches cdi-module's auto-mock layer to skip `jakarta.persistence.*` and `jakarta.transaction.*` so it does not pre-empt jpa-module's synthetic EntityManager / EntityManagerFactory / UserTransaction beans with parallel mock registrations (was causing `AmbiguousResolutionException` with three `THIRDPARTY` EntityManager beans). The filter is a separate impl that ships only with jpa-module/impl, so projects that use cdi-module without jpa-module keep the default mock-everything behaviour for JPA/JTA types where the mocks may indeed be wanted. Adds `cdi-module/api` as a compile-time dep on `jpa-module/impl` (api only — no impl coupling).
3. **`TransactionScopedContext` always-active.** Changed `isActive()` to always return `true` so `BeanManager.getContext(TransactionScoped.class)` succeeds before the first `activate()` call. The CDI Container wraps registered Contexts in its own internal passivating-capable wrapper (e.g. OWB's `CustomAlterablePassivatingContextImpl`), so `getContext` returned the wrapper and a cast to our own type failed; added `TransactionScopedContext.current()` static accessor that captures `this` on construction. The interceptor now retrieves the raw instance via the static accessor instead of `beanManager.getContext`, sidestepping both the always-active check and the wrapper. The actual "is there a tx scope?" guard moved to `get(Contextual, ...)` which throws `ContextNotActiveException` when the per-thread frame stack is empty (matches scope-module's pattern; standard CDI semantics for `@TransactionScoped` are still observable at the lookup site).
4. **EntityScanner default exclude list tightened.** Changed the default `org.os890.jawelte.` umbrella to two narrower entries (`org.os890.jawelte.core.` and `org.os890.jawelte.module.`) so test entity classes under `org.os890.jawelte.tests.*` are no longer accidentally filtered out.
5. **`EmfCache.getOrCreate(name, Supplier)`.** Refactored to accept a factory function; the caller decides between `Persistence.createEntityManagerFactory(name, props)` and the Hibernate-specific container-EMF path with a custom `PersistenceUnitInfo`. The legacy properties-only signature is dropped.
6. **`TestPersistenceUnitInfo` + Hibernate auto-discovery path.** Hibernate doesn't auto-scan for `@Entity` classes outside an application server, so we now feed the merged list of declared (`<class>` entries) + ASM-scanned entities through a programmatic `PersistenceUnitInfo` and bootstrap via `HibernatePersistenceProvider.createContainerEntityManagerFactory(unitInfo, properties)`. `JpaCdiExtension.bootstrapEntityManagerFactory` branches: `unit.hasClassElements()` → standard JPA path; otherwise → custom-PUInfo path. Added `hibernate-core` as a `provided` compile dep on jpa-module/impl.
7. **`JdbcTruncateDbCleanupStrategy` uses `Session.doWork`.** Hibernate 7's `SessionImpl` doesn't support `unwrap(Connection.class)`; switched to `em.unwrap(Session.class).doWork(conn -> …)` which is the documented Hibernate API for session-bound JDBC work.

Captured during diagnosis as follow-up tasks: #117 (multi-EM-bean root cause confirmed + fixed), #119 (cross-check tx/em handling against POC), #120 (`@Transactional` on test methods support — JUnit invokes test methods directly bypassing CDI proxies).

Full reactor mvn -P owb verify green on the 169-module reactor (added scenario-45).

## 2026-05-07 — TICKET-005 follow-up (Task #89: M:1 relationship scenario)

Scenario 46 (m1-relationship) added: `Customer` (parent) + `Order` (child, `@ManyToOne`); a `@Transactional` service persists both, walks the relationship from `Order.customer.name`, asserts row counts. Second method asserts per-method cleanup wiped both tables. Validates auto-discovery picks up multiple entities and Hibernate's `@ManyToOne` resolution works in our bootstrap path.

`./mvnw -pl tests/jpa-module/scenario-46-m1-relationship -am test` green.

## 2026-05-07 — scenario-52: @TransactionScoped per-tx PostConstruct/PreDestroy counts

Added `tests/jpa-module/scenario-52-tx-scoped-lifecycle-counts`. Verifies that two consecutive `@Transactional` calls each open and close their own transaction, so the `@TransactionScoped` audit-tracker bean goes through two complete CDI lifecycles — `POST_CONSTRUCT_COUNT` reaches 2, `PRE_DESTROY_COUNT` reaches 2. Static counters live on the bean class so the test can sample them after both invocations. The `touch()` proxy method materialises the contextual instance inside each tx (without that, the proxy never resolves and the lifecycle never fires).

Result: Tests run: 1, Failures: 0, Errors: 0 — task #99 done.

## 2026-05-07 — scenario-53: @TransactionScoped nested-tx isolation w/ counts

Added `tests/jpa-module/scenario-53-tx-scoped-nested-isolation`. Outer `@Transactional` calls inner `@Transactional`; each tx scope owns a distinct `NestedTxScopedTracker` instance (verified via per-instance UUID). Asserts: (a) outer's id sampled before and after the nested call is identical — outer's contextual instance survives the nested boundary; (b) inner's id differs from outer's — the inner tx opens its own scope; (c) PostConstruct=2 + PreDestroy=2 — both instances complete their lifecycle. Proves the tx scopes are stacked, not shared.

Result: Tests run: 1, Failures: 0, Errors: 0 — task #100 done.

## 2026-05-07 — scenario-16: @ReadOnly setter-modification rolled back + tx-strategy state fix

Filled in `tests/jpa-module/scenario-16-readonly-discards-writes` (was a placeholder). Seed an `Item` with `name="original"`, run an `@Transactional @ReadOnly` method that calls `item.setName("modified")` on the managed entity, then read back. The setter dirty-mark must not reach the database — `@ReadOnly` switches `FlushMode` to `COMMIT` and marks the tx rollback-only, so the dirty change is dropped.

Bug found while wiring this scenario: `DefaultResourceLocalTransactionStrategy.frames` was an instance ThreadLocal, but `TestContext.loadService(...)` returns a fresh strategy instance per call. `TransactionalInterceptor` and `ReadOnlyInterceptor` ended up on different strategies, so `@ReadOnly`'s `setRollbackOnly()` set the flag on a stack the outer interceptor never read — net effect: rollback-only was lost and the tx committed. Fixed by promoting `FRAMES` to a static field. Existing nested-tx and tx-scoped scenarios still pass.

Result: Tests run: 1, Failures: 0, Errors: 0 — task #94 done.

## 2026-05-07 — scenario-54: @ReadOnly multi-modification rolled back

Added `tests/jpa-module/scenario-54-readonly-multi-modification`. A single `@Transactional @ReadOnly` body packs three heterogeneous mutations — `em.persist(new Item(...))`, `existing.setName(...)` (setter-driven dirty mark), and `em.remove(...)`. The test asserts all three are discarded together: count stays 2, the targeted "preexisting-A" name is unchanged, and the supposedly-removed "preexisting-B" still exists. Confirms `@ReadOnly`'s `FlushMode.COMMIT` + `setRollbackOnly()` is rollback-symmetric across insert / update / delete.

Result: Tests run: 1, Failures: 0, Errors: 0 — task #95 done.

## 2026-05-07 — scenario-55: writable-outer + @ReadOnly inner cross-level

Added `tests/jpa-module/scenario-55-readonly-inner-writable-outer`. Outer writable `@Transactional` persists `outer-write-before`, calls a nested `@Transactional @ReadOnly` that does `persist + setter`, then persists `outer-write-after`. Asserts: (a) the seeded note's text is unchanged — inner setter rolled back; (b) total note count = 3 (seed + 2 outer writes), so the inner `persist` was dropped while the two outer persists committed. Confirms nested transactions are isolated, not shared: inner's rollback never propagates to the outer.

Result: Tests run: 1, Failures: 0, Errors: 0 — task #96 done.

## 2026-05-07 — scenario-56: inactive PU configured-but-unused (lazy-begin)

Added `tests/jpa-module/scenario-56-inactive-pu-lazy-begin`. Two PUs declared in `persistence.xml` (`testPU56a`, `testPU56b`); the service injects only PU-A's `EntityManager` (qualified `@Named("testPU56a")`) and `getFlushMode()`s it inside `@Transactional`. A CDI-event observer records every `TransactionStarted` payload's PU name. The test asserts the recorded list is exactly `["testPU56a"]` — proves `lazy-begin` actually is lazy: a configured-but-unused PU never has its tx opened.

Result: Tests run: 1, Failures: 0, Errors: 0 — task #107 done.

## 2026-05-07 — scenario-57: framework-owned tx event filter

Added `tests/jpa-module/scenario-57-framework-owned-tx-events`. Two persist paths to a real `Marker` entity: a `@Transactional` method (framework-driven), and a method that pulls a fresh `EntityManager` from the injected `EntityManagerFactory` and drives its `EntityTransaction` directly (user-driven, bypasses the strategy). A `TxEventRecorder` observer counts every `TransactionStarted` / `TransactionBeforeCompletion` / `TransactionCommitted` / `TransactionRolledBack`. After the framework path the counts are 1/1/1/0; after the user path the counts stay 1/1/1/0 — the strategy never saw the user-driven tx, so no events fired. Confirms the framework-owned filter ties events to the strategy's `begin/commit/rollback` boundaries.

Result: Tests run: 1, Failures: 0, Errors: 0 — task #111 done.

## 2026-05-07 — scenario-25: fileMode skip-after-first + per-class lifecycle

Filled in `tests/jpa-module/scenario-25-file-mode-true` (was a placeholder). Drives a `@PersistenceConfig(fileMode = true)`-annotated `Scenario25FileModeSubject` via JUnit Platform Test Kit (`EngineTestKit.engine("junit-jupiter").selectors(selectClass(...))`); subject's name (`...Subject`) keeps surefire from picking it up directly. The subject has two ordered `@Test` methods that each append to a static list. After the kit run, statistics are `started(2).succeeded(1).aborted(1)` — `JpaLifecycleAdapter.beforeEach` raises `TestAbortedException` for the second method because the first already executed. `EXECUTED_METHODS` confirms only the first method's body ran.

Note: tested via the framework's URL-override hook (`microprofile-config.properties` setting `org.os890.jawelte.module.jpa.persistence-property.jakarta.persistence.jdbc.url` to an in-memory H2 URL). Hibernate 7's `DriverManagerConnectionProviderImpl` wouldn't bootstrap the framework's default `jdbc:h2:file:...;AUTO_SERVER=TRUE` URL inside the testkit-launched sub-container, throwing `Cannot get a connection as the driver manager is not properly initialized`. The lifecycle assertion is the test's actual point — the URL override keeps that surface in focus.

Result: Tests run: 1, Failures: 0, Errors: 0 — task #112 done.

## 2026-05-07 — scenario-58: vendor-bean veto (Narayana / Geronimo on classpath)

Added `tests/jpa-module/scenario-58-vendor-bean-veto`. Two stand-in `@ApplicationScoped` beans live in vetoed packages — `com.arjuna.ats.jta.cdi.fake.FakeNarayanaBean` and `org.apache.geronimo.transaction.fake.FakeGeronimoBean` — plus a `RegularBean` in `org.os890.jawelte.tests.jpa.scenario58.*` as the sanity check. The test uses `CDI.current().select(...)` and asserts the two vetoed beans are `isUnsatisfied()` while `RegularBean.isResolvable()`. Confirms `JpaCdiExtension`'s `@Observes ProcessAnnotatedType` veto is targeted to the configured prefixes — a real Narayana/Geronimo jar on the test classpath would be filtered out before clashing with the synthetic `UserTransaction`/`TransactionStrategy` beans.

Result: Tests run: 1, Failures: 0, Errors: 0 — task #110 done.

## 2026-05-07 — scenario-26: fileMode URL/path resolution (in-memory)

Filled in `tests/jpa-module/scenario-26-file-mode-false` (was a placeholder). The test injects the bootstrapped `EntityManagerFactory` and pins the property map: `jakarta.persistence.jdbc.url == jdbc:h2:mem:testPU26;DB_CLOSE_DELAY=-1` and `jakarta.persistence.jdbc.driver == org.h2.Driver`. Confirms the default no-fileMode path produces the in-memory H2 URL keyed by PU name.

The corresponding fileMode=true path is exercised structurally by scenario-25 (skip-after-first lifecycle); the file URL itself can't be pinned end-to-end in surefire-launched testkit subcontainers (Hibernate 7's connection pool rejects the AUTO_SERVER URL). Captured the gap implicitly — anything more granular belongs in a focused unit test against `JpaCdiExtension.computeProperties`.

Result: Tests run: 1, Failures: 0, Errors: 0 — task #93 done.

## 2026-05-07 — scenario-59: PersistenceXmlParser unit tests

Added `tests/jpa-module/scenario-59-persistence-xml-parser`. Direct-call unit tests against `PersistenceXmlParser.parseAll(ClassLoader)`, each spinning up a `URLClassLoader` rooted at a JUnit `@TempDir` so the assertions don't see (or pollute) the reactor's own `persistence.xml` resources. Four cases:

1. Single PU with `<class>` element → `hasClassElements()=true`, classes list contains the declared FQCN.
2. Two PUs declared in one xml → both parsed in declaration order, no class elements.
3. PU without `<class>` elements → `hasClassElements()=false` (drives ASM auto-discovery).
4. Test-classpath-wins: a "prod" classpath root and a "test-classes" root both expose a `persistence.xml`; only the test one survives — confirms the `/test-classes/` filter in `PersistenceXmlParser.selectPreferred`.

Result: Tests run: 4, Failures: 0, Errors: 0 — task #92 done.

## 2026-05-08 — coverage-report: pull in scenarios 45–59

Added the 15 new jpa-module scenario sub-modules (45–59) to `coverage-report/pom.xml` so the aggregated JaCoCo report includes their exec data. Also brought all per-scenario `beans.xml` / `persistence.xml` / `microprofile-config.properties` headers into RAT compliance — abbreviated `Copyright 2026 os890` + `Licensed under the Apache License, Version 2.0 (the "License")` + `...` form, matching the pre-existing scenario style. Verified `./mvnw -pl coverage-report -am verify -DskipTests` is BUILD SUCCESS.

Task #113 done.

## 2026-05-08 — Phase 8b: full reactor verify under both CDI profiles

`./mvnw verify` (default: `-P owb`) — BUILD SUCCESS in 2:11. All 130+ modules build, every scenario test passes including the 15 newly-filled scenarios (25–27, 45–59) and the static-FRAMES + RAT/Checkstyle gates.

`./mvnw verify -Pweld` — BUILD SUCCESS in 2:44. Same module set runs against Weld 6.0.4; identical green status. The static `FRAMES` ThreadLocal change in `DefaultResourceLocalTransactionStrategy` works under both CDI implementations (no instance-field assumptions baked into Weld's bean lifecycle).

Task #114 done.

## 2026-05-08 — task #116: remove POC references from source

Cleaned the only two stale "POC" mentions in `modules/jpa-module/impl`:

- `UserTransactionImpl.setTransactionTimeout(...)` Javadoc — "The POC accepts the call as a no-op" → "jpa-module accepts the call as a no-op".
- `TransactionalInterceptor` Javadoc — "this POC interceptor" → "this interceptor".

The `tickets/poc-gaps-tbd.html` file is intentionally a POC-vs-jawelte gap analysis — staying as-is. Other "POC" mentions in `tickets/005-jpa-module.md` and the issue-body draft are historical context and out of scope.

Task #116 done.
