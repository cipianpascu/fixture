# Generated Client Interface Contract

The application supplies model classes and an interface. The library supplies runtime implementation through OlorinRestClientInjector.

```java
@OlorinRestClient("product-backend")
interface ProductBackend {
  @RestOperation(method = "POST", path = "/products/{productId}")
  ProductResponse update(
      @RestPath("productId") String productId,
      @RestQuery("dryRun") boolean dryRun,
      @RestHeader("X-Trace") String trace,
      @RestCookie("session") String session,
      @RestBody ProductRequest request);
}
```

- One parameter may be annotated RestBody. It is serialized with Jackson by Spring RestClient.
- RestPath values are URI-encoded during path expansion.
- RestQuery and RestHeader bind scalar generated-interface values. Repeated
  values are available through the explicit RestRequest additional parameter
  maps.
- RestCookie values are emitted in the Cookie header.
- A void return type accepts a successful no-content response. Other return types are deserialized by configured Spring message converters.
- Method-level operation metadata is required. Unannotated interface methods are rejected at injection time.
- Calls use the configured backend selected by OlorinRestClient.
