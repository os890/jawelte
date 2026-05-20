# todo

## Sequence reset on per-method cleanup (jpa-module)

Context: punch-list §2.1 (`tickets/poc-gaps-2nd-pass.html`). Hibernate sequences are not reset by today's TRUNCATE-based per-method cleanup, so id-equality assertions like `customer.getId() == 1L` work the first time and silently fail on subsequent methods that use the same entity.

Two layers — ship as a future feature.

### Layer 1 — DB-level sequence reset (cheap, opt-in default)
Walk `INFORMATION_SCHEMA.SEQUENCES` in `JdbcTruncateDbCleanupStrategy` after the table-truncate pass and run `ALTER SEQUENCE <name> RESTART WITH <start_value>` for each. Same shape as `InformationSchemaTableNameResolver`'s table walk. ~30 LOC.

- Opt-in via MP Config: `org.os890.jawelte.module.jpa.cleanup.reset-sequences=true` (default `false` so existing tests don't churn).
- Document the pooled-optimizer caveat (see Layer 2).
- Add a scenario test that asserts `seed().getId() == 1L` across two `@Test` methods to lock in the contract.

### Layer 2 — Hibernate in-memory generator state (vendor-specific, defer)
Hibernate's default `SequenceStyleGenerator` uses pooled optimizers — it pulls a batch from the DB sequence and serves IDs from an in-memory cache on the `EntityManagerFactory`. Resetting the DB sequence does NOT evict that cache, so the next persist still gets a high id from the cached batch.

Options if a real consumer needs full reset:
- Document `allocation_size=1` as the workaround (no pooling, sequence consulted every persist; Layer 1 alone is sufficient in this mode).
- Or expose a vendor-specific knob: walk the Hibernate `MappingMetamodel` and call `optimizer.reset()` on each generator. Hibernate-internal API; would couple jpa-module's cleanup to a specific Hibernate version.
- Or document the POC's `SCRIPT NODATA` + `DROP ALL OBJECTS` heavyweight variant as the "nuclear option" cleanup strategy a consumer can register at `META-INF/services` if they need fully-fresh state.

Defer Layer 2 until a consumer asks for it.

## ejb-module/impl: align with the `adapter` package layout

Context: jpa-module/impl is organised under `…module.jpa.impl.adapter.{context,extension,util}` (and similar sub-packages elsewhere). ejb-module/impl was created flat — `…module.ejb.impl.EjbAnnotationExtension`, `…module.ejb.impl.DefaultEjbAnnotationMapper`, etc. — without an `adapter` package boundary.

Action: move the CDI extension + the default mapper + `TransactionalLiteral` + `AnnotationInstanceFactory` under an `adapter` sub-package (matching jpa-module's split into `adapter.extension`, `adapter.context`, `adapter.util`). Keep the test-only port-impl prefix `TestScenario…` in the scenarios. Update the `META-INF/services/jakarta.enterprise.inject.spi.Extension` and `META-INF/services/org.os890.jawelte.module.ejb.api.port.EjbAnnotationMapper` files to point at the new FQCNs.

Touches: `modules/ejb-module/impl`, two `META-INF/services` files, every scenario test class that imports any of the moved types. Tests/scenarios that don't import the impl types (most of them) need no change. No semantic change — pure refactor.

Defer until the current content-diff topic ships.

## content-diff-module: flexible ObjectMapper cache

Context: `JsonDiffEngine` currently holds a single `static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();` — works for the engine's stateless use (parse a string to `JsonNode`) and matches Jackson's documented thread-safety. Limitations:

- A consumer that needs Jackson modules (`JavaTimeModule`, `Jdk8Module`, custom de/serializers) can't influence the mapper.
- A consumer that wants different parser features (lenient JSON, comments allowed, single-quoted strings) has no hook.
- The mapper can't be reset or torn down between test classes — across-JVM cache lifetime is the full process lifetime.

Possible directions when the need arises:
- New SPI port `JsonObjectMapperProvider` resolved via `TestContext.loadService(...)`; default implementation returns the current shared instance. Consumers ship their own provider at lower `@Priority` to plug in their configuration.
- Or: a small `Supplier<ObjectMapper>`-shaped MP Config knob naming the FQCN of a factory class (rare exception to the project-wide "no per-port FQCN MP Config key" rule).
- Or: lazy-but-mutable cache keyed by configuration fingerprint so multiple consumers cohabit (probably overkill for a test framework).

Defer until a consumer asks. Document the existing behaviour in the api-side docs once the design lands.

## db-testdata-module — configurable marker keywords

Make the marker keyword strings tunable so consumers can pick a
different naming if our defaults clash with their domain vocabulary.
The strings currently hard-coded in `impl/util` and `api/DbDiffBuilder`
are:

- `value` — the actual DB cell value bound inside `#{...}`
  predicates (`JakartaELInterpolator.evaluatePredicate` /
  `MarkerComparator` integration).
- `num` — the `Double`-parsed form of the same cell, bound when the
  string parses as a number.
- `MATCH:` — the regex marker keyword inside the bracketed
  `[MATCH:regex]` (once D6 ships).

Possible shapes for the override:
- MP Config keys (`org.os890.jawelte.module.dbtestdata.api.markers.value`
  etc.); FQCN-style, consistent with the existing config keys.
- A `MarkerKeywords` immutable record / config service the engines
  consult once at JVM bootstrap (mirrors `ServicePriorityResolver`'s
  shape).
- Per-call override via the builder (probably overkill).

Decide the override mechanism before adding any code; the goal is
ergonomic naming the team can iterate without breaking existing
fixtures.


## Revisit later: Runnable / framework-internal IP filter follow-ups

TICKET-009 surfaced and fixed a latent issue in `TestBeansCdiExtension`: the auto-mock loop had been silently mocking `java.lang.Runnable` in every Weld test for the entire history of the extension (Weld-SE's `RunnableDecorator` declares `@Inject Runnable` and the IP collector accepted it). Mockito hid the noise everywhere it was on classpath; db-testdata-module exposed the bug because its test parent doesn't pull Mockito.

The fix is in place (commits `270f7f3` → `377e895` → `1861487`) and `verify-all.sh` is green. Revisit later to decide whether to:

- Wire a dedicated cdi-module test scenario for `ExcludedPackageFilter.isOwningBeanExcluded(...)` — mirrors the existing `scenario-19-exclude-packages` / `scenario-36-custom-excluded-package-filter` style. Currently the new SPI method only has indirect coverage via the db-testdata-module Weld run + the silent-mocking that's now removed across jpa/scope/cdi/ejb Weld phases of `verify-all.sh`.
- Add `tests/db-testdata-module` to the `for cdi in owb weld` loop in `verify-all.sh:115-120`. Pre-existing gap (db-testdata isn't in the full-matrix sweep) is what let this bug ship undetected; closing it would prevent recurrence.
- Note for architecture.md / mission.md: the auto-mock framework-internal-bean filter follows the established `FrameworkAllowlist` pattern — `META-INF/microprofile-config.properties` defaults read through the active `ConfigResolver`, no Java constants on the consuming class. Worth a sentence under the cdi-module section if we want this documented for downstream readers.

Decide later whether any/all of the three are worth doing.

## TICKET-010 follow-ups (post-implementation findings)

### `BeforeScopeStarted` veto is currently advisory

scope-module's `ScopeLifecycleAdapter` fires `BeforeScopeStarted(TestMethodScoped.class)` and then activates the context unconditionally regardless of `event.isVetoed()` — the scope-module adapter docstring acknowledges: *"the 'usage-veto' semantics — telling consumers to skip use of @TestMethodScoped beans for a given method — are deferred to a follow-up ticket."*

Also: scope-module never fires `BeforeScopeStarted` for `@TestClassScoped`; that scope is added at `AfterBeanDiscovery` and remains active for the whole test class. So vetoing `@TestClassScoped` per method is not currently expressible.

Effect on TICKET-010: testcontrol's `TestControlScopeObserver` correctly emits `event.veto()` per the `@TestControl.startScopes` allow-list, but scope-module ignores the veto. The scope-filter affirmative scenarios (11, 13, 14, 15) cannot be expected to pass until scope-module is updated.

Paths forward:
- Update `ScopeLifecycleAdapter.beforeEach` to call `methodContext.activate()` only when `!event.isVetoed()`. Mirror change for any scope-module `BeforeScopeStarted` emission.
- Fire `BeforeScopeStarted(TestClassScoped.class)` in `beforeAll` and gate `TestClassScopedContext` activation on its veto status.

### Per-entry flush in TestDataHandler error-handling

`TestDataHandler.seedAll(...)` walks `@TestControl(testData=…)` entries in array order and runs each entry's `dbIn/` files (then later each entry's `dbUpdate/` files) without flushing between entries or files. When one entry's `*.xml` dataset fails mid-pipeline (constraint violation, FK violation, DBunit parse error), the failure currently surfaces from `DbSeed…execute()` immediately — but with no breadcrumb beyond the dataset file path, and the prior entries' data sits in the open transaction without being either fully committed or fully rolled back. Error localization across multi-entry seeds is harder than it needs to be.

Two shapes to consider (now tractable because `TestDataSeedTransactionTemplate` already runs each phase inside a managed transaction with the EM on the active stack — so a `flush` / `commit` between entries is a small addition to the existing loop):

- `em.flush()` between entries: pushes the EntityManager's first-level cache to the JDBC layer; still inside the active transaction; cheap. Doesn't change the transactional contract, just makes failures surface at the entry boundary they originated from.
- `connection.commit()` between entries: stronger isolation; each entry's data is durable as soon as it succeeds, so a later entry's failure does not roll back the earlier ones. Aligns naturally with the seed-commit semantics already documented for the post-seed commit phase, just earlier in the pipeline. Changes visibility: if a later `@Transactional` test method then opens its own transaction, seed data is already committed across all entries.

Decide which model fits before adding the flush/commit calls; document in the spec table that the chosen model is the contract.

## Starting-point smoke test: jawelte on classpath, plain JUnit, nothing breaks

Add a JUnit test scenario whose pom pulls in every jawelte module (cdi, scope, jpa, jta, ejb, content-diff, db-testdata, testcontrol — as useful) at test scope, but whose test class uses NONE of jawelte's features:

- No `@EnableTestBeans` on the test class.
- No `@TestControl`, no `@PersistenceConfig`, no `@Transactional` on the test method.
- No `@Inject` of jawelte-managed beans.
- No `persistence.xml`, no DBunit datasets, no test-data folders.
- A plain `@Test` method that asserts something trivial (e.g. `assertThat(1 + 1).isEqualTo(2);`).

The point is to verify the **opt-in contract**: a user who simply adds the jawelte jars to their test classpath (e.g. while gradually adopting the framework) should NOT see their existing plain JUnit tests break. Specifically:

- CDI extensions shipped by each module must not fail bootstrap when no `@EnableTestBeans` is present (and ideally must not bootstrap a CDI container at all).
- MP Config defaults shipped in `META-INF/microprofile-config.properties` must not be picked up by unrelated MP Config consumers in a way that changes behaviour.
- `META-INF/services` registrations (lifecycle ports, CDI extensions, transaction strategies, persistence-unit resolvers, DbSeed/DbDiff engines, …) must not run side-effects on classpath load.
- Lifecycle adapters' `beforeAll` / `beforeEach` / `afterAll` / `afterEach` must not fire when the JUnit extension is not registered (i.e. when `@EnableTestBeans` is absent).
- The H2 driver (and Hibernate, and the JTA transaction strategies) being on the classpath must not boot persistence units, register transaction managers, or open connections.

Position the scenario as its own per-scenario sub-module under `tests/` — most likely `tests/core/scenario-NN-jawelte-on-classpath-plain-junit-no-regression/`, since the test does not exercise any module-specific behaviour and just pins the framework-wide opt-in contract. Run under both `-Powb` (default) and `-Pweld` profiles; both should pass without jawelte ever activating. The signal value is high: future refactors that introduce side-effects on classpath load (e.g. a global static initializer that opens a connection) would fail this scenario immediately.

Two design choices to settle before writing the scenario:

- Should the pom pull jpa-module-impl + Hibernate + H2 too? (Confirms that even with the JPA stack on classpath, no persistence unit boots automatically.) Probably yes — the contract is strongest when ALL modules are present.
- Should we register a JUnit `ExtensionContext` listener / custom logger to assert NO jawelte lifecycle adapter ran? Or trust "the test method passes with the expected assertion" as evidence enough? Probably the latter for simplicity; the former is over-engineered.

## TICKET-011 follow-up — honour `@ApplicationPath` in `jaxrs-module`

`JaxRsLifecycleAdapter` currently wraps the user's `restResources`
in its own `TestApplication` (no `@ApplicationPath`) and pins
`SeBootstrap.Configuration.rootPath("/")`. A production application
class like

```java
@ApplicationPath("demoRest")
public class DemoRestApp extends Application { ... }
```

is therefore invisible to the test setup: tests hit
`<base>/<resource>` rather than `<base>/demoRest/<resource>`, so
test URLs cannot match the deployed shape.

**Options** (pick when picking up the follow-up):

- **A.** Add `applicationPath` attribute to `@EnableJaxRs`:
  `@EnableJaxRs(applicationPath = "demoRest", restResources = {...})`
  — the value flows to `SeBootstrap.Configuration.rootPath(...)`.
  Simple; user has to keep it in sync with the production
  `@ApplicationPath`.
- **B.** Detect `Application` subclasses passed in
  `restResources` (via `Application.class.isAssignableFrom(rc)`),
  treat such a class as the application instead of just a
  resource, and read its `@ApplicationPath` to derive
  `rootPath`. No DRY problem.
- **C.** Both A and B — most flexible.

Recommended: B (or C if belt-and-suspenders). New scenario
covering the case: `@EnableJaxRs(restResources={DemoRestApp.class})`
where `DemoRestApp` carries `@ApplicationPath("demoRest")` and a
`@Path("/customers")` resource is reachable at
`<TestUrl>/demoRest/customers`.

## TICKET-014 follow-up — optional classpath scan for never-injected repositories

Context: G1 of the TICKET-014 POC comparison
(`tickets/014-poc-comparison.html`). Our `SpringDataRepositoryExtension`
discovers repository interfaces via two channels: `ProcessInjectionPoint`
(catches every `@Inject CustomerRepository` site anywhere in the
deployment) and a walk of `TestContext.getTestClass().getDeclaredFields()`
(catches repositories declared on the test class, which is typically
not a CDI bean itself). The POC additionally walks the classpath
eagerly at `AfterBeanDiscovery` time.

The only consumer shape our discovery does NOT catch: a repository
that is never written as an injection point and is only resolved
programmatically, e.g.
`CDI.current().select(CustomerRepository.class).get()`. No scenario
in our 14-scenario suite exercises this. The first-draft decision
was to accept the gap rather than reintroduce an eager classpath
scan (which the POC implemented with `cl.getResources("")` walking
`file:` URLs only — JARs silently skipped, errors silently swallowed,
every class on the classpath force-loaded).

If a consumer ever asks for the programmatic-lookup case, the
follow-up options (cheapest first):

- **Cheapest** — add an MP Config key
  `jawelte.spring-data.additional-repository-interfaces` taking a
  comma-separated list of repository FQCNs. The extension reads it
  during `AfterBeanDiscovery` and adds each named class to
  `discoveredRepositories`. Opt-in, no scanning, no classpath cost.
- **Feature flag-gated scan** — re-introduce a classpath walk
  behind `jawelte.spring-data.scan-classpath=true` (default `false`).
  Build it on top of `xbean-finder-shaded` (already on the test
  classpath via the cdi-module ecosystem) so JAR archives are
  scanned correctly and class-init side-effects are avoided. Add a
  scenario that injects via `CDI.current().select(...)` only,
  asserts the repository resolves.
- **Combination** — ship both, document the trade-off in the
  module javadoc.

Defer until a consumer asks. Add a scenario at the same time so
the contract is locked in.

## @DisableAutoMock annotation + MP Config equivalent

- Add a `@DisableAutoMock` annotation (cdi-module/api) that switches off cdi-module's auto-mock layer for the annotated test class or injection point, so consumers can opt out without going through the existing exclude-package config list.
- Provide a general MicroProfile Config option (e.g. `org.os890.jawelte.module.cdi.auto-mock.disabled=true`) that disables auto-mocking JVM-wide. Useful for scenarios that already declare every dependency via `@TestBean` and want the cleanest container possible.
- Both must compose with the existing `DefaultExcludedPackageFilter` / `framework-exclude-packages` infrastructure — annotation/config wins over the prefix list.

## Support `@TestBean` on methods

`@TestBean`'s current `@Target` is `{TYPE, ANNOTATION_TYPE, FIELD}`.
Add `METHOD` so a test class can declare a producer-method directly:

```java
@EnableTestBeans
class MyTest {
    @TestBean
    @Produces
    public static Greeting greeting() { return new Greeting("…"); }

    @Inject Greeting greeting;
    …
}
```

Today the only producer-method route is the producer-class indirection
(`@TestBean(beanProducer = ProducerClass.class)` — listings/08).
Direct-on-method placement would let users skip the extra class.

Touch points:
- `core/api/.../TestBean.java` — add `ElementType.METHOD` to `@Target`.
- `modules/cdi-module/impl/.../TestBeanScanner.java` — add a `collectStaticMethods`
  walker parallel to `collectStaticFields` so the scanner registers the
  method as a producer.
- `modules/cdi-module/impl/.../TestBeansCdiExtension.onAfterBeanDiscovery` —
  synthesise the producer-method bean (capture method handle, invoke at
  injection time, scope precedence matching the field-mode path).
- New regression scenario in `tests/cdi-module/` exercising the
  pattern.
- `docs/core.html` section 2.2 `@TestBean modes` — drop the "cannot be
  placed directly on a @Produces method" caveat, add a fourth bullet,
  and add a new listing demonstrating it.
