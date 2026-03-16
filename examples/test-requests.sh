#!/bin/bash

# Test script for Backend Gateway
# Usage: ./test-requests.sh [routing|fixture]

MODE=${1:-fixture}
BASE_URL="http://localhost:8080"

echo "==============================================="
echo "Testing Backend Gateway in $MODE mode"
echo "==============================================="
echo ""

# Test health endpoint
echo "1. Testing health endpoint..."
curl -s "$BASE_URL/api/v1/health" | jq '.'
echo ""
echo ""

# Test GET request
echo "2. Testing GET /api/v1/user-service/users..."
curl -s "$BASE_URL/api/v1/user-service/users" \
  -H "Authorization: Bearer fake-jwt-token" | jq '.'
echo ""
echo ""

# Test GET request with ID
echo "3. Testing GET /api/v1/user-service/users/1..."
curl -s "$BASE_URL/api/v1/user-service/users/1" \
  -H "Authorization: Bearer fake-jwt-token" | jq '.'
echo ""
echo ""

# Test POST request
echo "4. Testing POST /api/v1/user-service/users..."
curl -s -X POST "$BASE_URL/api/v1/user-service/users" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer fake-jwt-token" \
  -d '{
    "name": "Test User",
    "email": "test@example.com"
  }' | jq '.'
echo ""
echo ""

# Test with query parameters
echo "5. Testing GET with query parameters..."
curl -s "$BASE_URL/api/v1/user-service/users?status=active&role=admin" \
  -H "Authorization: Bearer fake-jwt-token" | jq '.'
echo ""
echo ""

# Test non-existent backend
echo "6. Testing non-existent backend..."
curl -s "$BASE_URL/api/v1/non-existent-service/test"
echo ""
echo ""

echo "==============================================="
echo "Testing Admin API (Fixture mode only)"
echo "==============================================="
echo ""

# List all backends
echo "7. Listing all backends..."
curl -s "$BASE_URL/admin/api/backends" | jq '.'
echo ""
echo ""

# List all mock endpoints
echo "8. Listing all mock endpoints..."
curl -s "$BASE_URL/admin/api/mock-endpoints" | jq '.'
echo ""
echo ""

echo "==============================================="
echo "Testing Actuator endpoints"
echo "==============================================="
echo ""

# Health check
echo "9. Actuator health..."
curl -s "$BASE_URL/actuator/health" | jq '.'
echo ""
echo ""

# Metrics
echo "10. Available metrics..."
curl -s "$BASE_URL/actuator/metrics" | jq '.names | .[:10]'
echo ""
echo ""

echo "Tests completed!"
