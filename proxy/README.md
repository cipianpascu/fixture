# Backend Gateway Proxy

Quarkus-based HTTP proxy for routing agent traffic to multiple upstream backends with request validation, backend-specific authentication, and outbound TLS support.

## What This Module Does

- Routes requests from `/api/v1/{backend-name}/...` to configured upstream services
- Validates request path, method, and optional JSON body against configured OpenAPI schemas
- Applies backend-specific auth before forwarding
- Supports outbound TLS truststores and mTLS client certificates
- Supports Cloud Run service-to-service IAM auth for backends and for the auth service itself
- Exposes Quarkus health and Prometheus endpoints

This module is file-configured only. There is no database and no admin API.

## Request Flow

1. Incoming request hits `ProxyResource`
2. Backend is resolved from `gateway.backends`
3. Request is validated against the backend schema
4. Auth strategy enriches headers
5. Request is forwarded to the upstream backend
6. Upstream response is returned as-is

Main implementation points:

- Routing entrypoint: [src/main/java/com/agent/gateway/proxy/app/resource/ProxyResource.java](./src/main/java/com/agent/gateway/proxy/app/resource/ProxyResource.java)
- App-specific orchestration example: [src/main/java/com/agent/gateway/proxy/app/resource/OrderSummaryResource.java](./src/main/java/com/agent/gateway/proxy/app/resource/OrderSummaryResource.java)
- Shared forwarding, validation, auth, TLS, and OpenAPI support now live in the `bfa-library` module

## Auth Modes

Per backend, `securityType` can be:

- `none`: no auth enrichment
- `basic`: injects HTTP Basic credentials from `securityConfig.username/password`
- `jwt`: calls the configured auth service and maps returned tokens to backend-specific headers
- `form`: supports either a form-encoded auth-service pre-call or inline form parameter enrichment on the actual backend request
- `transactionid`: builds an auth-service JSON body from incoming headers and maps returned fields to backend headers
- `cloudrun`: generates a Google ID token and sends it as `X-Serverless-Authorization`

Auth-service calls are configured separately under `gateway.auth`. The auth service itself can also use Cloud Run IAM auth with:

- `gateway.auth.security-type=cloudrun`
- `gateway.auth.security-config.audience=...`

## TLS Model

`tls-profile` is transport-level configuration, not application auth.

Use `gateway.tls.profiles` to define reusable outbound TLS settings:

- `truststore`: trust private or internal CAs
- `keystore`: present a client certificate for mTLS

Then reference a profile from:

- `gateway.auth.tls-profile`
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

### Backend Fields

- `name`: backend identifier used in `/api/v1/{name}/...`
- `baseUrl`: upstream host
- `path`: upstream base path prefix
- `schema`: OpenAPI schema filename under `schemas/`
- `timeout`: request timeout for upstream call
- `enabled`: whether the backend is routable
- `securityType`: `none`, `basic`, `jwt`, `form`, `transactionid`, or `cloudrun`
- `securityConfig`: auth-specific key/value config
- `auth-request`: structured request payload sent to the auth service for `jwt`
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
- they do not affect calls to `gateway.auth.service-url`
- `non-proxy-hosts` supports exact hosts and `*.` suffix patterns

For `securityType: jwt`, `securityConfig` supports:

- `bearer-source`: one of `customer_access_token`, `auth_z_token`, or `glue_token`
- `bearer-header`: outbound bearer header name, defaults to `Authorization`
- `bearer-prefix`: bearer prefix, defaults to `Bearer`
- `token-headers.<Header-Name>`: maps an outbound header to one token field
- `static-headers.<Header-Name>`: adds a fixed outbound header such as an API key

For `securityType: transactionid`, `securityConfig` supports:

- `auth-path`: auth-service path to call, defaults to `/auth/transactions`
- `request-body.<field>`: maps an auth request body field from an incoming source
- `response-headers.<Header-Name>`: maps an auth response field into an outbound backend header

Supported `request-body.*` mapping sources:

- `header:<Header-Name>`: read from an incoming request header
- `cookie:<Cookie-Name>`: read from an incoming request cookie
- `literal:<value>`: use a fixed literal value

For `securityType: form`, `securityConfig` supports:

- `mode`: `auth-service` (default) or `inline`
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
- strict schema presence: enabled by `gateway.schemas.strict-mode`
- JSON response trimming: enabled by `gateway.schemas.validate-responses`

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

### Header-driven transaction-id backend

```yaml
gateway:
  auth:
    service-url: https://oauth-service-abcde-ew.a.run.app
    security-type: cloudrun
    security-config:
      audience: https://oauth-service-abcde-ew.a.run.app/

  backends:
    - name: face-service
      baseUrl: https://face.example.com
      path: /api
      schema: face-service.yaml
      securityType: transactionid
      securityConfig:
        auth-path: /auth/transactions
        request-body.processId: header:Process-Id
        response-headers.x-request-id: transactionId
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
        mode: auth-service
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
        mode: inline
        form-params.grant_type: literal:client_credentials
        form-params.client_id: header:Client-Id
        form-params.client_secret: cookie:clientSecret
```

Notes:
- `inline` mode does not call the auth service
- it merges configured `form-params.*` into the outbound backend request body
- use it only with `application/x-www-form-urlencoded` backend requests
- the incoming proxy request must also use `Content-Type: application/x-www-form-urlencoded`

Behavior:

- incoming request header `Process-Id: p123`
- auth request body `{ "processId": "p123" }`
- auth response body `{ "transactionId": "tx-123" }`
- backend request header `x-request-id: tx-123`

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

## TODO

- [ ] Response validation (if enabled)
- [ ] Request/response logging
- [ ] Rate limiting
- [ ] WebSocket support

## See Also

- [Migration Guide](../MIGRATION_GUIDE.md)
- [Example Schemas](src/main/resources/schemas/)
- [Configuration Reference](src/main/resources/application.yml)
