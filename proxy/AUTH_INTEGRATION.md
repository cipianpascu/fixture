# Auth Service Integration

## Overview

The proxy supports multiple authentication strategies per backend. Each backend can be configured with its own authentication type:

- **JWT Authentication** - Retrieves tokens from external auth service with configurable scopes
- **Basic Authentication** - Uses username/password from configuration
- **No Authentication** - No credentials added (for public APIs)

## Authentication Types

### 1. JWT Authentication

**Flow:**
```
1. Request arrives at gateway
   ↓
2. Extract sessionId from request (header or cookie)
   ↓
3. Call auth service with sessionId and backend scopes
   ↓
4. Receive serviceToken and userGrantsToken
   ↓
5. Attach tokens as X-Service-Token and X-User-Grants-Token headers
   ↓
6. Forward to backend service
```

**Configuration:**
```yaml
- name: example-service
  securityType: jwt
  authScopes:
    - read:users
    - write:users
```

### 2. Basic Authentication

**Flow:**
```
1. Request arrives at gateway
   ↓
2. Load username/password from backend configuration
   ↓
3. Create Basic auth header (Base64 encoded)
   ↓
4. Attach as Authorization header
   ↓
5. Forward to backend service
```

**Configuration:**
```yaml
- name: legacy-service
  securityType: basic
  securityConfig:
    username: ${LEGACY_USERNAME:admin}
    password: ${LEGACY_PASSWORD:secret}
```

### 3. No Authentication

**Flow:**
```
1. Request arrives at gateway
   ↓
2. No auth processing
   ↓
3. Forward to backend service
```

**Configuration:**
```yaml
- name: public-service
  # No securityType specified or securityType: none
```

## Configuration

### Auth Service (Quarkus)

All auth service configuration is in the `gateway.auth` section:

```yaml
gateway:
  auth:
    enabled: true                       # Enable/disable auth integration
    service-url: http://localhost:8081  # Auth service base URL
    session-id-header: X-Session-Id     # Header name for session ID
    session-id-cookie: sessionId        # Cookie name for session ID  
    timeout: 5s                         # Auth service call timeout
```

**How it works:**
- The `service-url` is the base URL only (e.g., `http://localhost:8081`)
- Concrete auth flows append their own relative paths, such as `/auth/tokens/{sessionId}`
- The shared `AuthServiceCaller` in `bfa-library` performs the HTTP call, TLS setup, timeout handling, and optional Cloud Run auth
- No separate Quarkus REST Client configuration is needed

### Complete Backend Examples
```yaml
gateway:
  backends:
    # JWT backend
    - name: example-service
      baseUrl: http://localhost:9001
      path: /api/v1/example
      securityType: jwt
      authScopes:
        - read:users
        - write:users
    
    # Basic auth backend
    - name: legacy-service
      baseUrl: http://localhost:9003
      path: /api/v1/legacy
      securityType: basic
      securityConfig:
        username: ${LEGACY_USERNAME:admin}
        password: ${LEGACY_PASSWORD:secret}
    
    # Public backend (no auth)
    - name: public-api
      baseUrl: http://localhost:9004
      path: /api/v1/public
      # No securityType - no authentication
```

## JWT Authentication Details

### SessionId Extraction

The gateway looks for sessionId in the following order:

1. **Request Header**: `X-Session-Id` (configurable)
2. **Cookie**: `sessionId` (configurable)

### Auth Service API

**Request:**
```
POST {service-url}/auth/tokens/{sessionId}
Headers:
  Content-Type: application/json
Body:
{
  "sparteGvo": ["..."],
  "btx": ["..."],
  "pss": ["..."]
}
```

**Response:**
```json
{
  "glue_token": "...",
  "auth_z_token": "...",
  "customer_access_token": "...",
  "disallowed_pss": []
}
```

**Backend Request Headers:**
```
Authorization: Bearer <mapped token>
X-Glue-Token: <mapped token>
... any configured static-headers/token-headers
```

## Basic Authentication Details

**Backend Request Headers:**
```
Authorization: Basic <base64(username:password)>
```

Credentials are loaded from configuration and encoded once at startup.

## Disable Auth

To disable auth integration:

```yaml
gateway:
  auth:
    enabled: false
```

## Error Handling

- If sessionId not found for a JWT backend → request fails with `401 Unauthorized`
- If auth service fails or returns unusable tokens → request fails closed with `502 Bad Gateway`
- If backend auth configuration is invalid or unsupported → request fails with `500 Internal Server Error`

The gateway is fail-closed for protected backends.
