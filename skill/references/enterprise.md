# EJB, Spring Data and Jakarta Batch

Three modules that make an existing programming model work in a test rather than adding one of
their own. All three need `cdi-module`; Spring Data and most EJB usage also need `jpa-module`.

## EJB

`ejb-module` maps session beans onto CDI scopes. A bean written for a container is injectable as
it stands — no jawelte annotation on it, and none on the test beyond `@EnableTestBeans`:

```java
@Singleton
public class Greeter {
    public String greet(String name) {
        return "Hello, " + name;
    }
}
```

```java
@EnableTestBeans
class GreeterTest {

    @Inject
    Greeter greeter;                       // @Singleton resolved through the CDI container

    @Test
    void ejbSingletonIsInjectable() {
        assertThat(greeter.greet("world")).isEqualTo("Hello, world");
    }
}
```

`@Singleton` and `@Stateless` are mapped and get implicit transactionality — a business method
behaves as if it carried `@Transactional`. `@Stateless` keeps dependent semantics; `@Singleton`
shares state across injections within the test class.

Deliberately ignored, so do not write assertions on them: `@Stateful`, `@MessageDriven`,
`@TransactionAttribute`, `@Lock`, `@AccessTimeout`, `@Startup`. A user-declared scope or a
user-declared `@Transactional` on a session bean is preserved rather than overwritten.

Needs `jakarta.ejb-api` on the test classpath — it is `provided`, so it does not arrive on its
own.

## Spring Data

`spring-data-module` discovers repository interfaces and registers real Spring Data proxies. You
write the interface exactly as in production; nothing registers it explicitly.

```java
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findByName(String name);          // derived query
}
```

Repository calls need a transaction, so drive them through a bean rather than from the test
method:

```java
@ApplicationScoped
public class CustomerService {

    @Inject
    CustomerRepository customerRepository;

    @Transactional
    public void save(String name) {
        customerRepository.save(new Customer(name));
    }

    @Transactional
    public List<Customer> findByName(String name) {
        return customerRepository.findByName(name);
    }
}
```

```java
@EnableTestBeans
class CustomerRepositoryTest {

    @Inject
    CustomerService customerService;

    @Test
    void derivedQueryReturnsMatchingRows() {
        customerService.save("Alice");
        customerService.save("Bob");

        assertThat(customerService.findByName("Alice")).hasSize(1);
        assertThat(customerService.findByName("Eve")).isEmpty();
    }
}
```

Derived queries, `@Query` (JPQL and native), paging and sorting all work, and the injected
repository is a real proxy — not a mock. If you produce a repository yourself the module backs
off; with no repository interface present it does nothing.

Needs everything `jpa-module` needs, plus `org.springframework.data:spring-data-jpa` — it is
`provided`, so you declare it.

## Jakarta Batch

`batch-module` runs a job and fills in a typed result.

The job itself is plain Jakarta Batch, and the two pieces the platform requires are easy to forget
because nothing in jawelte replaces them — a **Job XML descriptor** at
`src/test/resources/META-INF/batch-jobs/<jobName>.xml`, whose `id` is the name you will fire, and
the artifact it references, resolved by CDI name:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<job id="importJob" xmlns="https://jakarta.ee/xml/ns/jakartaee" version="2.0">
    <step id="step1">
        <batchlet ref="importBatchlet"/>
    </step>
</job>
```

```java
@Dependent
@Named("importBatchlet")
public class ImportBatchlet extends AbstractBatchlet {

    @Override
    public String process() {
        return "DONE";
    }
}
```

**A job is started by firing `BatchExecution` as a CDI event**, not by calling a method on it —
the module observes the event, drives `JobOperator`, polls to a terminal state and writes the
outcome back into the instance you fired:

```java
@EnableTestBeans
class ImportJobTest {

    @Inject
    Event<BatchExecution> batchEvent;

    @Test
    void jobCompletes() {
        BatchExecution execution = new BatchExecution("importJob")
                .param("file", "customers.csv")
                .timeout(Duration.ofSeconds(30));

        batchEvent.fire(execution);                  // blocks until terminal or timeout

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getExecutionId()).isEqualTo(execution.getJobExecution().getExecutionId());
    }
}
```

After the fire, read the outcome from the same object: `getStatus()`, `getExitStatus()`,
`getExecutionId()` and `getJobExecution()`. Assert on `getStatus()` — it is the `BatchStatus`
enum and is the reliable signal. `getExitStatus()` is a free-form string that defaults to the
`BatchStatus` name only when the job does not set its own, so pin it only for a job you know
overrides it. Calling `getExecutionId()` **before** firing throws `IllegalStateException` — there
is no execution yet.

| | |
| --- | --- |
| Job name | the constructor argument; must be non-empty |
| Parameters | `param(key, value)`, accumulating; or a `Properties` via the second constructor |
| Timeout | `timeout(Duration)`, default 60s |
| Polling | exponential backoff from 50ms, capped at 5s |

A timeout **fails the assertion without cancelling the job** — the job keeps running in the
runtime, so a slow job produces a failure that says "not finished yet", not a cancellation. A
custom `TimeoutHandler` can be supplied as an `@Alternative`.

Needs `jakarta.batch:jakarta.batch-api` on the test classpath — it does **not** arrive with the
runtime — plus a runtime. `org.apache.batchee:batchee-jbatch` is what jawelte itself is verified
against and needs nothing further; JBeret also works but additionally wants
`jakarta.transaction-api` and `org.wildfly.core:wildfly-security-manager`, without which the
container dies on `NoClassDefFoundError` during startup.
