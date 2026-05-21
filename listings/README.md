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
mvn -f core/01-hello-world/pom.xml test
```

Or copy the folder anywhere on your machine and run `mvn test` from
inside it.

## Running every listing for one module

```
mvn -f listings/jpa-module/pom.xml test
```

## Running every listing

```
mvn -f listings/pom.xml test
```

## Layout

Listings are grouped by module under `listings/<module>/`. Each
sub-folder has its own aggregator pom and numbers its listings locally
starting at `01`. Adding a new sample for an existing module is an
append inside that module's folder + an entry in its aggregator — no
global renumbering, no shifting of unrelated listings.

| Module | Listings | Documented in |
|---|---|---|
| `core/` | `01-hello-world`, `02-auto-mock`, `03-test-bean-alternative`, `04-test-bean-static-field`, `05-config-bean`, `06-limit-to-test-beans`, `07-external-container`, `08-test-bean-producer`, `09-test-bean-meta-annotation`, `10-test-context-metadata`, `11-before-scope-observer`, `12-container-started`, `13-lifecycle-port`, `14-bean-scope-mapper`, `15-config-resolver`, `16-priority-resolver`, `17-test-bean-qualified`, `18-config-bean-multi-key`, `19-test-instance-factory`, `20-config-key-alias-provider` | `docs/core.html` |
| `scope-module/` | `01-test-method-scoped`, `02-test-class-scoped`, `03-pre-destroy-callback`, `04-session-scoped-remap` | `docs/scope-module.html` |
| `jpa-module/` | `01-hello-world` | `docs/jpa-module.html` |
| `jta-module/` | `01-hello-world` | `docs/jta-module.html` |
| `ejb-module/` | `01-singleton` | `docs/ejb-module.html` |
| `jaxrs-module/` | `01-hello-world` | `docs/jaxrs-module.html` |
| `testcontrol-module/` | `01-hello-world` | `docs/testcontrol-module.html` |
| `db-testdata-module/` | `01-hello-world` | `docs/db-testdata-module.html` |
| `content-diff-module/` | `01-hello-world`, `02-mismatch`, `03-ignoring-pattern` | `docs/content-diff-module.html` |
| `wiremock-module/` | `01-hello-world` | `docs/wiremock-module.html` |
| `spring-data-module/` | `01-hello-world` | `docs/spring-data-module.html` |
| `batch-module/` | `01-hello-world` | `docs/batch-module.html` |

## CDI runtime

Every listing pins **OpenWebBeans** (`openwebbeans-se`) for a
deterministic build. Switching to Weld is a one-line change in the
listing's `pom.xml`: replace the `openwebbeans-se` dependency with
`org.jboss.weld.se:weld-se-shaded`.
