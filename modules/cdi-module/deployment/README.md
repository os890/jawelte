# cdi-module/deployment

Quarkus extension for jawelte's cdi-module. Discovered through
`META-INF/quarkus-extension.properties` (deployment-side) +
`META-INF/quarkus-extension.yaml` (runtime metadata) and contributes
`@BuildStep` methods that:

  - register `@TestBean` / `@TestBeans` as bean-defining annotations,
  - register the auto-mock BCE,
  - emit `ExcludedTypeBuildItem` for the `@EnableTestBeans.limitToTestBeans`
    filtering,
  - register `ContainerStarted` bridge bean.

Skeleton only — see the `quarkus-full-poc` branch for the full
implementation under `modules/cdi-module/deployment/`.
