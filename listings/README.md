# jawelte — documentation listings

Every code block in [`docs/core.html`](../docs/core.html) (and, later, the
per-module docs) is backed by a runnable Maven project under this
folder. One subdirectory per listing, each containing the dependencies
it needs and a JUnit test that exercises the snippet end-to-end.

The intent is simple: **you can copy any single listing folder out of
this repository, change nothing, and run `mvn test` on it.** Listings
do not inherit from the jawelte root parent and do not reference
anything outside their own folder; the optional `listings/pom.xml`
beside this file is a thin aggregator for our own
convenience (`mvn -f listings/pom.xml test` runs all listings in one
sweep) — child listings do not declare it as a parent.

## One-time setup

Listings reference jawelte at version `0.1.0-SNAPSHOT`. Until that
version is published, install the snapshots into your local Maven
repository once from the repository root:

```
mvn -DskipTests install
```

After that, each listing builds on its own without the rest of the
project on the classpath.

## Running a single listing

From this folder:

```
mvn -f 01-hello-world/pom.xml test
```

Or copy the folder anywhere on your machine and run `mvn test` from
inside it.

## Running every listing

```
mvn -f listings/pom.xml test
```

## What's here

| # | Folder | Documented in `docs/core.html` |
|---|---|---|
| 01 | `01-hello-world` | Quick-start &rarr; hello-world |
| 02 | `02-auto-mock` | Quick-start &rarr; auto-mocked dependency |
| 03 | `03-test-bean-alternative` | Quick-start &rarr; `@TestBean` alternative |
| 04 | `04-test-bean-static-field` | Quick-start &rarr; `@TestBean` static field |
| 05 | `05-config-bean` | Quick-start &rarr; `@ConfigBean` |
| 06 | `06-limit-to-test-beans` | Detailed &rarr; `@EnableTestBeans(limitToTestBeans=true)` |
| 07 | `07-external-container` | Detailed &rarr; `manageContainer=false` |
| 08 | `08-test-bean-producer` | Detailed &rarr; `@TestBean(beanProducer=…)` |
| 09 | `09-test-bean-meta-annotation` | Detailed &rarr; `@TestBean` on a meta-annotation |
| 10 | `10-test-context-metadata` | Detailed &rarr; `TestContext` metadata |
| 11 | `11-before-scope-observer` | Detailed &rarr; observing `BeforeScopeStarted` |
| 12 | `12-container-started` | Detailed &rarr; observing `ContainerStarted` |
| 13 | `13-lifecycle-port` | SPI &rarr; `TestModuleLifecyclePort` |
| 14 | `14-bean-scope-mapper` | SPI &rarr; `BeanScopeMapper` |
| 15 | `15-config-resolver` | SPI &rarr; `ConfigResolver` |
| 16 | `16-priority-resolver` | SPI &rarr; `ServicePriorityResolver` |
| 17 | `17-test-method-scoped` | scope-module &rarr; `@TestMethodScoped` |
| 18 | `18-test-class-scoped` | scope-module &rarr; `@TestClassScoped` |
| 19 | `19-jpa-hello-world` | jpa-module &rarr; `@Inject EntityManager` hello-world |
| 20 | `20-jta-hello-world` | jta-module &rarr; JTA strategy auto-active |
| 21 | `21-ejb-singleton` | ejb-module &rarr; `@jakarta.ejb.Singleton` injectable |
| 22 | `22-jaxrs-hello-world` | jaxrs-module &rarr; `@EnableJaxRs` + `TestUrl` |
| 23 | `23-testcontrol-hello-world` | testcontrol-module &rarr; `@TestControl(testData)` seed pipeline |

## CDI runtime

Every listing pins **OpenWebBeans** (`openwebbeans-se`) for a
deterministic build. Switching to Weld is a one-line change in the
listing's `pom.xml`: replace the `openwebbeans-se` dependency with
`org.jboss.weld.se:weld-se-shaded`.
