# Triple-runtime architecture: OWB / Weld / Quarkus

This document sketches the file/package layout that lets the same test
class run under three CDI runtimes:

  - **OpenWebBeans 4.x** (selected by `-Powb`, default on `main`)
  - **Weld 6.x** (selected by `-Pweld`)
  - **Quarkus ArC 3.x** (selected by `-Pquarkus`)

For Quarkus, a test author opts in by writing a thin **subclass** of an
existing test annotated with `@io.quarkus.test.junit.QuarkusTest`:

```java
// the existing test, runs on OWB/Weld unchanged
@EnableTestBeans
class ScopeStateResetTest { ... }

// the Quarkus companion, runs on ArC under the `-Pquarkus` profile
@QuarkusTest
class ScopeStateResetQuarkusTest extends ScopeStateResetTest {}
```

The subclass is the only opt-in the user writes. No annotation processor.
No Maven plugin. The framework's existing JUnit extension
(`DelegatingJUnitExtension`) detects `@QuarkusTest` on the active test
class and steps aside for what Quarkus owns (container boot/shutdown,
field injection, request-context activation) while still running the
module lifecycle hooks (testcontrol seed, jpa transaction, jta
adapter, scope activation, etc.).

## Why subclassing rather than annotation processor

A `*QuarkusTest extends *Test` subclass is **discoverable in IDE
navigation, grep, and git diff** with no build-step magic. Refactoring
the parent (rename a method, add an `@Inject` field) propagates to the
subclass via plain Java inheritance; the test author sees exactly what
the Quarkus runtime will execute. A code-generator would hide that
mapping behind a build phase and trade visibility for terseness.

The boilerplate is bounded — one ~6-line subclass per scenario you
want runnable under Quarkus. Scenarios that can't translate (see
"Non-translatable patterns" below) simply don't get a companion;
nothing is silently skipped.

## Module layout

```
core/
  api/                                # unchanged
    TestBeanContainerPort             # the container-impl SPI
    TestModuleLifecyclePort           # module hooks (testcontrol/jpa/…)
    TestContext / TestContextImpl
    EnableTestBeans                   # @Inherited (see below)
  impl/                               # unchanged
    DelegatingJUnitExtension          # already gates on @QuarkusTest

modules/
  cdi-module/
    api/                              # unchanged
      CdiContainerPort                # the existing container abstraction
      MockFactory / ExcludedPackageFilter / WhitelistFilter
    impl/                             # OWB/Weld backend
      SeContainerCdiContainerPort     # `SeContainerInitializer.newInstance()`
      TestBeansCdiExtension           # portable CDI extension
      …
    impl-arc/  ◀──── NEW              # Quarkus ArC backend
      ArcCdiContainerPort             # boots Arc directly OR no-ops under @QuarkusTest
      JaweltAutoMockBuildCompatibleExtension
      AnnotatedTypeBeanManagerWrapper
      adapter/quarkus/…
      spi/ArcContextContributor       # NEW SPI — see below
    deployment/  ◀──── NEW            # Quarkus extension @BuildSteps
      CdiModuleProcessor

  scope-module/
    impl/                             # OWB/Weld bits (unchanged)
    deployment/  ◀──── NEW
      ScopeModuleProcessor            # registers @TestClassScoped / @TestMethodScoped contexts

  jpa-module/
    impl/                             # adds JpaArcContextContributor (ArC side)
    deployment/  ◀──── NEW
      JpaModuleProcessor              # @ReadOnly + @Transactional binding registration

  jta-module/
    impl/                             # adds JtaArcContextContributor
    deployment/  ◀──── NEW (later)

  ejb-module/
    impl/                             # adds EjbArcContextContributor
                                      # (mapper-chain + stereotype fallback under ArC)

  wiremock-module/
    impl/                             # adds WireMockArcContextContributor

  spring-data-module/
                                      # adds SpringDataArcContextContributor

  testcontrol-module/
    impl/                             # adds DbTestDataArcContextContributor (already split)

  db-testdata-module/
    impl/                             # adds DbTestDataArcContextContributor

tests/
  {module}/
    scenario-NN-XXX/
      src/test/java/.../ScenarioNNTest.java          # the existing test
      src/test/java/.../ScenarioNNQuarkusTest.java   # `extends` companion (where applicable)
      src/test/resources/application.properties      # only if @QuarkusTest path needs it
```

## The new `ArcContextContributor` SPI

ArC dispatches only `BeforeBeanDiscovery` / `AfterBeanDiscovery` /
`AfterDeploymentValidation` to portable extensions (and even those
through the framework's reflective bridge in `CdiTestBeanContainer`).
It never invokes `ProcessAnnotatedType`. Every module that used a
portable extension for ProcessAnnotatedType (`EjbAnnotationExtension`,
`WireMockCdiExtension`, `JpaCdiExtension`, `SpringDataRepositoryExtension`)
needs an ArC-native counterpart.

```java
package org.os890.jawelte.module.cdi.impl.spi;

import io.quarkus.arc.processor.BeanProcessor;
import org.os890.jawelte.core.api.port.TestContext;

public interface ArcContextContributor {
    void contribute(TestContext testContext, BeanProcessor.Builder builder);
}
```

Each module ships an impl alongside its portable extension. The ArC
container loads them via `ServiceLoader` and calls them between
`BeforeBeanDiscovery` and `BeanProcessor.process()`. They:

  - register annotation transformations (replace `ProcessAnnotatedType.veto()`
    / `configureAnnotatedType().add(...)`),
  - register synthetic beans via `BeanRegistrar` (replace
    `AfterBeanDiscovery.addBean()`),
  - register interceptor bindings (replace
    `BeforeBeanDiscovery.addInterceptorBinding()`),
  - pre-register bean shapes on the framework's auto-mock BCE
    (`JaweltAutoMockBuildCompatibleExtension.preRegisterExistingBeanShape`)
    so the BCE doesn't duplicate beans the contributor already registered.

## Profile selection

Root `pom.xml` gains a `-Pquarkus` profile that:

  - swaps `jawelte-cdi-module-impl` for `jawelte-cdi-module-impl-arc`
    in `<dependencyManagement>` (or pulls both in and lets `ServiceLoader`
    rank by `@Priority`),
  - pulls each `jawelte-{module}-deployment` jar onto the test classpath,
  - configures surefire to include only `*QuarkusTest` classes (and
    exclude the OWB/Weld base classes which would re-run on the wrong
    runtime),
  - sets the `quarkus.index-dependency.*` properties globally so every
    jawelte jar is indexed for ArC bean discovery.

The default profile (`-Powb`) keeps `jawelte-cdi-module-impl` on the
classpath and runs the base test classes only.

## Required changes to existing code

1. **`@EnableTestBeans`**: add `@Inherited` (one-line meta-annotation). JUnit's
   `AnnotationSupport.findAnnotation` walks superclasses regardless, so the
   subclass already works; `@Inherited` makes the contract explicit and
   keeps reflective `Class.getAnnotation(...)` lookups (in the framework's
   own helpers) consistent.

2. **`DelegatingJUnitExtension`**: already gates `manageContainer` on
   `@QuarkusTest`. Extend the same check to `postProcessTestInstance` and
   `beforeEach` so field injection + request-context activation also step
   aside when Quarkus owns them. (See the `isQuarkusTest(testClass)`
   checks already added on `quarkus-full-poc`.)

3. **`CdiTestBeanContainer`**: wrap the injected `BeanManager` with a
   delegating JDK proxy that handles `createAnnotatedType(Class)` (ArC
   rejects that call) via a reflection-backed `AnnotatedType` stub.
   Carry over from `quarkus-full-poc`.

4. **`JaweltAutoMockBuildCompatibleExtension`**: new class in
   `cdi-module/impl-arc`. The `preRegisterExistingBeanShape` /
   `clearPreRegisteredBeanShapes` side-channel is the integration point
   for module-specific contributors. Carry over from `quarkus-full-poc`.

5. **Per-module deployment artefacts**: each is a small jar with
   `@BuildStep` methods registering `AnnotationsTransformerBuildItem` /
   `InterceptorBindingRegistrarBuildItem` / `BeanRegistrarBuildItem` /
   `ExcludedTypeBuildItem`. Carry over from `quarkus-full-poc`.

## Non-translatable patterns

These can't be expressed as `@QuarkusTest` subclasses; they stay
OWB/Weld-only:

  - **`@EnableTestBeans(manageContainer = false)` + user-driven
    `SeContainerInitializer`**: Quarkus owns the container; the test
    can't bootstrap one alongside.
  - **`EngineTestKit`-driven sub-test launchers**: `@QuarkusTest`'s
    `FacadeClassLoader` doesn't compose with `EngineTestKit.selectClass`
    on a sibling test class. Failure-mode scenarios that drive a subject
    class through `EngineTestKit` (testcontrol-08a, testcontrol-28) keep
    their existing form.
  - **Tests that exercise `BeanManager` methods ArC doesn't implement**:
    the framework wraps `createAnnotatedType` to make it work, but
    `addExtension` / `fireEvent` / a few others throw
    `UnsupportedOperationException` outright. The BCE / contributor
    SPI is the path forward — tests should use those rather than the
    legacy portable surface.

## Per-module status under `-Pquarkus`

The triple-runtime layout differentiates between modules whose Quarkus
support is *self-contained* (jawelte's own ArC plumbing covers
everything the test exercises) and those that require *first-party
Quarkus extensions* on the classpath because Quarkus already ships
the same primitives (EntityManager, JTA, JAX-RS, …) and would conflict
with a parallel jawelte registration.

  - **cdi-module**: self-contained. Every scenario runnable under
    `-Pquarkus` with the thin `*QuarkusTest extends *Test` companion;
    `cdi-module/deployment` is the only deployment artefact needed.
  - **scope-module**: self-contained. Same as cdi-module;
    `scope-module/deployment` registers `@TestClassScoped` and
    `@TestMethodScoped` with ArC's build pipeline.
  - **jpa-module, jta-module, db-testdata-module, spring-data-module**:
    require Quarkus first-party extensions
    (`quarkus-hibernate-orm`, `quarkus-narayana-jta`, `quarkus-jdbc-h2`,
    …) under `-Pquarkus`. EntityManager / UserTransaction come from
    Quarkus's own build steps; jawelte's
    `JpaArcContextContributor` no-ops (its synthetic-bean registrar
    backs off when a producer for the same type is already present).
    The thin subclass works, but each scenario's pom needs the
    `quarkus-*` deps in its `-Pquarkus`-specific dependency block.
    Not all scenarios translate — those that probe Hibernate
    internals via the standalone `PersistenceXmlParser` path don't
    have a one-line Quarkus equivalent.
  - **wiremock-module, ejb-module, testcontrol-module**: pending.
    Tests in these modules rely on lifecycle hooks that the
    contributor SPI implements; under `-Pquarkus` the contributors
    won't run, so the hooks need either a build-step bridge (like
    `JpaModuleProcessor`'s interceptor-binding registration) or a
    Quarkus-side-effect equivalent. Designed but not yet wired
    end-to-end on this branch.

`cdi-module/scenario-01` and `scope-module/scenario-01` are the
verified examples; remaining modules ship the impl-arc and (where
applicable) deployment artefacts so the *infrastructure* exists, but
each per-scenario `*QuarkusTest` companion is opt-in work as
described above.

## Working reference

The full implementation across all modules — every contributor, every
deployment artefact, every test-side `*QuarkusTest` subclass — exists
on the `quarkus-full-poc` branch with **408 / 408** scenarios green
under Quarkus. This branch (`triple-runtime-layout`) carries the
layout proposal and the minimum-surface scaffolding; cherry-picking
the POC's commits into the new module directories completes the
implementation.
