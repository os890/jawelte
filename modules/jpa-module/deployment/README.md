# jpa-module/deployment

Quarkus extension for jawelte's jpa-module. Single `@BuildStep`
registers `@ReadOnly` as an interceptor binding via
`InterceptorBindingRegistrarBuildItem` (the standalone
`JpaCdiExtension` does this at `BeforeBeanDiscovery` which doesn't
fire under `@QuarkusTest`).

Skeleton only — see the `quarkus-full-poc` branch for the full
implementation under `modules/jpa-module/deployment/`.

## What it does (sketch)

```java
@BuildStep
public InterceptorBindingRegistrarBuildItem registerReadOnlyBinding() {
    return new InterceptorBindingRegistrarBuildItem(new InterceptorBindingRegistrar() {
        @Override
        public List<InterceptorBinding> getAdditionalBindings() {
            return List.of(InterceptorBinding.of(READ_ONLY_DOT));
        }
    });
}
```

## Companion runtime-side work

`JpaArcContextContributor` (in `jpa-module/impl`) registers:

  - `@jakarta.transaction.Transactional` as an interceptor binding
    with `value` / `rollbackOn` / `dontRollbackOn` declared nonbinding
    (ArC doesn't auto-detect `@Nonbinding` on those members);
  - an `AnnotationTransformation` that strips those members from every
    `@Transactional` instance so the matcher fires regardless of
    TxType (jpa-module's interceptor treats every call as
    `REQUIRES_NEW` anyway);
  - the `@TransactionScoped` context via `addContextRegistrar` when
    the platform doesn't provide one.

The deployment artefact + the runtime contributor together replace
the legacy portable `JpaCdiExtension` under `@QuarkusTest`.
