#!/bin/bash

set -euo pipefail

# Example script that demonstrates both supported fixture-mode workflows:
# 1. Manual: create backend -> endpoint -> response
# 2. Schema: create backend -> upload OpenAPI -> generate mocks

BASE_URL="${BASE_URL:-http://localhost:8080}"

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

require_command curl
require_command jq

echo "Base URL: $BASE_URL"
echo

echo "=== Manual workflow ==="

MANUAL_BACKEND=$(curl -sS -X POST "$BASE_URL/admin/api/backends" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "manual-example-service",
    "baseUrl": "http://example.internal",
    "path": "/api/manual",
    "securityType": "NONE",
    "securityConfig": "{}",
    "enabled": true
  }')

MANUAL_BACKEND_ID=$(echo "$MANUAL_BACKEND" | jq -r '.id')
echo "Created backend manual-example-service with id $MANUAL_BACKEND_ID"

MANUAL_ENDPOINT=$(curl -sS -X POST "$BASE_URL/admin/api/mock-endpoints" \
  -H "Content-Type: application/json" \
  -d '{
    "backendName": "manual-example-service",
    "method": "GET",
    "path": "/users/{id}",
    "description": "Get one user manually",
    "enabled": true
  }')

MANUAL_ENDPOINT_ID=$(echo "$MANUAL_ENDPOINT" | jq -r '.id')
echo "Created endpoint /users/{id} with id $MANUAL_ENDPOINT_ID"

curl -sS -X POST "$BASE_URL/admin/api/mock-endpoints/$MANUAL_ENDPOINT_ID/responses" \
  -H "Content-Type: application/json" \
  -d "{
    \"mockEndpointId\": $MANUAL_ENDPOINT_ID,
    \"name\": \"User found\",
    \"matchConditions\": \"{}\",
    \"httpStatus\": 200,
    \"responseBody\": \"{\\\"id\\\":1,\\\"name\\\":\\\"Manual Alice\\\",\\\"tier\\\":\\\"gold\\\"}\",
    \"responseHeaders\": \"{\\\"Content-Type\\\":\\\"application/json\\\",\\\"X-Source\\\":\\\"manual-example\\\"}\",
    \"priority\": 10,
    \"enabled\": true,
    \"delayMs\": 5
  }" | jq .

echo
echo "Manual gateway check:"
curl -sS "$BASE_URL/api/v1/manual-example-service/users/1" | jq .

echo
echo "=== Schema workflow ==="

SCHEMA_BACKEND=$(curl -sS -X POST "$BASE_URL/admin/api/backends" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "schema-example-service",
    "baseUrl": "http://upstream.internal",
    "path": "/api/schema",
    "securityType": "NONE",
    "securityConfig": "{}",
    "enabled": true
  }')

SCHEMA_BACKEND_ID=$(echo "$SCHEMA_BACKEND" | jq -r '.id')
echo "Created backend schema-example-service with id $SCHEMA_BACKEND_ID"

SCHEMA_FILE=$(mktemp)
cat <<'EOF' >"$SCHEMA_FILE"
{
  "openapi": "3.0.3",
  "info": {
    "title": "Schema Example API",
    "version": "1.0.0",
    "description": "Generated example API"
  },
  "paths": {
    "/users": {
      "get": {
        "summary": "List users",
        "responses": {
          "200": {
            "description": "Users list",
            "content": {
              "application/json": {
                "schema": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "id": { "type": "integer" },
                      "name": { "type": "string" },
                      "email": { "type": "string", "format": "email" }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
EOF

curl -sS -X POST "$BASE_URL/admin/api/backends/$SCHEMA_BACKEND_ID/schema" \
  -F "schemaFile=@$SCHEMA_FILE;type=application/json" \
  -F "migrationOption=REVALIDATE_AND_DISABLE" | jq .

curl -sS -X POST "$BASE_URL/admin/api/backends/$SCHEMA_BACKEND_ID/generate-mocks" \
  -H "Content-Type: application/json" \
  -d '{
    "generateEndpoints": true,
    "generateResponses": true,
    "guidedValues": {
      "[].__size": 2,
      "[0].id": 101,
      "[0].name": "Schema Alice",
      "[0].email": "schema.alice@example.com",
      "[1].id": 102,
      "[1].name": "Schema Bob",
      "[1].email": "schema.bob@example.com"
    }
  }' | jq .

echo
echo "Schema gateway check:"
curl -sS "$BASE_URL/api/v1/schema-example-service/users" | jq .

echo
echo "Discovery:"
curl -sS "$BASE_URL/api/backends/catalog" | jq .

echo
echo "Swagger UI:"
echo "$BASE_URL/swagger-ui/index.html?url=/api/backends/schema-example-service/openapi.json"
