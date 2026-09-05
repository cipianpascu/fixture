# Olorin Spring REST Client

bfa-spring-library is a Spring Boot client library for configured backend
calls. Applications generate request and response model classes at build time;
the library has no SpringDoc, OpenAPI parser, or runtime schema validation.

## Install and bootstrap

    <dependency>
      <groupId>com.agent</groupId>
      <artifactId>bfa-spring-library</artifactId>
      <version>1.0.0-SNAPSHOT</version>
    </dependency>

Provide the already-loaded core configuration section:

    @Bean
    RestConfigurationProvider restConfigurationProvider(ConfigurationIF configuration) {
        return () -> configuration; // name: com.db.olorin.rest.configuration
    }

This is Spring Boot auto-configuration; no component scan of com.db.olorin.rest
is needed.

## Configuration

Keep the existing gateway.* item names in the one
com.db.olorin.rest.configuration section:

    {
      "name": "com.db.olorin.rest.configuration",
      "configurationItems": [
        {"name": "gateway.backends[0].name", "value": "product-backend"},
        {"name": "gateway.backends[0].baseUrl", "value": "https://products.example.com"},
        {"name": "gateway.backends[0].timeout", "value": "10s"},
        {"name": "gateway.backends[0].securityType", "value": "basic"},
        {"name": "gateway.backends[0].securityConfig.username", "value": "service-user"},
        {"name": "gateway.backends[0].securityConfig.password", "value": "secret"}
      ]
    }

### Backend settings

Every backend requires gateway.backends[n].name and gateway.backends[n].baseUrl.
Optional settings are gateway.backends[n].path, enabled (default true), timeout
(default 30s), http-version (http1_1 or http2), protocol (rest), tls-profile,
proxy.host, proxy.port, and proxy.non-proxy-hosts. Both `baseUrl` and
`base-url`, `securityType` and `security-type`, and `securityConfig.*` and
`security-config.*` are accepted. Backend and operation paths are joined with
one slash. Lists accept either a comma-separated value or indexed entries, for
example gateway.backends[0].proxy.non-proxy-hosts[0].

### Security settings

Set gateway.backends[n].securityType to one of the following values.

| Type | Required configuration | Result |
| --- | --- | --- |
| none | none | No security headers are added. |
| basic | securityConfig.username, securityConfig.password | Adds an Authorization Basic header. |
| cloudrun | optional securityConfig.audience | Adds X-Serverless-Authorization with a Google ID token. The backend base URL is the default audience. |
| jwt | auth-request and/or authz-request plus a referenced auth service | Retrieves configured tokens and maps them to backend headers. |
| form | securityConfig.form-params.* | Adds form fields inline, or calls the configured form auth service. |

For a JWT backend, auth-request.service defaults to auth and supports path,
sparte-gvo, btx, and pss. authz-request supports service, path,
branch-customer-number (literal:, header:, or cookie:), gvo-entitlements-list,
business-transactions, and service-shop-transactions. Token mappings use
securityConfig.bearer-source, bearer-header, bearer-prefix,
token-headers.<header>, and static-headers.<header>. Valid values for a token
source are glueToken, authZToken, customerAccessToken, authorizationToken,
glueAccessToken, eidpAccessToken, and authzCustomerAccessToken.

Referenced auth services live in the same section under gateway.<name>.*:
enabled, service-url, session-id-header, session-id-cookie, timeout,
tls-profile, security-type, security-config.*, cache.enabled,
cache.expiry-skew, and cache.max-size. Authz tokens are cached only when a
future JWT exp remains after expiry-skew.

For form security, securityConfig.service=inline modifies an
application/x-www-form-urlencoded body. Any other service name calls that
gateway auth service at securityConfig.auth-path (default /auth/form).
securityConfig.form-params.<field> accepts literal:, header:, or cookie:
sources; securityConfig.response-headers.<header> maps response fields, and
response-header-prefixes.<header> optionally prefixes the mapped value.

### TLS and outbound proxy settings

Define gateway.tls.profiles.<profile>.truststore.path, password, and type
(default PKCS12) for server trust. Define keystore.path, password, type, and
key-password for client certificates. Select the profile with backend
tls-profile. Outbound proxy configuration uses proxy.host, proxy.port, and
non-proxy-hosts; wildcard host suffixes are supported. HTTP version is selected
through http-version.

### Historization settings

Global history settings are gateway.history.enabled, provider (default
gcp-pubsub), delivery-mode (async or confirmed), fail-open, service-url,
project-id, topic, timeout, tls-profile, and executor.core-threads,
executor.max-threads, executor.queue-capacity. A backend may override enabled,
provider, delivery-mode, fail-open, service-url, project-id, topic, token-header,
additional-properties.*, and generated-headers.* under gateway.backends[n].history.

Additional-property values support literal:, header:, cookie:, token:, date:,
manifest:, and generator:uuid. Generated headers currently support
generator:uuid. The library does not replace an existing header; otherwise it
generates one UUID per logical request and shares it with submitted, fulfilled,
and failed history events.

## Configuration scenarios

Each example below is the `configurationItems` array inside the one
`com.db.olorin.rest.configuration` `ConfigurationIF` section. Replace values
and indexes to define further named backends.

### Public or internal backend with no auth

    [
      {"name":"gateway.backends[0].name","value":"catalog"},
      {"name":"gateway.backends[0].baseUrl","value":"https://catalog.example.com"},
      {"name":"gateway.backends[0].path","value":"/api/v1"},
      {"name":"gateway.backends[0].securityType","value":"none"},
      {"name":"gateway.backends[0].timeout","value":"10s"}
    ]

### Legacy backend with Basic auth and mTLS

    [
      {"name":"gateway.tls.profiles.legacy.truststore.path","value":"/secrets/legacy-ca.p12"},
      {"name":"gateway.tls.profiles.legacy.truststore.password","value":"changeit"},
      {"name":"gateway.tls.profiles.legacy.truststore.type","value":"PKCS12"},
      {"name":"gateway.tls.profiles.legacy.keystore.path","value":"/secrets/client.p12"},
      {"name":"gateway.tls.profiles.legacy.keystore.password","value":"changeit"},
      {"name":"gateway.tls.profiles.legacy.keystore.key-password","value":"changeit"},
      {"name":"gateway.backends[0].name","value":"legacy-orders"},
      {"name":"gateway.backends[0].baseUrl","value":"https://orders.internal"},
      {"name":"gateway.backends[0].tls-profile","value":"legacy"},
      {"name":"gateway.backends[0].securityType","value":"basic"},
      {"name":"gateway.backends[0].securityConfig.username","value":"service-user"},
      {"name":"gateway.backends[0].securityConfig.password","value":"secret"}
    ]

### Backend with outbound HTTP proxy

    [
      {"name":"gateway.backends[0].name","value":"partner"},
      {"name":"gateway.backends[0].baseUrl","value":"https://partner.example.com"},
      {"name":"gateway.backends[0].http-version","value":"http2"},
      {"name":"gateway.backends[0].proxy.host","value":"corp-proxy.internal"},
      {"name":"gateway.backends[0].proxy.port","value":"8080"},
      {"name":"gateway.backends[0].proxy.non-proxy-hosts[0]","value":"localhost"},
      {"name":"gateway.backends[0].proxy.non-proxy-hosts[1]","value":"*.svc.cluster.local"}
    ]

### Private Cloud Run backend

    [
      {"name":"gateway.backends[0].name","value":"recommendations"},
      {"name":"gateway.backends[0].baseUrl","value":"https://recommendations-abc-uc.a.run.app"},
      {"name":"gateway.backends[0].securityType","value":"cloudrun"},
      {"name":"gateway.backends[0].securityConfig.audience","value":"https://recommendations-abc-uc.a.run.app"}
    ]

### Forward an ASM/Istio request token and selected cookie

This is opt-in. The values come from the current Spring MVC request; explicit
`RestRequest` headers and cookies always take precedence.

    [
      {"name":"gateway.backends[0].name","value":"protected-backend"},
      {"name":"gateway.backends[0].baseUrl","value":"https://protected.example.com"},
      {"name":"gateway.backends[0].securityType","value":"none"},
      {"name":"gateway.backends[0].forward-headers[0]","value":"x-asm-rctoken"},
      {"name":"gateway.backends[0].forward-cookies[0]","value":"session"}
    ]

No value is forwarded when the client is called outside an active Spring MVC
request, such as from a scheduled job or message consumer.

### JWT backend using an auth service

    [
      {"name":"gateway.auth.enabled","value":"true"},
      {"name":"gateway.auth.service-url","value":"https://identity.example.com"},
      {"name":"gateway.auth.session-id-header","value":"X-Session-Id"},
      {"name":"gateway.backends[0].name","value":"customer"},
      {"name":"gateway.backends[0].baseUrl","value":"https://customer.example.com"},
      {"name":"gateway.backends[0].securityType","value":"jwt"},
      {"name":"gateway.backends[0].auth-request.service","value":"auth"},
      {"name":"gateway.backends[0].auth-request.path","value":"/auth/tokens/{sessionId}"},
      {"name":"gateway.backends[0].auth-request.btx[0]","value":"ReadCustomer"},
      {"name":"gateway.backends[0].securityConfig.token-headers.X-Glue-Token","value":"glueToken"}
    ]

### JWT authorization follow-up with cache

    [
      {"name":"gateway.authz.enabled","value":"true"},
      {"name":"gateway.authz.service-url","value":"https://identity.example.com"},
      {"name":"gateway.authz.cache.enabled","value":"true"},
      {"name":"gateway.authz.cache.expiry-skew","value":"30s"},
      {"name":"gateway.authz.cache.max-size","value":"10000"},
      {"name":"gateway.backends[0].name","value":"entitled-service"},
      {"name":"gateway.backends[0].baseUrl","value":"https://entitled.example.com"},
      {"name":"gateway.backends[0].securityType","value":"jwt"},
      {"name":"gateway.backends[0].authz-request.service","value":"authz"},
      {"name":"gateway.backends[0].authz-request.path","value":"/auth/authz/{sessionId}"},
      {"name":"gateway.backends[0].authz-request.branch-customer-number","value":"header:Branch-Customer-Number"},
      {"name":"gateway.backends[0].authz-request.service-shop-transactions[0]","value":"shop-a"},
      {"name":"gateway.backends[0].securityConfig.bearer-source","value":"authorizationToken"}
    ]

### Form auth service and inline form enrichment

Form-service mode calls a named auth service and maps fields from its JSON
response:

    [
      {"name":"gateway.auth.service-url","value":"https://identity.example.com"},
      {"name":"gateway.backends[0].name","value":"form-service"},
      {"name":"gateway.backends[0].baseUrl","value":"https://partner.example.com"},
      {"name":"gateway.backends[0].securityType","value":"form"},
      {"name":"gateway.backends[0].securityConfig.service","value":"auth"},
      {"name":"gateway.backends[0].securityConfig.auth-path","value":"/auth/form"},
      {"name":"gateway.backends[0].securityConfig.form-params.session","value":"header:X-Session-Id"},
      {"name":"gateway.backends[0].securityConfig.response-headers.Authorization","value":"accessToken"},
      {"name":"gateway.backends[0].securityConfig.response-header-prefixes.Authorization","value":"Bearer"}
    ]

Inline mode adds mapped values to the actual form-encoded request body:

    [
      {"name":"gateway.backends[0].name","value":"legacy-form"},
      {"name":"gateway.backends[0].baseUrl","value":"https://legacy.example.com"},
      {"name":"gateway.backends[0].securityType","value":"form"},
      {"name":"gateway.backends[0].securityConfig.service","value":"inline"},
      {"name":"gateway.backends[0].securityConfig.form-params.client_id","value":"literal:rest-client"},
      {"name":"gateway.backends[0].securityConfig.form-params.session","value":"cookie:session"}
    ]

### GCP Pub/Sub history with UUID correlation

    [
      {"name":"gateway.history.enabled","value":"true"},
      {"name":"gateway.history.provider","value":"gcp-pubsub"},
      {"name":"gateway.history.delivery-mode","value":"confirmed"},
      {"name":"gateway.history.fail-open","value":"false"},
      {"name":"gateway.history.project-id","value":"my-project"},
      {"name":"gateway.history.topic","value":"backend-history"},
      {"name":"gateway.backends[0].name","value":"orders"},
      {"name":"gateway.backends[0].baseUrl","value":"https://orders.example.com"},
      {"name":"gateway.backends[0].history.generated-headers.X-Request-Id","value":"generator:uuid"},
      {"name":"gateway.backends[0].history.additional-properties.requestId","value":"header:X-Request-Id||generator:uuid"},
      {"name":"gateway.backends[0].history.additional-properties.traceId","value":"header:X-Trace-Id"},
      {"name":"gateway.backends[0].history.additional-properties.eventTime","value":"date:iso-instant"}
    ]

## Typed calls

RestRequest carries generated input and output types; the public API has no
untyped Object request parameter.

    @Autowired
    @OlorinRestClient("product-backend")
    private TypedRestClient restClient;

    ProductResponse product = restClient.exchange(
        "product-backend",
        new RestRequest<>(
            HttpMethod.POST, "/products", new ProductRequest("book"),
            Map.of("X-Trace-Id", traceId), Map.of("include", "price"),
            ProductResponse.class
        )
    );

OlorinRestClient can also inject a generated-style interface. Bind dynamic
arguments explicitly; an unannotated single argument remains a body for
backward compatibility:

    interface ProductBackend {
        @RestOperation(method = "POST", path = "/products/{id}")
        ProductResponse create(@RestPath("id") String id,
            @RestQuery("include") String include,
            @RestHeader("X-Trace-Id") String traceId,
            @RestCookie("session") String session,
            @RestBody ProductRequest request);
    }

    @OlorinRestClient("product-backend")
    private ProductBackend productBackend;

RestPath, RestQuery, RestHeader, RestCookie, and RestBody are library
annotations designed for generated client interfaces. Void methods accept a
successful no-content response. TypedRestClient remains available for callers
that prefer explicit RestRequest construction, including repeated headers and
query parameters.

## Typed SOAP calls

Configure a SOAP backend with protocol, soap.version, and an optional action:

    [
      {"name":"gateway.backends[0].name","value":"customer-profile"},
      {"name":"gateway.backends[0].baseUrl","value":"https://customer.example.com"},
      {"name":"gateway.backends[0].path","value":"/soap/customer-profile"},
      {"name":"gateway.backends[0].protocol","value":"soap"},
      {"name":"gateway.backends[0].soap.version","value":"1.1"},
      {"name":"gateway.backends[0].soap.soap-action","value":"urn:GetCustomerProfile"}
    ]

Invoke JAXB-generated request and response classes through `TypedSoapClient`:

    CustomerProfileResponse response = typedSoapClient.exchange(
        "customer-profile", new GetCustomerProfile("321"),
        CustomerProfileResponse.class);

## Model generation

Generate models in the consuming application:

- OpenAPI: org.openapitools:openapi-generator-maven-plugin using
  generatorName=java and generateApis=false.
- JSON Schema: org.jsonschema2pojo:jsonschema2pojo-maven-plugin.

Use generated classes directly as RestRequest<I, O> types. No generated server
stubs are needed.

## History correlation

The UUID generator is shared by submitted and fulfilled events:

    {"name": "gateway.backends[0].history.generated-headers.X-Request-Id",
     "value": "generator:uuid"}
    {"name": "gateway.backends[0].history.additional-properties.correlationId",
     "value": "generator:uuid"}

It generates only if the outgoing header is absent.

## Operational behavior

The client retries transient upstream and auth-service failures twice with a
200 ms delay. Authentication, authorization, and configuration failures are
not retried. A backend circuit opens for five seconds after at least two
failures in a four-call window. HTTP 401 and 403 are exposed as authentication
and authorization failures; other backend/transport failures are exposed as
UpstreamProxyException.

## Tests as examples

- SpringTypedRestClientIntegrationTest: typed JSON, headers, query parameters,
  retry behavior, history lifecycle invocation, and a local HTTP server.
- OlorinRestClientInjectorTest: generated-style interface injection.
- RestConfigurationAdapterTest: ConfigurationIF and gateway.* mapping.
- FormAuthServiceTest: inline form-auth enrichment.
- HistoryUuidCorrelationTest: stable UUID pairing.
