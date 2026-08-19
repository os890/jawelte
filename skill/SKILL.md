---
name: jawelte
description: Write and debug integration tests with jawelte, a JUnit 6 test framework for Jakarta EE 11 applications that boots a real CDI SE container per test class. Use when a project depends on org.os890.jawelte artifacts, when a test class carries @EnableTestBeans / @EnableJaxRs / @EnableWireMock / @EnableFlowAssert / @TestControl, when adding tests for CDI beans, JPA persistence, JTA transactions, REST endpoints, WireMock stubs, Jakarta Batch jobs or Spring Data repositories in a Jakarta EE codebase, or when such a test fails to start its container.
---

# jawelte

A JUnit 6 framework for Jakarta EE 11. Each test class gets its own CDI SE container, and the
platform's own annotations — `@DataSourceDefinition`, `@Resource`, `@Transactional`,
`persistence.xml` — mean in a test what they mean in production, so application wiring runs
unchanged.

Everything beyond the core is opt-in: add the module for the technology under test and nothing
else. A module that is not on the classpath changes nothing, and most modules stay inert until
their entry-point annotation is used.

Current release: **0.3.0**. Artifacts live in a plain Maven repository served over GitHub Pages,
not Maven Central.

## Before writing any test, verify the four setup preconditions

Most "the container will not start" reports are one of these, not a bug in the test.

1. **The `<repositories>` entry exists in the consuming POM.** It is *not* inherited through a
   dependency's POM. Without it nothing resolves.
2. **`jawelte-core-impl` is an explicit dependency.** `jawelte-cdi-module-impl` brings
   `cdi-module-api` and `core-api` transitively but deliberately **not** `core-impl`. Missing it
   fails with `No TestBeansExtension found via ServiceLoader. Add core-impl to the test classpath.`
3. **`src/test/resources/META-INF/beans.xml` exists** with `bean-discovery-mode="annotated"`.
4. **`src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` contains
   `mock-maker-subclass`** — required whenever auto-mocking is used. Without it Mockito's inline
   maker cannot self-attach under a modern JDK inside surefire, refuses every type, and
   auto-mocking is silently off for that module.

The Jakarta APIs are `provided` in `jawelte-parent`, so they are **not** transitive either — a
consumer declares `jakarta.enterprise.cdi-api`, `jakarta.annotation-api`, `jakarta.inject-api`,
`microprofile-config-api` + `smallrye-config`, `mockito-core` and exactly one CDI runtime itself.

Full dependency blocks per module, the runtime choice and the **package of every public type** (they do not all follow the module name — `@EnableTestBeans` and `@TestBean` live in
`org.os890.jawelte.core.api`, not in the cdi-module package): **`references/setup.md`**.

## The core pattern

```java
import org.os890.jawelte.core.api.EnableTestBeans;   // note: core.api, not module.cdi.api

@EnableTestBeans
class GreetingTest {

    @Inject
    Greeter greeter;                 // the real bean under test

    @Test
    void greets() {
        assertThat(greeter.greet("world")).isEqualTo("hello world");
    }
}
```

`Greeter`'s own unsatisfied collaborators are auto-mocked. Beans and entities live in ordinary
top-level classes on the test classpath — the container discovers them normally.

To stub or verify a collaborator, declare it as a `@TestBean` static field rather than injecting
it into the test class:

```java
import org.os890.jawelte.core.api.TestBean;          // core.api as well

@EnableTestBeans
class GreetingTest {

    @TestBean
    static final AuditService AUDIT = mock(AuditService.class);

    @Inject
    Greeter greeter;

    @Test
    void greets() {
        when(AUDIT.audit("greet")).thenReturn("logged");
        assertThat(greeter.greet("world")).isEqualTo("hello world");
        verify(AUDIT).audit("greet");
    }
}
```

One mock is registered per unsatisfied type, shared by every injection point that needs it —
application beans and the test class alike — so injecting the collaborator into the test class as
well is fine and yields the same instance the bean received. A `@TestBean` static field is still
the clearer way to express "this is my stub", and the only way to supply a pre-configured one.

## Pick the module for what you are testing

Load the reference file only when that area is actually in play.

| Testing | Modules | Reference |
| --- | --- | --- |
| CDI beans, mocking, scopes, config beans | `cdi-module`, `scope-module` | `references/core-testing.md` |
| JPA, transactions, data sources, naming, migrations | `jpa-module`, `jta-module`, `datasource-module`, `jndi-module`, `resource-module`, `db-migration-module` | `references/persistence.md` |
| EJB session beans, Spring Data repositories, Batch jobs | `ejb-module`, `spring-data-module`, `batch-module` | `references/enterprise.md` |
| Seeding rows and asserting DB state | `db-testdata-module`, `testcontrol-module` | `references/test-data.md` |
| REST endpoints, HTTP stubs, JSON/XML comparison | `jaxrs-module`, `wiremock-module`, `content-diff-module` | `references/http-and-content.md` |
| Which bean called which, in what order | `flow-assert-module` | `references/flow-assert.md` |
| Tuning behaviour via MicroProfile Config | any | `references/configuration.md` |

## Rules that prevent the common failures

- **One entry-point annotation is enough.** `@EnableJaxRs`, `@EnableWireMock` and
  `@EnableFlowAssert` are meta-annotated with `@EnableTestBeans`; adding both is harmless but
  redundant.
- **Prefer the platform annotation over a framework one.** `@Transactional`, `@Resource`,
  `@DataSourceDefinition` and `persistence.xml` work as they do in production. Reach for a
  jawelte annotation only for something the platform has no answer for.
- **`@EnableTestBeans(limitToTestBeans = true)`** vetoes every discovered bean except `@TestBean`
  declarations and disables auto-mocking. Use it to prove a wiring is real, not as the default.
- **State does not leak between test classes** — each gets a fresh container. Within a class,
  `@TestClassScoped` survives across methods and `@TestMethodScoped` resets per method; both need
  `scope-module` on the classpath, otherwise the framework falls back to `@Dependent` /
  `@RequestScoped` cleanly.
- **Under `@QuarkusTest`, container management switches itself off** — Quarkus owns the
  container and jawelte will not boot a second one. See `references/core-testing.md`.
- **Never assert on timings, ports or generated IDs** unless the module fixes them
  (`@WireMockEndpoint(port = ...)` does; a random port does not).

## Verifying a change

Run the module's own tests with plain `mvn test`. There is no jawelte-specific runner, JVM
argument or surefire configuration — if a test needs one, that is a bug in the setup, not a
missing step.
