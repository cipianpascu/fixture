#!/bin/bash

# Example script to create a complete mock setup via Admin API
# Run in fixture mode

BASE_URL="http://localhost:8080"

echo "Creating backend configuration..."
BACKEND_RESPONSE=$(curl -s -X POST "$BASE_URL/admin/api/backends" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "user-service",
    "baseUrl": "http://localhost:9001",
    "path": "/api/v1/users",
    "securityType": "JWT",
    "securityConfig": "{}",
    "enabled": true
  }')

echo "Backend created: $BACKEND_RESPONSE"
echo ""

echo "Creating mock endpoint for GET /users..."
ENDPOINT_RESPONSE=$(curl -s -X POST "$BASE_URL/admin/api/mock-endpoints" \
  -H "Content-Type: application/json" \
  -d '{
    "backendName": "user-service",
    "method": "GET",
    "path": "/users",
    "description": "Get all users",
    "enabled": true
  }')

ENDPOINT_ID=$(echo $ENDPOINT_RESPONSE | jq -r '.id')
echo "Mock endpoint created with ID: $ENDPOINT_ID"
echo ""

echo "Creating mock response - Success case..."
curl -s -X POST "$BASE_URL/admin/api/mock-endpoints/$ENDPOINT_ID/responses" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Success - List of users",
    "matchConditions": "{}",
    "httpStatus": 200,
    "responseBody": "[{\"id\":1,\"name\":\"John Doe\",\"email\":\"john@example.com\"},{\"id\":2,\"name\":\"Jane Smith\",\"email\":\"jane@example.com\"}]",
    "responseHeaders": "{\"Content-Type\":\"application/json\",\"X-Total-Count\":\"2\"}",
    "priority": 10,
    "enabled": true,
    "delayMs": 100
  }'

echo ""
echo "Creating mock endpoint for GET /users/{id}..."
ENDPOINT2_RESPONSE=$(curl -s -X POST "$BASE_URL/admin/api/mock-endpoints" \
  -H "Content-Type: application/json" \
  -d '{
    "backendName": "user-service",
    "method": "GET",
    "path": "/users/{id}",
    "description": "Get user by ID",
    "enabled": true
  }')

ENDPOINT2_ID=$(echo $ENDPOINT2_RESPONSE | jq -r '.id')
echo "Mock endpoint created with ID: $ENDPOINT2_ID"
echo ""

echo "Creating mock response - User found..."
curl -s -X POST "$BASE_URL/admin/api/mock-endpoints/$ENDPOINT2_ID/responses" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "User found",
    "matchConditions": "{}",
    "httpStatus": 200,
    "responseBody": "{\"id\":1,\"name\":\"John Doe\",\"email\":\"john@example.com\",\"role\":\"admin\"}",
    "responseHeaders": "{\"Content-Type\":\"application/json\"}",
    "priority": 10,
    "enabled": true,
    "delayMs": 50
  }'

echo ""
echo "Creating mock endpoint for POST /users..."
ENDPOINT3_RESPONSE=$(curl -s -X POST "$BASE_URL/admin/api/mock-endpoints" \
  -H "Content-Type: application/json" \
  -d '{
    "backendName": "user-service",
    "method": "POST",
    "path": "/users",
    "description": "Create new user",
    "enabled": true
  }')

ENDPOINT3_ID=$(echo $ENDPOINT3_RESPONSE | jq -r '.id')
echo "Mock endpoint created with ID: $ENDPOINT3_ID"
echo ""

echo "Creating mock response - User created..."
curl -s -X POST "$BASE_URL/admin/api/mock-endpoints/$ENDPOINT3_ID/responses" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "User created successfully",
    "matchConditions": "{}",
    "httpStatus": 201,
    "responseBody": "{\"id\":3,\"name\":\"New User\",\"email\":\"new@example.com\",\"message\":\"User created successfully\"}",
    "responseHeaders": "{\"Content-Type\":\"application/json\",\"Location\":\"/api/v1/users/3\"}",
    "priority": 10,
    "enabled": true,
    "delayMs": 150
  }'

echo ""
echo "Setup complete! Test with:"
echo "curl http://localhost:8080/api/v1/user-service/users"
echo "curl http://localhost:8080/api/v1/user-service/users/1"
echo "curl -X POST http://localhost:8080/api/v1/user-service/users -H 'Content-Type: application/json' -d '{\"name\":\"Test\"}'"
