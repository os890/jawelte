# Configuration

jawelte reads configuration through **MicroProfile Config**, so any MP Config source works. The
usual one in a test module is `src/test/resources/META-INF/microprofile-config.properties`;
system properties and environment variables override it by the standard MP Config ordering.

Everything here has a working default. Reach for a key only when the default is wrong for your
project — most test suites set none of them.

List-valued keys are comma-separated.

## Auto-mocking (`cdi-module`)

| Key | Effect |
| --- | --- |
| `org.os890.jawelte.module.cdi.auto-mock.default-scope` | scope given to generated mocks |
| `org.os890.jawelte.module.cdi.auto-mock.exclude-packages` | types in these packages are never auto-mocked |
| `org.os890.jawelte.module.cdi.auto-mock.exclude-owning-bean-packages` | injection points *declared by* beans in these packages are not auto-mocked |
| `org.os890.jawelte.module.cdi.auto-mock.fail-on-unmockable` | fail instead of continuing when a type cannot be mocked |
| `org.os890.jawelte.module.cdi.framework-allowlist.packages` | packages exempt from the framework veto |

Modules extend the exclude list for their own vendor internals via
`org.os890.jawelte.module.<jpa|jta|springdata>.auto-mock.framework-exclude-packages`. That is why
Hibernate, Narayana and Geronimo types do not turn into mocks.

## JPA

| Key | Effect |
| --- | --- |
| `org.os890.jawelte.module.jpa.persistence-property.<name>` | sets persistence property `<name>` on the unit — the general escape hatch, e.g. `...persistence-property.hibernate.show_sql=true` |
| `org.os890.jawelte.module.jpa.cleanup.exclude-tables` | tables to leave out of per-method cleanup |
| `org.os890.jawelte.module.jpa.entity-scan.whitelist.packages` | restrict entity discovery to these packages |
| `org.os890.jawelte.module.jpa.entity-scan.whitelist.patterns` | same, by pattern |
| `org.os890.jawelte.module.jpa.scan-exclude-packages` | packages to skip while scanning |
| `org.os890.jawelte.module.jpa.app-label` | label used in generated database names |

## JTA

| Key | Effect |
| --- | --- |
| `org.os890.jawelte.module.jta.default-tx-timeout-seconds` | default transaction timeout |
| `org.os890.jawelte.module.jta.xa-data-source-class` | override the XA data source class |
| `org.os890.jawelte.module.jta.vendor-veto.allowlist.packages` | vendor beans exempt from the veto |

## Data sources

| Key | Effect |
| --- | --- |
| `org.os890.jawelte.module.datasource.<definitionName>.url` | redirect a declared `@DataSourceDefinition`'s URL |

This is the key that lets a production `@DataSourceDefinition` stay untouched while the test run
points it at an in-memory database.

## Database migration

| Key | Effect |
| --- | --- |
| `org.os890.jawelte.module.dbmigration.cleanup.exclude-tables` | migration bookkeeping tables kept out of cleanup |

## Test data

| Key | Effect |
| --- | --- |
| `org.os890.jawelte.module.testcontrol.api.TestControl.base-path` | default base path for `@TestControl(testData = ...)` |
| `org.os890.jawelte.module.dbtestdata.api.DbDiff.ignore` | project-wide ignored columns |
| `org.os890.jawelte.module.dbtestdata.api.DbDiff.unordered-tables` | tables always compared as multisets |
| `org.os890.jawelte.module.dbtestdata.api.DbDiff.boolean-true` | extra literals treated as `true` |
| `org.os890.jawelte.module.dbtestdata.api.DbDiff.boolean-false` | extra literals treated as `false` |

## Content diff

| Key | Effect |
| --- | --- |
| `org.os890.jawelte.module.contentdiff.api.ContentDiff.json.ignore` | default ignored JSON paths |
| `org.os890.jawelte.module.contentdiff.api.ContentDiff.json.unordered-arrays` | JSON arrays always compared unordered |
| `org.os890.jawelte.module.contentdiff.api.ContentDiff.xml.ignore` | default ignored XML paths |

## Scope defaults

| Key | Effect |
| --- | --- |
| `org.os890.jawelte.module.jaxrs.test-url.default-scope` | scope of the injected `TestUrl` |
| `org.os890.jawelte.module.wiremock.registry.default-scope` | scope of the WireMock endpoint registry |
| `org.os890.jawelte.module.ejb.singleton.default-scope` | scope `@Singleton` is mapped to |

## EJB scanning

| Key | Effect |
| --- | --- |
| `org.os890.jawelte.module.ejb.bean-defining-annotations` | which annotations mark a session bean |
| `org.os890.jawelte.module.ejb.scan-exclude-packages` | packages to skip while scanning |

## Reading configuration from a test

`ConfigResolver` is injectable, so a test can read what the framework reads rather than going to
MicroProfile Config directly:

```java
@Inject
ConfigResolver configResolver;   // resolve(dotKey), resolveKeys(), resolveAliasKeysFor(logicalKey)
```

It resolves a dotted key and falls back to the underscore form of the same key, which is what
makes an environment variable a usable override for every key in this file. Supplying an
`@Alternative` replaces it wholesale.

## Replacing behaviour instead of configuring it

Every module is built on ports. When a key is not enough, provide an `@Alternative`
implementation of the port, or register one through `ServiceLoader` with a higher `@Priority` —
that is the supported extension route for every port. Highest priority wins; a tie stays
ambiguous and is reported as such.

| Area | Ports you can replace |
| --- | --- |
| core | `ConfigResolver`, `ConfigKeyAliasProvider`, `ServicePriorityResolver`, `BeanScopeMapper`, `TestBeanContainerPort`, `TestInstanceFactoryPort`, `TestModuleLifecyclePort` |
| cdi | `MockFactory`, `WhitelistFilter`, `ExcludedPackageFilter`, `CdiContainerPort` |
| jpa | `TransactionStrategy`, `EntityScanner`, `DbCleanupStrategy`, `TableNameResolver`, `PersistencePropertyResolver`, `PersistenceUnitConnectionResolver`, `CdiTransactionalSupportProvider` |
| jta / naming | `TransactionManagerProvider`, `JndiContextProvider`, `DataSourceFactory`, `ResourceLookup` |
| test data | `DbSeedEngine`, `DbDiffEngine`, `ELInterpolator`, `PersistenceUnitNameSupplier` |
| content / flow | `DiffEngine`, `JsonPatternDialect`, `XmlPatternDialect`, `FlowDialect`, `FlowDiffEngine`, `FlowRecordingPort` |
| ejb / batch | `EjbAnnotationMapper`, `EjbAnnotationScanner`, `TimeoutHandler` |

A module that supplies types of its own registers them in `SuppliedTypeRegistry`, which is what
keeps them out of auto-mocking.
