# Test data: seeding rows and asserting database state

Two modules. `db-testdata-module` is the engine (`DbSeed`, `DbDiff`); `testcontrol-module` wires
it to a test method declaratively via `@TestControl`. Both need `jpa-module`.

## Declarative: `@TestControl`

On a **test method**.

```java
@EnableTestBeans
@PersistenceConfig(persistenceUnitName = "customerPU")
class CustomerCountTest {

    @Inject
    CustomerCountService service;

    @Test
    @TestControl(testData = "testdata/customers", requireDbExpected = false)
    void countsSeededRows() {
        assertThat(service.countCustomers()).isEqualTo(2);
    }
}
```

| Attribute | Effect |
| --- | --- |
| `testData` | classpath base paths; each is read as `<path>/dbIn/*.xml` before the method and `<path>/dbExpected/*.xml` after it |
| `testDataBasePath` | a prefix applied to `testData` entries |
| `requireDbExpected` | defaults to **`true`** — a missing `dbExpected` directory fails the test. Set `false` for seed-only fixtures |
| `startScopes` | which scopes to activate for this method; also vetoes the ones not listed |

Layout on the test classpath:

```
src/test/resources/testdata/customers/
├── dbIn/customers.xml          seeded before the method
└── dbExpected/customers.xml    compared after the method
```

Both are DBUnit flat XML — element name is the table, attributes are the columns:

```xml
<dataset>
    <CUSTOMER ID="1" NAME="Alice"/>
    <CUSTOMER ID="2" NAME="Bob"/>
</dataset>
```

`@TestControl` is inherited from a superclass method, and an overriding method's own annotation
wins. A failing `beforeEach` does not leak seeded data, and a `dbExpected` mismatch still cleans
the database. With multiple persistence units each entry routes to its own unit; an unknown unit
name fails loudly.

## Programmatic: `DbSeed`

```java
DbSeed.forPersistenceUnit()           // the configured unit
DbSeed.forCurrentPersistenceUnit()    // the active one
DbSeed.forPersistenceUnit("customerPU")
DbSeed.forConnection(connection)      // caller owns the connection
```

then a dataset, a mode and `execute()`:

```java
DbSeed.forConnection(connection)
        .datasetContent(csv)
        .format("text/csv")           // default is "dbunit-xml"
        .cleanInsert()
        .execute();
```

Modes: `cleanInsert()` (delete then insert, foreign-key ordered, circular FKs handled),
`insert()`, `update()`, `refresh()`. Failures are prefixed `[DbSeed]`. Calling
`forPersistenceUnit()` outside an active unit throws `IllegalStateException` with
`No active persistence unit`.

## Programmatic: `DbDiff`

Same entry points, then an expectation and `assertEquals()`:

```java
DbDiff.forConnection(connection)
        .expected("testdata/expected/customers.xml")   // classpath resource
        .assertEquals();

DbDiff.forCurrentPersistenceUnit()
        .expectedContent("<dataset><CUSTOMER ID=\"1\" NAME=\"Alice\"/></dataset>")
        .assertEquals();
```

The failure message counts the differences and names each one, e.g.
`DB diff found 2 difference(s)` followed by `CUSTOMER[0]: unexpected row in database`.

Only the tables and columns named in the expectation are compared — it is a subset assertion, so
unrelated tables do not have to be listed.

### Cell matching rules

Markers go in the **expected** dataset's attribute values.

| Written | Means |
| --- | --- |
| `[NULL]` | SQL `NULL` (uppercase only — `null` and `Null` are literal strings) |
| `[MATCH:<regex>]` | regular-expression match, e.g. `CODE="[MATCH:[A-Z]+]"` |
| `uuid'<value>'` | a UUID literal |

Because the marker is the bracketed form, a value containing a tilde or any other punctuation
needs no escaping — `NICK="[MATCH:~literal]"` matches the literal `~literal`.

Booleans are normalised, so `true` / `1` / `Y` compare equal (the recognised sets are
configurable; an unrecognised value is compared literally). Numeric precision is respected.

Two builder methods relax the comparison:

```java
DbDiff.forConnection(connection)
        .expectedContent(expected)
        .ignoring("CUSTOMER.CREATED_AT")   // one column; "*.ID" ignores that column in every table
        .unorderedTables("CUSTOMER")       // compare rows as a multiset, ignoring order
        .assertEquals();
```

An ignored column is skipped entirely — a real difference in another column of the same row
still surfaces, reported as e.g. `CUSTOMER[0].NAME`.

### EL in fixtures

Both `DbSeed` and `DbDiff` interpolate EL expressions, resolving against explicitly supplied
values, CDI beans and static or instance functions. A supplied value overrides a bean of the
same name; a missing variable is an error rather than an empty string.
