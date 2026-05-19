# scope-module/deployment

Quarkus extension for jawelte's scope-module. Two `@BuildStep` methods:

  - `registerScopes` — `ContextRegistrationPhaseBuildItem` registers
    `@TestClassScoped` and `@TestMethodScoped` as ArC contexts with
    their respective `ContextCreator`s.
  - `registerScopedBeansAsBeans` — `AdditionalBeanBuildItem` adds
    every class annotated with `@TestClassScoped` / `@TestMethodScoped`
    on the index so ArC includes them in the bean archive.
  - `remapConfigBeanScope` — `AnnotationsTransformerBuildItem` that
    swaps `@ApplicationScoped` for `@TestClassScoped` on
    `@ConfigBean` classes (when no user-declared scope wins).

Skeleton only — see the `quarkus-full-poc` branch for the full
implementation under `modules/scope-module/deployment/`.
