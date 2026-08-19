# HTTP: REST endpoints, stub servers, content comparison

## `jaxrs-module` — a real REST container

```java
@EnableJaxRs(restResources = {HelloResource.class})
class HelloResourceTest {

    @Inject
    TestUrl testUrl;                  // base URL of the embedded server

    @Test
    void getReturnsBody() {
        try (Client client = ClientBuilder.newClient();
             Response response = client.target(testUrl.get() + "/hello").request().get()) {

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.readEntity(String.class)).isEqualTo("hello");
        }
    }
}
```

`restResources` is mandatory and lists the resource classes to publish. `@EnableJaxRs` is
meta-annotated with `@EnableTestBeans`, so it boots the whole lifecycle on its own.

The server binds a **random port** — always build URLs from `TestUrl`, never hard-code one. It
starts before the test class and stops after it. Resource classes get CDI injection;
`@RequestScoped` beans are per HTTP request; a `@Dependent` resource is instantiated per request;
a plain (non-bean) resource still works through a reflective fallback. `@SessionScoped` is
remapped to `@TestMethodScoped`. Exception mappers are honoured, and an unknown path returns 404.

`ResponseDiff` reads the entity and hands it to the content-diff engine:

```java
ResponseDiff.forJson(response).expected("expected/customer.json").assertEquals();
ResponseDiff.forXml(response).expectedContent("<customer/>").assertEquals();
```

## `wiremock-module` — one stub server per endpoint

With no endpoint declared at all, the module boots one server on an OS-assigned port and an
unqualified `WireMockServer` injection resolves to it. To run several, or to pin a port,
declare a qualifier annotated with `@WireMockEndpoint`; the module starts one server per
qualifier and injects it.

```java
@WireMockEndpoint(port = 18081)
@Qualifier
@Retention(RUNTIME)
@Target({FIELD, METHOD, PARAMETER, TYPE})
public @interface PaymentApi {}
```

```java
@EnableWireMock
class PaymentClientTest {

    @Inject
    @PaymentApi
    WireMockServer paymentServer;

    @Test
    void stubsTheDownstream() {
        paymentServer.stubFor(get("/pay").willReturn(ok("{\"status\":\"ok\"}")));
        ...
    }
}
```

`@EnableWireMock` is meta-annotated with `@EnableTestBeans` and is `@Inherited`. Servers stop
after the test class, and **stubs reset between test methods** — register them per method.

Port rules: `port = <n>` binds that fixed port (a conflict fails loudly); omit it for a random
port and read the actual one from the injected server. With exactly one endpoint declared, an
unqualified injection resolves to it; with several, an unqualified injection is ambiguous unless
one endpoint has a higher `@Priority`. `WireMock` and `WireMockServer` injections for the same
qualifier share one endpoint, and `WireMockRuntimeInfo` is injectable qualified or unqualified.

Types the module supplies are not auto-mocked, and it works with or without `jaxrs-module`.

## `content-diff-module` — semantic JSON / XML comparison

Compares structure, not strings, so key order and formatting do not matter.

```java
ContentDiff.forJson(actualJson)
        .expected("expected/customer.json")     // classpath resource
        .ignoring("$.id", "$..createdAt", "$.items[*].id")
        .unorderedArrays("$.items")
        .assertEquals();

ContentDiff.forXml(actualXml)
        .expectedContent("<customer><name>Alice</name></customer>")
        .ignoring("/customer/version", "//timestamp")
        .assertEquals();
```

`expected(...)` and `expectedContent(...)` are mutually exclusive — using both throws.

- **Ignoring** uses JSONPath for JSON — `$.id` (a field), `$..createdAt` (recursive at any
  depth), `$.items[*].id` (a field of every array element), `$.*.createdAt` (single-segment
  wildcard) — and XPath-style paths for XML: `/orders/order/id` absolute, `//timestamp`
  recursive. XML attributes and elements are both addressable.
- JSON-only: `unorderedArrays(...)` compares an array as a multiset — `"$"` for the root array,
  `"$.items"` for a nested one. It is duplicate-sensitive.
- `null` versus missing is a real difference and is reported as one.
- EL expressions in the expected content are interpolated (no sandboxing — expected content is
  trusted input). A missing variable is an error.
- Malformed content on either side fails with a message saying so.
- Failures list every difference with its path, expected value, actual value and the line number
  in the expected resource; there is no cap on how many are reported.

Project-wide defaults for ignore patterns and unordered arrays can be set through MicroProfile
Config — see `configuration.md`.
