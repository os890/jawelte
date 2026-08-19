# Core testing: beans, mocking, scopes

## `@EnableTestBeans`

The entry point. Put it on the test class; each class gets its own CDI SE container, booted
before `beforeAll` and shut down after `afterAll`.

```java
@EnableTestBeans
class OrderServiceTest {

    @Inject
    OrderService orderService;      // real bean, discovered normally

    @Test
    void placesOrder() {
        assertThat(orderService.place("SKU-1")).isEqualTo("ok");
    }
}
```

Two attributes:

| Attribute | Default | Effect |
| --- | --- | --- |
| `limitToTestBeans` | `false` | `true` vetoes every discovered bean except `@TestBean` declarations plus framework internals, and disables auto-mocking |
| `manageContainer` | `true` | `false` skips the boot/shutdown calls; you start the container yourself (e.g. `SeContainerInitializer`). The other lifecycle callbacks still run |

The test class itself is not a CDI bean, but its fields are injected.

### Running under Quarkus

A test class annotated `io.quarkus.test.junit.QuarkusTest` or `QuarkusComponentTest` gets
`manageContainer = false` automatically — Quarkus already runs a bean container and jawelte must
not boot a second one. Container boot and shutdown are skipped; `postProcessTestInstance`,
`beforeEach` and `afterEach` still fire, so module ports and per-test setup behave as they do with
an externally managed container. Instance creation is left to Quarkus's own
`TestInstanceFactory`. Nothing has to be configured — the detection is by annotation name, so it
works whether or not Quarkus is on the classpath.

## Auto-mocking

Any injection point the container cannot satisfy gets a Mockito mock. Unstubbed methods return
`null` / the type default.

This requires `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` containing
`mock-maker-subclass`. Without it Mockito refuses every type and the framework reports the
refusal — it does not silently continue.

**A satisfied dependency is never mocked.** If a real `@ApplicationScoped` implementation is on
the classpath, it wins; auto-mock only fills genuine gaps.

**Deduplication.** Two application beans injecting the same unsatisfied type share one mock.

**One asymmetry to know about.** Candidates are keyed by type *plus qualifiers*, and the two
collection paths disagree on what a plain `@Inject` means. An injection point inside an
application bean is keyed from CDI's normalized qualifier set, where no declared qualifier counts
as `{@Default}`. The test class's own fields are keyed from the annotations physically written on
them, which for a plain `@Inject` is the empty set. The keys differ, so the same unsatisfied type
injected into both an application bean and the test class registers two `@Default` beans and the
deployment fails:

```java
// BROKEN — AmbiguousResolutionException (OWB) / WELD-001409 (Weld)
@EnableTestBeans
class GreetingTest {
    @Inject Greeter greeter;            // Greeter injects AuditService
    @Inject AuditService auditService;  // ...and so does the test class
}
```

An **explicitly qualified** injection is unaffected — both sides then carry the same qualifier
annotation, the keys match, and one shared mock is registered. Only the unqualified case diverges.

Use a `@TestBean` static field instead — it is also how you stub and verify:

```java
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

Auto-mocking covers interfaces, concrete classes, parameterized generics, `@Named` and custom
qualifiers, constructor and initializer-method injection, producer- and observer-method
parameters, and `Provider<T>` / `Instance<T>` unwrapping.

## `@TestBean`

Three forms, repeatable, and usable as a meta-annotation. Valid on the test class or any
superclass.

```java
@TestBean(bean = StubEmailService.class)          // activate an @Alternative bean class
@TestBean(beanProducer = StubProducer.class)      // activate an @Alternative producer class
```

```java
@TestBean                                          // register an existing instance as a singleton
static final Greeting GREETING = new Greeting("hello");
```

The static-field form must be `static` and non-null; an instance field or a `null` value is an
error. A class named by `bean` / `beanProducer` must actually carry `@Alternative`.

Repeat freely, or bundle several into one meta-annotation and put that on the test class:

```java
@TestBean(bean = StubEmailService.class)
@TestBean(bean = StubAuditService.class)
@Retention(RUNTIME)
@Target(TYPE)
public @interface WithStubs {}
```

## Scopes

`scope-module` adds two scopes. Without the module on the classpath the framework falls back to
`@Dependent` / `@RequestScoped` cleanly, so tests still run.

| Scope | Lifetime |
| --- | --- |
| `@TestClassScoped` | one instance per test class; `@PostConstruct` once, `@PreDestroy` after `afterAll` |
| `@TestMethodScoped` | fresh instance per test method; state resets between methods |

Both are normal scopes, both are thread-safe within their extent, and parallel test classes stay
isolated. A user-declared scope on a bean always wins over the framework's default mapping.

## `@ConfigBean`

A stereotype: `@ApplicationScoped` plus a marker. With `scope-module` present, `@ConfigBean`
types are remapped to `@TestClassScoped` so per-class configuration does not leak between
classes. A `@ConfigBean` that declares an explicit scope is left alone.

## Reading the container from a test

`TestContext` is the core's own handle — `TestContext.loadService(SomeSpi.class)` performs the
prioritized SPI lookup the modules use. Application code should use plain CDI injection; reach
for `TestContext` only when writing a module or an SPI adapter.

## Lifecycle events

Core fires CDI events that modules and tests can observe: `ContainerStarted`,
`BeforeScopeStarted` (vetoable — observe and veto to suppress a scope activation) and
`AfterTestTransaction`.
