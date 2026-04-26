# Cloud Run Deployment

This module is prepared for Google Cloud Run and uses Quarkus Jib support for container image creation.

## What Is Configured

- The service listens on `0.0.0.0`
- The HTTP port is driven by Cloud Run's `PORT` environment variable
- Container images are built with `quarkus-container-image-jib`
- Jib can build and push directly to Artifact Registry without a Docker daemon

## Prerequisites

- Google Cloud SDK authenticated to the target project
- An Artifact Registry Docker repository
- A Cloud Run region, service name, and project id

Example variables:

```bash
export PROJECT_ID="my-gcp-project"
export REGION="europe-west1"
export REPOSITORY="backend-images"
export SERVICE="backend-gateway-proxy"
export IMAGE_TAG="$(git rev-parse --short HEAD)"
export IMAGE_REGISTRY="${REGION}-docker.pkg.dev"
export IMAGE_GROUP="${PROJECT_ID}/${REPOSITORY}"
```

## Build And Push With Jib

From the repository root:

```bash
mvn -pl proxy clean package \
  -DskipTests \
  -Dquarkus.container-image.build=true \
  -Dquarkus.container-image.push=true \
  -Dquarkus.container-image.registry="${IMAGE_REGISTRY}" \
  -Dquarkus.container-image.group="${IMAGE_GROUP}" \
  -Dquarkus.container-image.name="${SERVICE}" \
  -Dquarkus.container-image.tag="${IMAGE_TAG}"
```

The resulting image reference will be:

```bash
${IMAGE_REGISTRY}/${IMAGE_GROUP}/${SERVICE}:${IMAGE_TAG}
```

## Deploy To Cloud Run

```bash
gcloud run deploy "${SERVICE}" \
  --project "${PROJECT_ID}" \
  --region "${REGION}" \
  --platform managed \
  --image "${IMAGE_REGISTRY}/${IMAGE_GROUP}/${SERVICE}:${IMAGE_TAG}" \
  --allow-unauthenticated
```

## Runtime Configuration

### Simple env var configuration

Quarkus and `@ConfigMapping` properties can be set from environment variables. Examples:

```bash
--set-env-vars=GATEWAY_AUTH_ENABLED=true \
--set-env-vars=GATEWAY_AUTH_SERVICE_URL=https://auth.example.com \
--set-env-vars=GATEWAY_SCHEMAS_DIRECTORY=classpath:schemas/
```

### Backend configuration

For anything beyond a trivial setup, do not model the full backend list as individual Cloud Run env vars. Use a mounted config file instead.

Recommended approach:

1. Store a full Quarkus config file in Secret Manager.
2. Mount that secret into Cloud Run as a file.
3. Point Quarkus at it with `QUARKUS_CONFIG_LOCATIONS`.

Example additional deploy flags:

```bash
--update-secrets=/secrets/application-cloudrun.yml=backend-gateway-config:latest \
--set-env-vars=QUARKUS_CONFIG_LOCATIONS=/secrets/application-cloudrun.yml
```

Your mounted file can override or extend the default `application.yml`, including:

```yaml
gateway:
  auth:
    service-url: https://auth.example.com
    tls-profile: internal-ca
  tls:
    profiles:
      internal-ca:
        truststore:
          path: /secrets/truststore.p12
          password: ${TRUSTSTORE_PASSWORD}
          type: PKCS12
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
  backends:
    - name: example-service
      baseUrl: https://backend.example.com
      path: /api/v1/example
      schema: example-service.yaml
      enabled: true
      securityType: jwt
      tls-profile: partner-mtls
      authScopes:
        - read:users
```

### TLS and certificates

Cloud Run terminates inbound HTTPS before requests reach the container. The TLS configuration in this module is for outbound connections from the proxy to upstream backends or the auth service.

Use `gateway.tls.profiles` for reusable outbound TLS settings:

- `truststore`: trusted CAs for HTTPS upstreams with private or internal certificates
- `keystore`: optional client certificate for mTLS
- `gateway.auth.tls-profile`: TLS profile used by the auth service client
- `gateway.backends[n].tls-profile`: TLS profile used by a specific backend

Recommended Cloud Run setup:

1. Store truststores and keystores in Secret Manager.
2. Mount them into the container as files.
3. Reference those mounted paths from `gateway.tls.profiles`.
4. Keep passwords in env vars or separate mounted secrets.

Example deploy flags for mounted cert material:

```bash
gcloud run deploy "${SERVICE}" \
  --project "${PROJECT_ID}" \
  --region "${REGION}" \
  --platform managed \
  --image "${IMAGE_REGISTRY}/${IMAGE_GROUP}/${SERVICE}:${IMAGE_TAG}" \
  --update-secrets=/secrets/application-cloudrun.yml=backend-gateway-config:latest \
  --update-secrets=/secrets/truststore.p12=backend-gateway-truststore:latest \
  --update-secrets=/secrets/client-keystore.p12=backend-gateway-client-keystore:latest \
  --set-env-vars=QUARKUS_CONFIG_LOCATIONS=/secrets/application-cloudrun.yml \
  --set-env-vars=TRUSTSTORE_PASSWORD=changeit,KEYSTORE_PASSWORD=changeit
```

## Health Checks

Cloud Run can use the built-in Quarkus health endpoints:

- `/q/health`
- `/q/health/live`
- `/q/health/ready`

## Notes

- If you want immutable deployments, set `CONTAINER_IMAGE_TAG` explicitly per build instead of using `latest`.
- If your schemas are not baked into the image, mount them into the container and override `GATEWAY_SCHEMAS_DIRECTORY` with a `file:` path.
