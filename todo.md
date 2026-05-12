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
