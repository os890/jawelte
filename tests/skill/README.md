# tests/skill

Scenarios backing the statements in `skill/` — the agent-facing documentation of how to consume
jawelte.

Only statements that nothing else already covers live here. A statement that belongs to one
module is verified by a scenario in that module's own test tree instead, which is why this module
holds two scenarios rather than a copy of the skill.

| Scenario | Backs |
| --- | --- |
| `scenario-01-consumer-dependency-contract` | the dependency table in `references/setup.md` — which artifacts a consumer declares and which arrive transitively, and the deliberate absence of a `cdi-module/impl` → `core/impl` edge |
| `scenario-02-documented-core-pattern` | the two worked examples in `SKILL.md`, run as written |

## Where the rest is verified

| Statement | Scenario |
| --- | --- |
| `Add core-impl to the test classpath` / `Multiple TestBeansExtension` | `tests/core/scenario-proxy-no-provider`, `scenario-proxy-multi-provider` |
| A mock refusal is reported rather than swallowed (the mock-maker precondition) | `tests/cdi-module/scenario-62` |
| An unsatisfied injection point is auto-mocked and answers `null` | `tests/cdi-module/scenario-01` |
| A satisfied dependency is never mocked | `tests/cdi-module/scenario-12` |
| One auto-mock is shared by every application bean that needs it | `tests/cdi-module/scenario-63` |
| One auto-mock is shared by an application bean and the test class | `tests/cdi-module/scenario-64` |
| A `@TestBean` static mock can be stubbed and verified | `tests/cdi-module/scenario-65` |
| `@TestBean` on a superclass applies to the subclass | `tests/cdi-module/scenario-66` |
| `@TestBean` forms: bean, producer, static field, repeatable, meta-annotation, misuse | `tests/cdi-module/scenario-13` … `-17`, `-27` … `-29`, `-35` |
| `limitToTestBeans` disables auto-mocking | `tests/cdi-module/scenario-18`, `tests/spring-data-module/scenario-11` |
| `manageContainer = false` | `tests/cdi-module/scenario-32` |
| The test class is not a CDI bean but its fields are injected | `tests/cdi-module/scenario-30`, `-31` |
| Containers do not leak between test classes | `tests/cdi-module/scenario-20` |
| `@TestClassScoped` / `@TestMethodScoped` lifetimes, and the fallback without scope-module | `tests/scope-module/scenario-01`, `-12`, `-27` |
| `@ConfigBean` remapped to `@TestClassScoped` | `tests/scope-module/scenario-28` … `-30` |
| An entry-point annotation boots the lifecycle on its own | `tests/jaxrs-module/scenario-12`, `tests/wiremock-module/scenario-15`, `tests/flow-assert-module/scenario-01` |
| `TestUrl` and the random port | `tests/jaxrs-module/scenario-01`, `-02` |
| WireMock fixed ports, random ports, stub reset, qualifier resolution | `tests/wiremock-module/scenario-03` … `-05`, `-08`, `-09`, `-24`, `-25` |
| `requireDbExpected` defaults to `true` | `tests/testcontrol-module/scenario-28` |
| `DbDiff` cell markers `[NULL]`, `[MATCH:…]`, `uuid'…'`, ignoring, unordered tables | `tests/db-testdata-module/scenario-14` … `-20`, `-26` … `-28` |
| Content-diff path syntax and semantics | `tests/content-diff-module/scenario-04` … `-06`, `-10`, `-16`, `-17`, `-29` |
| Transaction outcome rules, `@ReadOnly`, nesting | `tests/jpa-module/scenario-08` … `-17`, `tests/jta-module/*` |
| Batch timeout, backoff and fluent builder | `tests/batch-module/scenario-03`, `-05`, `-10`, `-11`, `-13` |
| `@QuarkusTest` switches container management off | `tests/core/scenario-quarkus-auto-skip` |
| JPA transaction events on commit and rollback | `tests/jpa-module/scenario-38`, `-39`, `-40` |
| `ConfigResolver` is injectable and replaceable | `tests/cdi-module/scenario-48`, `-49` |
| Dotted key with underscore fallback, alias aggregation | `tests/core/scenario-config-01` … `-08` |
| Ports resolve by priority, ties stay ambiguous | `tests/cdi-module/scenario-44`, `-45`, `tests/core/scenario-23` |

## Not machine-verified

Two setup preconditions cannot be asserted from inside the reactor, where every artifact is
already present and every scenario ships the files:

- the `<repositories>` entry a consuming POM needs
- the presence of `META-INF/beans.xml` in a consuming project

Both were checked by hand against the published 0.2.0 artifacts, by building a consumer project
outside this repository from an empty local repository.
