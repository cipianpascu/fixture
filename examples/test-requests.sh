#!/bin/bash

set -euo pipefail

# Smoke-test script for the current fixture-mode API surface.
# Assumes the example services created by create-mock-example.sh already exist.

BASE_URL="${BASE_URL:-http://localhost:8080}"

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

require_command curl
require_command jq

echo "==============================================="
echo "Testing Backend Gateway at $BASE_URL"
echo "==============================================="
echo

echo "1. Health endpoint"
curl -sS "$BASE_URL/api/v1/health" | jq .
echo

echo "2. Actuator health"
curl -sS "$BASE_URL/actuator/health" | jq .
echo

echo "3. Backend discovery catalog"
curl -sS "$BASE_URL/api/backends/catalog" | jq .
echo

echo "4. Backend descriptors"
curl -sS "$BASE_URL/api/backends/with-schemas" | jq .
echo

echo "5. Manual mock endpoint"
curl -sS "$BASE_URL/api/v1/manual-example-service/users/1" | jq .
echo

echo "6. Schema-generated endpoint"
curl -sS "$BASE_URL/api/v1/schema-example-service/users" | jq .
echo

echo "7. Schema OpenAPI document"
curl -sS "$BASE_URL/api/backends/schema-example-service/openapi.json" | jq '{title:.info.title, server:.servers[0].url, paths:(.paths | keys)}'
echo

echo "8. Admin backends"
curl -sS "$BASE_URL/admin/api/backends" | jq .
echo

echo "9. Manual backend endpoints"
curl -sS "$BASE_URL/admin/api/mock-endpoints/backend/manual-example-service" | jq .
echo

echo "10. Schema backend endpoints"
curl -sS "$BASE_URL/admin/api/mock-endpoints/backend/schema-example-service" | jq .
echo

echo "11. Swagger UI deep link"
echo "$BASE_URL/swagger-ui/index.html?url=/api/backends/schema-example-service/openapi.json"
