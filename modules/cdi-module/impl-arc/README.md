# cdi-module/impl-arc

ArC-backed companion to `cdi-module/impl`. Loaded under the
`-Pquarkus` profile (or whenever `io.quarkus.arc:arc-processor` is on
the test classpath); the SE-shaped `cdi-module/impl` stays the default
for OWB and Weld.

## What lives here

This is the skeleton package layout. The full implementation exists on
the `quarkus-full-poc` branch — these files are the cherry-pick
targets:

| file (target path under this module)                              | source on `quarkus-full-poc` |
| ----------------------------------------------------------------- | --------------------------- |
| `adapter/arc/ArcCdiContainerPort.java`                            | derived from `CdiTestBeanContainer` (the ArC-bootstrap half) |
| `adapter/quarkus/JaweltAutoMockBuildCompatibleExtension.java`     | `modules/cdi-module/impl/.../adapter/quarkus/JaweltAutoMockBuildCompatibleExtension.java` |
| `adapter/quarkus/MockSyntheticBeanCreator.java`                   | same package on POC |
| `adapter/quarkus/InlineFieldSyntheticBeanCreator.java`            | same package on POC |
| `adapter/quarkus/TestBeanInstanceSyntheticBeanCreator.java`       | same package on POC |
| `adapter/quarkus/TestBeanProducerMethodSyntheticBeanCreator.java` | same package on POC |
| `adapter/se/ArcSeContainerInitializer.java`                       | same package on POC |
| `adapter/se/ArcSeContainer.java`                                  | same package on POC |
| `adapter/se/ArcSeContainerView.java`                              | same package on POC |
| `spi/ArcContextContributor.java`                                  | `modules/cdi-module/impl/.../spi/ArcContextContributor.java` |
| `META-INF/services/jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension` | same path on POC |
| `META-INF/services/jakarta.enterprise.inject.se.SeContainerInitializer` | same path on POC |

## What stays in `cdi-module/impl`

OWB/Weld-shaped pieces:

  - `SeContainerCdiContainerPort` (uses `SeContainerInitializer`)
  - `TestBeansCdiExtension` (portable CDI extension)
  - `MockitoMockFactory`, `FrameworkAllowlist`, `InjectFieldsHelper`,
    `TestBeanScanner`, `SyntheticBeanUtil`

These keep working under OWB and Weld unchanged.

## Boundaries

`cdi-module/impl-arc` depends on `cdi-module/impl` (the existing
helpers in `util/`) and on `arc-processor` (Quarkus). It does NOT
depend on the Quarkus runtime — that's only needed under `@QuarkusTest`,
which Quarkus itself pulls in.
