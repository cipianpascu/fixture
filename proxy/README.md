# Backend Gateway Proxy

Quarkus-based HTTP proxy for routing agent traffic to multiple upstream backends with request validation, backend-specific authentication, and outbound TLS support.

## What This Module Does

- Routes requests from `/api/v1/{backend-name}/...` to configured upstream services
- Validates request path, method, and optional JSON body against configured OpenAPI schemas
- Applies backend-specific auth before forwarding
- Demonstrates application-owned orchestration resources built on top of the shared library
- Supports outbound TLS truststores and mTLS client certificates
- Supports Cloud Run service-to-service IAM auth for backends and for the auth service itself
- Can emit custom history events for mutating backend calls
- Exposes Quarkus health and Prometheus endpoints

This module is file-configured only. There is no database and no admin API.

## Request Flow

1. Generic proxy requests hit `ProxyResource`, resolve a backend from `gateway.backends`, validate the request, enrich auth headers, and forward to the upstream backend
2. Concrete application resources can orchestrate one or more backend calls, validate against their own published contract, and compose a new response instead of returning an upstream payload directly

Main implementation points:

- Routing entrypoint: [src/main/java/com/agent/gateway/proxy/app/resource/ProxyResource.java](./src/main/java/com/agent/gateway/proxy/app/resource/ProxyResource.java)
- App-specific orchestration example: [src/main/java/com/agent/gateway/proxy/app/resource/OrderSummaryResource.java](./src/main/java/com/agent/gateway/proxy/app/resource/OrderSummaryResource.java)
- Shared forwarding, validation, auth, TLS, and OpenAPI support now live in the `bfa-library` module

Current orchestration examples:

- `GET /api/v1/order-summaries/{id}`: fan-out aggregation over orders and payments backends
- `POST /api/v1/order-summaries/{id}/compose`: forwards selected incoming headers, uses request-body attributes to shape downstream headers and query params, and injects fields from the first backend response into the second backend request

## Auth Modes

Per backend, `securityType` can be:

- `none`: no auth enrichment
- `basic`: injects HTTP Basic credentials from `securityConfig.username/password`
- `jwt`: calls the configured auth service and maps returned tokens to backend-specific headers
- `form`: supports either a form-encoded auth-service pre-call or inline form parameter enrichment on the actual backend request
- `cloudrun`: generates a Google ID token and sends it as `X-Serverless-Authorization`

Auth-capable service calls are configured separately under named `gateway.<service>` blocks. Typical profiles are `gateway.auth` and `gateway.authz`. A service profile can also use Cloud Run IAM auth with:

- `gateway.auth.security-type=cloudrun`
- `gateway.auth.security-config.audience=...`

## TLS Model

`tls-profile` is transport-level configuration, not application auth.

Use `gateway.tls.profiles` to define reusable outbound TLS settings:

- `truststore`: trust private or internal CAs
- `keystore`: present a client certificate for mTLS

Then reference a profile from:

- `gateway.auth.tls-profile`
- `gateway.authz.tls-profile`
- `gateway.backends[n].tls-profile`

Typical usage:

- Cloud Run upstream with normal public HTTPS: usually no `tls-profile`
- Legacy/private backend with private CA or mTLS: use `tls-profile`
- Private Cloud Run upstream: use `securityType: cloudrun` or `gateway.auth.security-type: cloudrun`

## Configuration

The runtime config lives in [src/main/resources/application.yml](./src/main/resources/application.yml).

Minimal shape:

```yaml
gateway:
  auth:
    enabled: true
    service-url: http://localhost:8081

  authz:
    enabled: true
    service-url: http://localhost:8082

  schemas:
    directory: classpath:schemas/
    validate-requests: true
    validate-bodies: true
    strict-mode: true

  backends:
    - name: example-service
      baseUrl: http://localhost:9001
      path: /api/v1/example
      schema: example-service.yaml
      enabled: true
      securityType: jwt
      auth-request:
        sparte-gvo:
          - a
          - b
        btx:
          - FirstFunction
        pss:
          - SecondFunction
```

### History Events

History emission is disabled by default. When enabled, `ProxyService` and `SoapBackendService` emit events after auth/security enrichment and before the backend call for configured mutating methods.

```yaml
gateway:
  history:
    enabled: true
    provider: gcp-pubsub
    delivery-mode: async
    fail-open: true
    methods: [POST, PUT, PATCH, DELETE]
    project-id: ${GCP_PROJECT_ID}
    topic: backend-history
    executor:
      core-threads: 2
      max-threads: 8
      queue-capacity: 1000

  backends:
    - name: orders
      baseUrl: https://orders.example.com
      path: /api
      schema: orders.yaml
      securityType: jwt
      history:
        additional-properties:
          source: literal:bfa
          traceId: header:X-Trace-Id
          sessionId: cookie:SESSION
          customerId: token:partner_id|c_partner_id
          eventDate: "date: yyyy-MM-dd"
          eventTimestamp: date:timestamp
          appVersion: manifest:Implementation-Version

    - name: read-only
      baseUrl: https://readonly.example.com
      path: /api
      schema: readonly.yaml
      securityType: none
      history:
        enabled: false
```

Applications provide the event body by implementing `HistoryPayloadMapper`. The mapper receives `HistoryRequestContext`, including the backend, incoming request, inbound/outbound body, outbound headers, and resolved `additionalProperties`. `date:` values are generated in UTC and support `timestamp`, `epoch-second`, `iso-instant`, or Java date/time patterns. `manifest:` values read classpath manifest attributes, for example `manifest:Implementation-Version`.

Async delivery uses a bounded executor. `executor.queue-capacity: 0` disables queueing and applies backpressure immediately when all workers are busy.

Delivery behavior:

| delivery-mode | fail-open | Behavior |
| --- | --- | --- |
| `async` | `true` | Submit publish task and continue; local submit failure is logged. |
| `async` | `false` | Submit publish task and continue; local submit failure aborts the backend call. Pub/Sub ack is not awaited. |
| `confirmed` | `true` | Wait for publish result; publish failure is logged and the backend call continues. |
| `confirmed` | `false` | Wait for publish result; publish failure aborts the backend call. |

### Backend Fields

- `name`: backend identifier used in `/api/v1/{name}/...`
- `baseUrl`: upstream host
- `path`: upstream base path prefix
- `schema`: OpenAPI schema filename under `schemas/`
- `timeout`: request timeout for upstream call
- `enabled`: whether the backend is routable
- `http-version`: `http1_1` by default, optionally `http2` for known-good upstreams
- `securityType`: `none`, `basic`, `jwt`, `form`, or `cloudrun`
- `securityConfig`: auth-specific key/value config
- `auth-request`: structured request payload sent to the selected auth service for `jwt`
- `history`: optional per-backend history overrides and additional property extraction
- `tls-profile`: optional outbound TLS profile name
- `proxy`: optional outbound HTTP proxy for this backend only

Backend proxy shape:

```yaml
proxy:
  host: corp-proxy.internal
  port: 8080
  non-proxy-hosts:
    - localhost
    - 127.0.0.1
    - "*.svc.cluster.local"
```

Notes:

- backend proxy settings affect only calls from the proxy to that backend
- they do not affect calls to `gateway.<service>.service-url`, which use the selected service profile transport configuration instead
- `non-proxy-hosts` supports exact hosts and `*.` suffix patterns
- keep `http-version: http1_1` for plain `http://` upstreams
- use `http-version: http2` only for upstreams that are known to support it correctly, typically over `https://`

For `securityType: jwt`, `securityConfig` supports:

- `bearer-source`: one of `customer_access_token`, `auth_z_token`, or `glue_token`
- `bearer-header`: outbound bearer header name, defaults to `Authorization`
- `bearer-prefix`: bearer prefix, defaults to `Bearer`
- `token-headers.<Header-Name>`: maps an outbound header to one token field
- `static-headers.<Header-Name>`: adds a fixed outbound header such as an API key

If no explicit bearer or token-header mapping is configured, the runtime preserves the legacy default JWT header injection behavior (`X-Glue-Token`, `X-Auth-Z-Token`, `X-Customer-Access-Token`).

JWT auth can also optionally perform an authorization follow-up call through `authz-request`.

Additional JWT token sources from authz are:

- `authorization_token`
- `eidp_access_token`
- `authz_customer_access_token`

For `securityType: jwt`, `auth-request` fields are independently optional:

- `service`: named `gateway.<service>` profile to use for the auth call; defaults to `auth`
- `path`: auth service path override; supports `{sessionId}` placeholder and defaults to `/auth/tokens/{sessionId}`
- `sparte-gvo`
- `btx`
- `pss`

Only configure the lists required by the target auth flow. Omitted fields are not sent to the auth service.

JWT auth requests use the `gateway.<service>.service-url` configured by `auth-request.service`, or `gateway.auth.service-url` when `service` is omitted. `auth-request.path` only overrides the relative path used for that backend’s auth call.

For `securityType: jwt`, `authz-request` fields are also optional and can be used independently or together with `auth-request`:

- `service`: named `gateway.<service>` profile to use for the authz call; defaults to `auth`
- `path`: authz service path; supports `{sessionId}` placeholder
- `branch-customer-number`: branch/customer number mapping using a plain literal, `literal:<value>`, `header:<Header-Name>`, or `cookie:<Cookie-Name>`
- `gvo-entitlements-list`: EIDP authz list
- `business-transactions`: CIAM authz list
- `service-shop-transactions`: authz list used by both flows

If `authz-request` is configured, the proxy performs a second authz call and makes the returned tokens available through the same JWT header-mapping mechanism.

Example EIDP authz flow:

```yaml
gateway:
  authz:
    service-url: https://partner-authz.example.com
    security-type: cloudrun
    security-config:
      audience: https://partner-authz.example.com
  backends:
    - name: jwt-authz-eidp-service
      baseUrl: https://partner.example.com
      path: /api
      schema: partner.yaml
      securityType: jwt
      authz-request:
        service: authz
        path: /auth/authz/eidp/{sessionId}
        branch-customer-number: header:Branch-Customer-Number
        gvo-entitlements-list:
          - entitlement-a
        service-shop-transactions:
          - shop-a
      securityConfig:
        token-headers.X-Authorization-Token: authorization_token
        token-headers.X-Eidp-Access-Token: eidp_access_token
```

Example CIAM authz flow:

```yaml
gateway:
  authz:
    service-url: https://partner-authz.example.com
    security-type: cloudrun
    security-config:
      audience: https://partner-authz.example.com
  backends:
    - name: jwt-authz-ciam-service
      baseUrl: https://partner.example.com
      path: /api
      schema: partner.yaml
      securityType: jwt
      authz-request:
        service: authz
        path: /auth/authz/ciam/{sessionId}
        branch-customer-number: header:Branch-Customer-Number
        business-transactions:
          - business-a
        service-shop-transactions:
          - shop-b
      securityConfig:
        bearer-source: authz_customer_access_token
```

For `securityType: form`, `securityConfig` supports:

- `service`: optional; defaults to `inline`. `inline` performs inline form enrichment, while any other value uses the configured `gateway.auth` service
- `auth-path`: auth-service path to call, defaults to `/auth/form`
- `form-params.<field>`: maps a form parameter from an incoming source
- `response-headers.<Header-Name>`: maps an auth response field into an outbound backend header
- `response-header-prefixes.<Header-Name>`: optional prefix added before a mapped header value

Supported `form-params.*` mapping sources:

- `header:<Header-Name>`: read from an incoming request header
- `cookie:<Cookie-Name>`: read from an incoming request cookie
- `literal:<value>`: use a fixed literal value

### Auth Service Fields

- `service-url`: URL used for JWT token retrieval
- `session-id-header`: incoming header to read the session id from
- `session-id-cookie`: fallback cookie to read the session id from
- `timeout`: auth service timeout
- `security-type`: optional auth for calling the auth service itself
- `security-config`: config for `security-type`
- `tls-profile`: optional outbound TLS profile name

### TLS Profile Fields

```yaml
gateway:
  tls:
    profiles:
      partner-mtls:
        truststore:
          path: /secrets/truststore.p12
          password: ${TRUSTSTORE_PASSWORD}
          type: PKCS12
        keystore:
          path: /secrets/client-keystore.p12
          password: ${KEYSTORE_PASSWORD}
          key-password: ${KEY_PASSWORD:${KEYSTORE_PASSWORD}}
          type: PKCS12
```

## Schemas

Place OpenAPI files under:

```text
src/main/resources/schemas/
```

At startup, the shared library loads every schema file found under `gateway.schemas.directory`, keeps them in memory, and reuses them for validation and Swagger/OpenAPI decoration.

Validation behavior:

- path validation: enabled by `gateway.schemas.validate-requests`
- JSON body validation: enabled by `gateway.schemas.validate-bodies`
- malformed or otherwise unprocessable JSON is rejected with `400` when body validation is enabled
- strict schema presence: enabled by `gateway.schemas.strict-mode`
- JSON response trimming: enabled by `gateway.schemas.validate-responses`
- response trimming applies only to JSON bodies and only when a matching response schema is available

## Examples

### Public or internal backend with no auth

```yaml
gateway:
  backends:
    - name: public-service
      baseUrl: http://localhost:9004
      path: /api/v1/public
      schema: public-service.yaml
      enabled: true
      securityType: none
```

### Legacy backend with Basic auth and mTLS

```yaml
gateway:
  tls:
    profiles:
      legacy-mtls:
        truststore:
          path: /secrets/truststore.p12
          password: ${TRUSTSTORE_PASSWORD}
          type: PKCS12
        keystore:
          path: /secrets/client-keystore.p12
          password: ${KEYSTORE_PASSWORD}
          key-password: ${KEY_PASSWORD:${KEYSTORE_PASSWORD}}
          type: PKCS12

  backends:
    - name: legacy-service
      baseUrl: https://legacy.example.com
      path: /api
      schema: legacy-service.yaml
      securityType: basic
      tls-profile: legacy-mtls
      securityConfig:
        username: ${LEGACY_SERVICE_USERNAME}
        password: ${LEGACY_SERVICE_PASSWORD}
```

### Private Cloud Run OAuth service plus legacy JWT backend

```yaml
gateway:
  auth:
    service-url: https://oauth-service-abcde-ew.a.run.app
    security-type: cloudrun
    security-config:
      audience: https://oauth-service-abcde-ew.a.run.app/

  backends:
    - name: legacy-service
      baseUrl: https://legacy.example.com
      path: /api
      schema: legacy-service.yaml
      securityType: jwt
      auth-request:
        sparte-gvo:
          - a
          - b
        btx:
          - FirstFunction
        pss:
          - SecondFunction
      securityConfig:
        bearer-source: auth_z_token
        token-headers.X-Glue-Token: glue_token
```

### Apigee-style JWT backend

```yaml
gateway:
  backends:
    - name: apigee-service
      baseUrl: https://apigee.example.com
      path: /api
      schema: apigee-service.yaml
      securityType: jwt
      auth-request:
        sparte-gvo:
          - a
          - b
        btx:
          - FirstFunction
        pss:
          - SecondFunction
      securityConfig:
        bearer-source: customer_access_token
        static-headers.x-api-key: ${APIGEE_API_KEY}
```

### Form Auth Example

```yaml
gateway:
  backends:
    - name: form-service
      baseUrl: https://legacy.example.com
      path: /api/v1/legacy
      schema: legacy-service.yaml
      securityType: form
      securityConfig:
        service: auth
        auth-path: /auth/form
        form-params.grant_type: literal:client_credentials
        form-params.client_id: header:Client-Id
        form-params.client_secret: cookie:clientSecret
        form-params.scope: literal:appointments.read
        response-headers.Authorization: access_token
        response-header-prefixes.Authorization: Bearer
        response-headers.X-Tenant-Token: tenant_token
```

### Inline Form Auth Example

Use this when the backend request itself must carry the form credentials, similar to `basic` auth using a single upstream request.

```yaml
gateway:
  backends:
    - name: form-inline-service
      baseUrl: https://legacy.example.com
      path: /api/v1/legacy
      schema: legacy-form.yaml
      securityType: form
      securityConfig:
        service: inline
        form-params.grant_type: literal:client_credentials
        form-params.client_id: header:Client-Id
        form-params.client_secret: cookie:clientSecret
```

Notes:
- `service: inline` does not call the auth service
- omitting `service` is equivalent to `service: inline`
- it merges configured `form-params.*` into the outbound backend request body
- use it only with `application/x-www-form-urlencoded` backend requests
- the incoming proxy request must also use `Content-Type: application/x-www-form-urlencoded`

### Private Cloud Run backend

```yaml
gateway:
  backends:
    - name: orders
      baseUrl: https://orders-service-abcde-ew.a.run.app
      path: /
      schema: orders-service.yaml
      securityType: cloudrun
      securityConfig:
        audience: https://orders-service-abcde-ew.a.run.app/
```

## Running Locally

Development:

```bash
cd proxy
mvn quarkus:dev
```

Package:

```bash
cd proxy
mvn clean package
```

Run packaged app:

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

## Health and Metrics

- Health: `/q/health`
- Liveness: `/q/health/live`
- Readiness: `/q/health/ready`
- Metrics: `/q/metrics`

## Cloud Run

This module is prepared for Cloud Run and Jib-based image creation.

Build and push:

```bash
mvn -pl proxy clean package \
  -DskipTests \
  -Dquarkus.container-image.build=true \
  -Dquarkus.container-image.push=true \
  -Dquarkus.container-image.registry="${REGION}-docker.pkg.dev" \
  -Dquarkus.container-image.group="${PROJECT_ID}/${REPOSITORY}" \
  -Dquarkus.container-image.name="backend-gateway-proxy" \
  -Dquarkus.container-image.tag="${IMAGE_TAG}"
```

For Cloud Run deployment, mounted config files, TLS secret mounting, and service-to-service auth details, see [CLOUDRUN.md](./CLOUDRUN.md).

## Testing

Run module tests:

```bash
mvn -pl proxy test
```

Current tests cover:

- schema loading for multiple configured backends
- request body validation on templated paths
- auth misconfiguration handling
- retry behavior for transient auth-service failures
- Cloud Run backend auth
- Cloud Run auth-service calls
- TLS profile loading
curl http://localhost:8080/q/metrics

# Prometheus format
curl http://localhost:8080/q/metrics/prometheus
```

### Health Checks

```bash
# Liveness
curl http://localhost:8080/q/health/live

# Readiness
curl http://localhost:8080/q/health/ready

# Overall health
curl http://localhost:8080/q/health
```

### Dev UI (Development only)

```
http://localhost:8080/q/dev
```
