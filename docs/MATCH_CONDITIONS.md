# Match Conditions Guide

## Overview

Match conditions allow you to **return different responses** based on the incoming request characteristics. This enables dynamic mock behavior without creating separate endpoints.

## Supported Match Conditions

The `matchConditions` field in `MockResponse` supports five types of matching:

### 1. Path Parameters (`pathParams`)
Match based on values extracted from the URL path pattern.

### 2. Query Parameters (`queryParams`)
Match based on URL query string parameters.

### 3. Headers (`headers`)
Match based on HTTP request headers.

### 4. Body Content (`bodyContains`)
Match if the request body contains specific text (literal string matching).

### 5. JSON Body Attributes (`bodyAttributes`)
Match based on JSON attributes with smart type conversion (string ↔ number).

## Match Conditions Format

```json
{
  "pathParams": {
    "id": "1",
    "orderId": "456"
  },
  "queryParams": {
    "role": "admin",
    "status": "active"
  },
  "headers": {
    "X-User-Type": "premium",
    "Authorization": "Bearer token123"
  },
  "bodyContains": "search-term",
  "bodyAttributes": {
    "id": 123,
    "status": "active",
    "user.type": "premium"
  }
}
```

## Path Parameter Matching

### Problem: Multiple Resources, One Endpoint

When you have an endpoint pattern like `/users/{id}`, both `/users/1` and `/users/2` match the same pattern. How do you return different data for each user?

**Solution:** Use `pathParams` in `matchConditions`!

### Example: Different Users

```bash
# 1. Create endpoint with path parameter
curl -X POST http://localhost:8080/admin/api/mock-endpoints \
  -H "Content-Type: application/json" \
  -d '{
    "backendName": "user-service",
    "method": "GET",
    "path": "/users/{id}",
    "enabled": true
  }'
# Returns: {"id": 100, ...}

# 2. Create response for user ID = 1
curl -X POST http://localhost:8080/admin/api/mock-endpoints/100/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "User 1 - John",
    "matchConditions": "{\"pathParams\":{\"id\":\"1\"}}",
    "httpStatus": 200,
    "responseBody": "{\"id\":1,\"name\":\"John\",\"email\":\"john@example.com\"}",
    "priority": 20,
    "enabled": true
  }'

# 3. Create response for user ID = 2
curl -X POST http://localhost:8080/admin/api/mock-endpoints/100/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "User 2 - Jane",
    "matchConditions": "{\"pathParams\":{\"id\":\"2\"}}",
    "httpStatus": 200,
    "responseBody": "{\"id\":2,\"name\":\"Jane\",\"email\":\"jane@example.com\"}",
    "priority": 20,
    "enabled": true
  }'

# 4. Create default "not found" response
curl -X POST http://localhost:8080/admin/api/mock-endpoints/100/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "User Not Found",
    "matchConditions": "{}",
    "httpStatus": 404,
    "responseBody": "{\"error\":\"User not found\"}",
    "priority": 10,
    "enabled": true
  }'
```

**Test it:**
```bash
curl http://localhost:8080/api/v1/user-service/users/1
# Returns: {"id":1,"name":"John","email":"john@example.com"}

curl http://localhost:8080/api/v1/user-service/users/2
# Returns: {"id":2,"name":"Jane","email":"jane@example.com"}

curl http://localhost:8080/api/v1/user-service/users/999
# Returns: {"error":"User not found"}  (default fallback)
```

### Multiple Path Parameters

For endpoints with multiple path parameters like `/users/{userId}/orders/{orderId}`:

```bash
curl -X POST http://localhost:8080/admin/api/mock-endpoints/200/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "User 1 Order 123",
    "matchConditions": "{\"pathParams\":{\"userId\":\"1\",\"orderId\":\"123\"}}",
    "httpStatus": 200,
    "responseBody": "{\"userId\":1,\"orderId\":123,\"product\":\"Laptop\",\"total\":999.99}",
    "priority": 20,
    "enabled": true
  }'
```

**Test:**
```bash
curl http://localhost:8080/api/v1/user-service/users/1/orders/123
# Returns: {"userId":1,"orderId":123,"product":"Laptop","total":999.99}

curl http://localhost:8080/api/v1/user-service/users/1/orders/456
# No specific match, returns default response
```

## Query Parameter Matching

Match based on URL query strings like `?role=admin&status=active`.

### Example: User Roles

```bash
# Create endpoint
curl -X POST http://localhost:8080/admin/api/mock-endpoints \
  -H "Content-Type: application/json" \
  -d '{
    "backendName": "user-service",
    "method": "GET",
    "path": "/users",
    "enabled": true
  }'
# Returns: {"id": 300, ...}

# Response for admin users
curl -X POST http://localhost:8080/admin/api/mock-endpoints/300/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Admin Users",
    "matchConditions": "{\"queryParams\":{\"role\":\"admin\"}}",
    "httpStatus": 200,
    "responseBody": "[{\"id\":1,\"name\":\"Admin John\",\"role\":\"admin\"}]",
    "priority": 20,
    "enabled": true
  }'

# Response for regular users
curl -X POST http://localhost:8080/admin/api/mock-endpoints/300/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Regular Users",
    "matchConditions": "{}",
    "httpStatus": 200,
    "responseBody": "[{\"id\":2,\"name\":\"User Jane\",\"role\":\"user\"}]",
    "priority": 10,
    "enabled": true
  }'
```

**Test:**
```bash
curl "http://localhost:8080/api/v1/user-service/users?role=admin"
# Returns admin users

curl "http://localhost:8080/api/v1/user-service/users"
# Returns regular users (default)
```

## Header Matching

Match based on HTTP headers.

### Example: API Versioning

```bash
# v1 API response
curl -X POST http://localhost:8080/admin/api/mock-endpoints/400/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "API v1 Response",
    "matchConditions": "{\"headers\":{\"X-API-Version\":\"v1\"}}",
    "httpStatus": 200,
    "responseBody": "{\"version\":\"v1\",\"data\":{\"id\":1}}",
    "priority": 20,
    "enabled": true
  }'

# v2 API response
curl -X POST http://localhost:8080/admin/api/mock-endpoints/400/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "API v2 Response",
    "matchConditions": "{\"headers\":{\"X-API-Version\":\"v2\"}}",
    "httpStatus": 200,
    "responseBody": "{\"version\":\"v2\",\"data\":{\"id\":1,\"metadata\":{}}}",
    "priority": 20,
    "enabled": true
  }'
```

**Test:**
```bash
curl http://localhost:8080/api/v1/user-service/data \
  -H "X-API-Version: v1"
# Returns v1 format

curl http://localhost:8080/api/v1/user-service/data \
  -H "X-API-Version: v2"
# Returns v2 format with metadata
```

## Body Content Matching

Match if the request body contains specific text.

### Example: Search Queries

```bash
# Response for "laptop" searches
curl -X POST http://localhost:8080/admin/api/mock-endpoints/500/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Laptop Search Results",
    "matchConditions": "{\"bodyContains\":\"laptop\"}",
    "httpStatus": 200,
    "responseBody": "[{\"id\":1,\"name\":\"Dell Laptop\",\"price\":999}]",
    "priority": 20,
    "enabled": true
  }'

# Response for "phone" searches
curl -X POST http://localhost:8080/admin/api/mock-endpoints/500/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Phone Search Results",
    "matchConditions": "{\"bodyContains\":\"phone\"}",
    "httpStatus": 200,
    "responseBody": "[{\"id\":2,\"name\":\"iPhone\",\"price\":1200}]",
    "priority": 20,
    "enabled": true
  }'
```

**Test:**
```bash
curl -X POST http://localhost:8080/api/v1/product-service/search \
  -H "Content-Type: application/json" \
  -d '{"query":"laptop"}'
# Returns laptop results

curl -X POST http://localhost:8080/api/v1/product-service/search \
  -H "Content-Type: application/json" \
  -d '{"query":"phone"}'
# Returns phone results
```

## JSON Body Attributes Matching

**⭐ RECOMMENDED for JSON APIs** - Intelligently matches JSON attributes with automatic type conversion.

### Why Use `bodyAttributes` Instead of `bodyContains`?

**The Problem with `bodyContains`:**
```json
// Your match condition
{"bodyContains": "\"id\": \"123\""}

// ✅ Works with string IDs
{"id": "123", "name": "John"}

// ❌ FAILS with integer IDs (no quotes!)
{"id": 123, "name": "John"}
```

**The Solution with `bodyAttributes`:**
```json
// Your match condition
{"bodyAttributes": {"id": 123}}

// ✅ Works with integer IDs
{"id": 123, "name": "John"}

// ✅ ALSO works with string IDs (smart conversion!)
{"id": "123", "name": "John"}
```

### Features

✅ **Type-aware matching** - Handles strings, numbers, booleans  
✅ **Smart conversion** - Matches `"123"` with `123` automatically  
✅ **Nested attributes** - Use dot notation: `"user.type"`  
✅ **Multiple fields** - Match on several attributes at once  
✅ **JSON parsing** - Proper JSON structure understanding  

### Example 1: Match by ID (String or Integer)

```bash
# Create endpoint
curl -X POST http://localhost:8080/admin/api/mock-endpoints \
  -H "Content-Type: application/json" \
  -d '{
    "backendName": "user-service",
    "method": "POST",
    "path": "/users",
    "enabled": true
  }'

# Response for ID = 123 (works with BOTH string and integer!)
curl -X POST http://localhost:8080/admin/api/mock-endpoints/1/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "User 123",
    "matchConditions": "{\"bodyAttributes\":{\"id\":123}}",
    "httpStatus": 200,
    "responseBody": "{\"id\":123,\"name\":\"John Doe\",\"email\":\"john@example.com\"}",
    "priority": 20,
    "enabled": true
  }'

# Response for ID = 456
curl -X POST http://localhost:8080/admin/api/mock-endpoints/1/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "User 456",
    "matchConditions": "{\"bodyAttributes\":{\"id\":456}}",
    "httpStatus": 200,
    "responseBody": "{\"id\":456,\"name\":\"Jane Smith\",\"email\":\"jane@example.com\"}",
    "priority": 20,
    "enabled": true
  }'

# Default response
curl -X POST http://localhost:8080/admin/api/mock-endpoints/1/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "User Not Found",
    "matchConditions": "{}",
    "httpStatus": 404,
    "responseBody": "{\"error\":\"User not found\"}",
    "priority": 10,
    "enabled": true
  }'
```

**Test - Both Integer and String Work:**
```bash
# Integer ID
curl -X POST http://localhost:8080/api/v1/user-service/users \
  -d '{"id": 123, "action": "create"}'
# ✅ Returns: User 123

# String ID (same match condition!)
curl -X POST http://localhost:8080/api/v1/user-service/users \
  -d '{"id": "123", "action": "create"}'
# ✅ Also returns: User 123 (smart matching!)
```

### Example 2: Multiple Attributes

Match on multiple JSON fields simultaneously:

```bash
curl -X POST http://localhost:8080/admin/api/mock-endpoints/1/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Active User 123",
    "matchConditions": "{\"bodyAttributes\":{\"id\":123,\"status\":\"active\"}}",
    "httpStatus": 200,
    "responseBody": "{\"id\":123,\"status\":\"active\",\"data\":\"Active user data\"}",
    "priority": 30,
    "enabled": true
  }'
```

**Matches:**
```bash
# ✅ Integer ID, matches
curl -X POST http://localhost:8080/api/v1/user-service/users \
  -d '{"id": 123, "status": "active"}'

# ✅ String ID, also matches!
curl -X POST http://localhost:8080/api/v1/user-service/users \
  -d '{"id": "123", "status": "active"}'

# ❌ Wrong status, doesn't match
curl -X POST http://localhost:8080/api/v1/user-service/users \
  -d '{"id": 123, "status": "inactive"}'
```

### Example 3: Nested Attributes

Access nested JSON fields using **dot notation**:

```bash
curl -X POST http://localhost:8080/admin/api/mock-endpoints/1/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Premium User",
    "matchConditions": "{\"bodyAttributes\":{\"user.type\":\"premium\",\"user.id\":123}}",
    "httpStatus": 200,
    "responseBody": "{\"message\":\"Welcome premium user!\",\"features\":[\"unlimited\",\"priority-support\"]}",
    "priority": 30,
    "enabled": true
  }'
```

**Test:**
```bash
curl -X POST http://localhost:8080/api/v1/user-service/validate \
  -d '{
    "user": {
      "id": 123,
      "type": "premium"
    },
    "action": "login"
  }'
# ✅ Returns: Premium user message
```

### Example 4: Real-World - Order Processing

```bash
# 1. Create order endpoint
curl -X POST http://localhost:8080/admin/api/mock-endpoints \
  -H "Content-Type: application/json" \
  -d '{
    "backendName": "order-service",
    "method": "POST",
    "path": "/orders",
    "enabled": true
  }'

# 2. Response for product ID = 1 (handles both "1" and 1)
curl -X POST http://localhost:8080/admin/api/mock-endpoints/1/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Product 1 Order",
    "matchConditions": "{\"bodyAttributes\":{\"productId\":1}}",
    "httpStatus": 201,
    "responseBody": "{\"orderId\":1001,\"productId\":1,\"status\":\"confirmed\",\"price\":99.99}",
    "priority": 20,
    "enabled": true
  }'

# 3. Response for product ID = 2
curl -X POST http://localhost:8080/admin/api/mock-endpoints/1/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Product 2 Order",
    "matchConditions": "{\"bodyAttributes\":{\"productId\":2}}",
    "httpStatus": 201,
    "responseBody": "{\"orderId\":1002,\"productId\":2,\"status\":\"confirmed\",\"price\":149.99}",
    "priority": 20,
    "enabled": true
  }'

# 4. Out of stock product
curl -X POST http://localhost:8080/admin/api/mock-endpoints/1/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Product 3 - Out of Stock",
    "matchConditions": "{\"bodyAttributes\":{\"productId\":3}}",
    "httpStatus": 400,
    "responseBody": "{\"error\":\"Product out of stock\"}",
    "priority": 20,
    "enabled": true
  }'

# 5. Default - invalid product
curl -X POST http://localhost:8080/admin/api/mock-endpoints/1/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Invalid Product",
    "matchConditions": "{}",
    "httpStatus": 400,
    "responseBody": "{\"error\":\"Invalid product ID\"}",
    "priority": 10,
    "enabled": true
  }'
```

**Test with Different Data Types:**
```bash
# Integer productId
curl -X POST http://localhost:8080/api/v1/order-service/orders \
  -d '{"productId": 1, "quantity": 5}'
# ✅ Returns: Product 1 Order

# String productId (same condition works!)
curl -X POST http://localhost:8080/api/v1/order-service/orders \
  -d '{"productId": "1", "quantity": 5}'
# ✅ Also returns: Product 1 Order

# Out of stock
curl -X POST http://localhost:8080/api/v1/order-service/orders \
  -d '{"productId": 3, "quantity": 1}'
# ✅ Returns: Out of stock error

# Invalid product
curl -X POST http://localhost:8080/api/v1/order-service/orders \
  -d '{"productId": 999, "quantity": 1}'
# ✅ Returns: Invalid product error
```

### Comparison: `bodyContains` vs `bodyAttributes`

| Feature | `bodyContains` | `bodyAttributes` |
|---------|----------------|------------------|
| **String matching** | ✅ Literal text search | ✅ Intelligent matching |
| **Type handling** | ❌ `"123"` ≠ `123` | ✅ `"123"` = `123` |
| **JSON structure** | ❌ String-based only | ✅ Proper JSON parsing |
| **Nested fields** | ❌ Difficult | ✅ Dot notation: `user.id` |
| **Multiple fields** | ❌ Complex patterns | ✅ Simple object |
| **Number matching** | ❌ Quotes matter | ✅ Type-agnostic |
| **Use case** | Text search | **JSON APIs** ⭐ |

### When to Use Each

| Scenario | Use |
|----------|-----|
| **Matching JSON attributes by value** | `bodyAttributes` ⭐ |
| **Need to handle integer/string IDs** | `bodyAttributes` ⭐ |
| **Matching nested JSON fields** | `bodyAttributes` ⭐ |
| **Multiple field matching** | `bodyAttributes` ⭐ |
| **Simple text search** | `bodyContains` |
| **Non-JSON content** | `bodyContains` |
| **Partial text matching** | `bodyContains` |

### Type Conversion Rules

The `bodyAttributes` matcher handles these conversions automatically:

```
"123" ↔ 123     ✅ String and integer are considered equal
"true" ↔ true   ✅ String and boolean match
"12.5" ↔ 12.5   ✅ String and float match
null ↔ null     ✅ Null values match
```

### Important Notes

✅ **Recommended for JSON APIs** - Use `bodyAttributes` for all JSON-based matching  
✅ **Case sensitive** - String comparisons are case-sensitive  
✅ **Exact match** - All specified attributes must match  
⚠️ **JSON only** - Request body must be valid JSON  
⚠️ **No regex** - Values are matched exactly (with type conversion)  

## Combining Multiple Conditions

You can combine multiple condition types for very specific matching.

### Example: Admin User with Specific ID

```bash
curl -X POST http://localhost:8080/admin/api/mock-endpoints/100/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Admin User 1 with Details",
    "matchConditions": "{\"pathParams\":{\"id\":\"1\"},\"queryParams\":{\"role\":\"admin\"},\"headers\":{\"X-Include-Details\":\"true\"}}",
    "httpStatus": 200,
    "responseBody": "{\"id\":1,\"name\":\"Admin John\",\"role\":\"admin\",\"permissions\":[\"read\",\"write\",\"delete\"],\"department\":\"IT\"}",
    "priority": 30,
    "enabled": true
  }'

curl -X POST http://localhost:8080/admin/api/mock-endpoints/100/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Admin User 1 Basic",
    "matchConditions": "{\"pathParams\":{\"id\":\"1\"},\"queryParams\":{\"role\":\"admin\"}}",
    "httpStatus": 200,
    "responseBody": "{\"id\":1,\"name\":\"Admin John\",\"role\":\"admin\"}",
    "priority": 20,
    "enabled": true
  }'

curl -X POST http://localhost:8080/admin/api/mock-endpoints/100/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Regular User 1",
    "matchConditions": "{\"pathParams\":{\"id\":\"1\"}}",
    "httpStatus": 200,
    "responseBody": "{\"id\":1,\"name\":\"John\"}",
    "priority": 10,
    "enabled": true
  }'
```

**Test:**
```bash
# Most specific - all three conditions match
curl "http://localhost:8080/api/v1/user-service/users/1?role=admin" \
  -H "X-Include-Details: true"
# Returns: full admin details with permissions

# Two conditions match
curl "http://localhost:8080/api/v1/user-service/users/1?role=admin"
# Returns: basic admin info

# Only path param matches
curl "http://localhost:8080/api/v1/user-service/users/1"
# Returns: regular user info
```

## Priority and Matching Rules

### How Matching Works

1. **Path Matching**: System finds endpoint matching the URL pattern (e.g., `/users/{id}` matches `/users/123`)
2. **Path Parameter Extraction**: Extracts values from URL (e.g., `{id: "123"}`)
3. **Response Selection**: Gets all responses for that endpoint, ordered by **priority (highest first)**
4. **Condition Checking**: Checks each response's `matchConditions` in priority order
5. **First Match Wins**: Returns the **first response** where all conditions match
6. **Default Fallback**: If no conditions match, returns first response as default

### Priority Guidelines

Use these priority ranges:

- **30-40**: Very specific matches (3+ conditions)
- **20-29**: Specific matches (1-2 conditions)
- **10-19**: Default/fallback responses (no conditions or `{}`)

### Example Priority Setup

```json
[
  {
    "priority": 30,
    "matchConditions": "{\"pathParams\":{\"id\":\"1\"},\"queryParams\":{\"role\":\"admin\"},\"headers\":{\"X-Details\":\"true\"}}",
    "comment": "Most specific - 3 conditions"
  },
  {
    "priority": 25,
    "matchConditions": "{\"pathParams\":{\"id\":\"1\"},\"queryParams\":{\"role\":\"admin\"}}",
    "comment": "Specific - 2 conditions"
  },
  {
    "priority": 20,
    "matchConditions": "{\"pathParams\":{\"id\":\"1\"}}",
    "comment": "Moderate - 1 condition"
  },
  {
    "priority": 10,
    "matchConditions": "{}",
    "comment": "Default - no conditions"
  }
]
```

## Important Notes

### String Matching
All values are matched as **strings**, not numbers or booleans:
- ✅ `{"pathParams":{"id":"1"}}` - Correct
- ❌ `{"pathParams":{"id":1}}` - Wrong (number)

### Empty Conditions
Empty or missing `matchConditions` means **always match**:
```json
{
  "matchConditions": "{}"    // Always matches
}
```
or
```json
{
  "matchConditions": ""      // Always matches
}
```

### Case Sensitivity
- **Path parameters**: Case-sensitive (`id=1` ≠ `id=1` in different case)
- **Query parameters**: Case-sensitive
- **Headers**: Case-insensitive (HTTP standard)

### Condition Logic
All conditions within a response must match (AND logic):
```json
{
  "pathParams": {"id": "1"},
  "queryParams": {"role": "admin"}
}
```
Means: `id=1 AND role=admin` (both must be true)

## Best Practices

### 1. Always Provide a Default
Create at least one response with empty conditions as fallback:
```json
{
  "name": "Default Response",
  "matchConditions": "{}",
  "priority": 10,
  "httpStatus": 200
}
```

### 2. Use Descriptive Names
Name responses to indicate what they match:
```json
{
  "name": "User 1 - Admin with Details",
  "name": "User 1 - Admin Basic",
  "name": "User 1 - Regular"
}
```

### 3. Test from Specific to General
When testing, start with most specific requests:
```bash
# 1. Test most specific first
curl "/users/1?role=admin" -H "X-Details: true"

# 2. Test medium specific
curl "/users/1?role=admin"

# 3. Test least specific
curl "/users/1"
```

### 4. Use Logging to Debug
Check application logs to see which response matched:
```
INFO MockService - Found matching mock response: User 1 - Admin with Details for GET /users/1
DEBUG MockService - Matched endpoint: GET /users/{id} with path params: {id=1}
```

### 5. Group Related Responses
Keep responses for the same resource together with clear priority separation:
```
Priority 30: User 1 (3 conditions)
Priority 20: User 1 (2 conditions)
Priority 10: User 1 (default)

Priority 30: User 2 (3 conditions)
Priority 20: User 2 (2 conditions)
Priority 10: User 2 (default)
```

## Troubleshooting

### Always Getting the Same Response?

**Problem**: Both `/users/1` and `/users/2` return the same data.

**Solution**: 
1. Check if you're using `pathParams` in `matchConditions`
2. Verify the path parameter name matches the pattern (e.g., `{id}` → `"id"`)
3. Check that values are strings: `"1"` not `1`

### Condition Not Matching?

**Debug steps**:
1. Check logs: `Matched endpoint ... with path params: {id=123}`
2. Verify JSON is valid: Use a JSON validator
3. Check spelling: `pathParams` not `pathParam`
4. Test with curl `-v` to see actual request

### Priority Not Working?

**Check**:
1. Higher numbers = higher priority (30 > 20 > 10)
2. Responses are ordered by priority DESC
3. First matching response wins

## Complete Example

Full workflow for a RESTful API with path parameters:

```bash
# 1. Create endpoint
curl -X POST http://localhost:8080/admin/api/mock-endpoints \
  -H "Content-Type: application/json" \
  -d '{
    "backendName": "api-service",
    "method": "GET",
    "path": "/products/{id}",
    "enabled": true
  }'

# 2. Product 1 - Premium user with details
curl -X POST http://localhost:8080/admin/api/mock-endpoints/1/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Product 1 - Premium Details",
    "matchConditions": "{\"pathParams\":{\"id\":\"1\"},\"headers\":{\"X-User-Type\":\"premium\"}}",
    "httpStatus": 200,
    "responseBody": "{\"id\":1,\"name\":\"Laptop\",\"price\":999,\"stock\":10,\"reviews\":[...]}",
    "priority": 30
  }'

# 3. Product 1 - Regular user
curl -X POST http://localhost:8080/admin/api/mock-endpoints/1/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Product 1 - Basic",
    "matchConditions": "{\"pathParams\":{\"id\":\"1\"}}",
    "httpStatus": 200,
    "responseBody": "{\"id\":1,\"name\":\"Laptop\",\"price\":999}",
    "priority": 20
  }'

# 4. Product 2
curl -X POST http://localhost:8080/admin/api/mock-endpoints/1/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Product 2",
    "matchConditions": "{\"pathParams\":{\"id\":\"2\"}}",
    "httpStatus": 200,
    "responseBody": "{\"id\":2,\"name\":\"Mouse\",\"price\":29}",
    "priority": 20
  }'

# 5. Default - Product not found
curl -X POST http://localhost:8080/admin/api/mock-endpoints/1/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Product Not Found",
    "matchConditions": "{}",
    "httpStatus": 404,
    "responseBody": "{\"error\":\"Product not found\"}",
    "priority": 10
  }'

# Test
curl http://localhost:8080/api/v1/api-service/products/1 -H "X-User-Type: premium"
# → Returns product 1 with full details

curl http://localhost:8080/api/v1/api-service/products/1
# → Returns product 1 basic

curl http://localhost:8080/api/v1/api-service/products/2
# → Returns product 2

curl http://localhost:8080/api/v1/api-service/products/999
# → Returns 404 not found
```

## Summary

Match conditions provide powerful, flexible mock behavior:

✅ **Path Parameters** - Different data for different resources  
✅ **Query Parameters** - Filter and pagination support  
✅ **Headers** - API versioning, authentication  
✅ **Body Content (bodyContains)** - Simple text search in request body  
✅ **JSON Body Attributes (bodyAttributes)** ⭐ - Smart JSON matching with type conversion  
✅ **Priority System** - Control match order  
✅ **Combine Conditions** - Very specific matching  

**💡 Pro Tip:** For JSON APIs, always use `bodyAttributes` instead of `bodyContains` to handle type differences (string vs integer IDs, etc.)

Use them to create realistic, dynamic API mocks! 🚀
