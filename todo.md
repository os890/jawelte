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
