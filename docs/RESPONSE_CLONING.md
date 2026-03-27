# Response Cloning Feature

## Overview

Response cloning allows you to **reuse responses across multiple endpoints** without duplication. This is useful when multiple endpoints should return the same response structure.

## Architecture

The system maintains the **OneToMany** relationship between `MockEndpoint` and `MockResponse`:
- Each response belongs to exactly one endpoint
- Cloning creates **new response instances** with the same configuration
- Each cloned response gets a unique ID

```
MockEndpoint A                    MockEndpoint B
├── Response 1 (id: 10)          ├── Response 4 (id: 40) ← clone of 10
├── Response 2 (id: 20)          ├── Response 5 (id: 50) ← clone of 20
└── Response 3 (id: 30)          └── Response 6 (id: 60) ← clone of 30
```

## API Endpoints

### 1. Clone a Single Response

Clone one response to a target endpoint:

```bash
POST /admin/api/mock-responses/{responseId}/clone?targetEndpointId={endpointId}
```

**Example:**
```bash
curl -X POST "http://localhost:8080/admin/api/mock-responses/10/clone?targetEndpointId=5"
```

**Response:**
```json
{
  "id": 42,
  "name": "Success Response (copy)",
  "httpStatus": 200,
  "responseBody": "{\"status\":\"ok\"}",
  "responseHeaders": "{\"Content-Type\":\"application/json\"}",
  "priority": 10,
  "enabled": true,
  "delayMs": 100,
  "createdAt": "2026-03-22T16:40:00",
  "updatedAt": "2026-03-22T16:40:00"
}
```

**Features:**
- ✅ Clones all response configuration (status, body, headers, etc.)
- ✅ Appends " (copy)" to the name to indicate it's cloned
- ✅ Validates against target endpoint's schema (if configured)
- ✅ Returns the newly created response

### 2. Clone All Responses

Clone all responses from one endpoint to another:

```bash
POST /admin/api/mock-endpoints/{sourceEndpointId}/clone-responses?targetEndpointId={endpointId}
```

**Example:**
```bash
curl -X POST "http://localhost:8080/admin/api/mock-endpoints/1/clone-responses?targetEndpointId=5"
```

**Response:**
```json
{
  "clonedCount": 3,
  "responses": [
    {
      "id": 42,
      "name": "Success Response",
      "httpStatus": 200,
      ...
    },
    {
      "id": 43,
      "name": "Error Response",
      "httpStatus": 404,
      ...
    },
    {
      "id": 44,
      "name": "Validation Error",
      "httpStatus": 400,
      ...
    }
  ],
  "message": "Successfully cloned 3 responses"
}
```

**Features:**
- ✅ Clones all responses from source endpoint
- ✅ Preserves original names (no " (copy)" suffix)
- ✅ Validates each response against target schema
- ✅ Returns summary with count and all cloned responses

## Use Cases

### Use Case 1: Standard Error Responses

Create standard error responses once, then clone to all endpoints:

```bash
# 1. Create a "standard errors" endpoint
POST /admin/api/mock-endpoints
{
  "backendName": "templates",
  "method": "GET",
  "path": "/standard-errors",
  "description": "Template endpoint for standard errors",
  "enabled": false
}
# Returns: { "id": 999, ... }

# 2. Create standard error responses
POST /admin/api/mock-endpoints/999/responses
{
  "name": "404 Not Found",
  "httpStatus": 404,
  "responseBody": "{\"error\":\"Resource not found\"}",
  "priority": 10
}

POST /admin/api/mock-endpoints/999/responses
{
  "name": "400 Bad Request",
  "httpStatus": 400,
  "responseBody": "{\"error\":\"Invalid request\"}",
  "priority": 10
}

POST /admin/api/mock-endpoints/999/responses
{
  "name": "500 Internal Error",
  "httpStatus": 500,
  "responseBody": "{\"error\":\"Internal server error\"}",
  "priority": 10
}

# 3. Clone to all your endpoints
curl -X POST "http://localhost:8080/admin/api/mock-endpoints/999/clone-responses?targetEndpointId=1"
curl -X POST "http://localhost:8080/admin/api/mock-endpoints/999/clone-responses?targetEndpointId=2"
curl -X POST "http://localhost:8080/admin/api/mock-endpoints/999/clone-responses?targetEndpointId=3"
# ... clone to all endpoints
```

### Use Case 2: Different HTTP Methods, Same Response

Multiple HTTP methods returning the same data:

```bash
# Create GET /users endpoint with response
POST /admin/api/mock-endpoints
{
  "backendName": "user-service",
  "method": "GET",
  "path": "/users",
  "enabled": true
}
# Returns: { "id": 1, ... }

POST /admin/api/mock-endpoints/1/responses
{
  "name": "Users List",
  "httpStatus": 200,
  "responseBody": "[{\"id\":1,\"name\":\"John\"},{\"id\":2,\"name\":\"Jane\"}]",
  "priority": 10
}
# Returns: { "id": 10, ... }

# Create POST /users/search endpoint
POST /admin/api/mock-endpoints
{
  "backendName": "user-service",
  "method": "POST",
  "path": "/users/search",
  "enabled": true
}
# Returns: { "id": 2, ... }

# Clone the same response
curl -X POST "http://localhost:8080/admin/api/mock-responses/10/clone?targetEndpointId=2"

# Now both endpoints return the same user list
```

### Use Case 3: Multi-Backend Consistency

Same response structure across different backends:

```bash
# Create response on backend A
POST /admin/api/mock-endpoints/100/responses
{
  "name": "Health Check",
  "httpStatus": 200,
  "responseBody": "{\"status\":\"healthy\",\"uptime\":1234}",
  "priority": 10
}
# Returns: { "id": 50, ... }

# Clone to backend B's health endpoint
curl -X POST "http://localhost:8080/admin/api/mock-responses/50/clone?targetEndpointId=200"

# Clone to backend C's health endpoint
curl -X POST "http://localhost:8080/admin/api/mock-responses/50/clone?targetEndpointId=300"

# All backends now return consistent health check format
```

### Use Case 4: CRUD Operations

Clone success response to all CRUD endpoints:

```bash
# Create success response on POST endpoint
POST /admin/api/mock-endpoints/1/responses  # POST /items
{
  "name": "Success",
  "httpStatus": 200,
  "responseBody": "{\"status\":\"success\",\"message\":\"Operation completed\"}",
  "priority": 10
}
# Returns: { "id": 100, ... }

# Clone to all CRUD endpoints
curl -X POST "http://localhost:8080/admin/api/mock-responses/100/clone?targetEndpointId=2"  # PUT /items/{id}
curl -X POST "http://localhost:8080/admin/api/mock-responses/100/clone?targetEndpointId=3"  # DELETE /items/{id}
curl -X POST "http://localhost:8080/admin/api/mock-responses/100/clone?targetEndpointId=4"  # PATCH /items/{id}
```

## Validation

When cloning responses, the system:

1. **Validates source exists**: Fails if source response doesn't exist
2. **Validates target exists**: Fails if target endpoint doesn't exist
3. **Schema validation**: If target endpoint has an OpenAPI schema configured, validates the cloned response against it
4. **Preserves all fields**: Copies all configuration (status, body, headers, priority, delay, etc.)

**Example validation error:**
```bash
curl -X POST "http://localhost:8080/admin/api/mock-responses/10/clone?targetEndpointId=5"

# If target endpoint has schema that doesn't allow the response:
Response: 400 Bad Request
{
  "error": "ValidationError",
  "message": "Schema validation failed: Response status 201 not defined in schema for GET /users",
  "path": "/admin/api/mock-responses/10/clone",
  "status": 400,
  "timestamp": "2026-03-22T16:40:00"
}
```

## Service Methods

### `MockService.cloneResponse()`

```java
/**
 * Clone a single response to another endpoint
 * @param responseId ID of the response to clone
 * @param targetEndpointId ID of the endpoint to clone to
 * @return The newly created response
 * @throws IllegalArgumentException if source or target not found
 */
@Transactional
public MockResponse cloneResponse(Long responseId, Long targetEndpointId)
```

### `MockService.cloneAllResponses()`

```java
/**
 * Clone all responses from one endpoint to another
 * @param sourceEndpointId ID of the source endpoint
 * @param targetEndpointId ID of the target endpoint
 * @return List of newly created responses
 * @throws IllegalArgumentException if source or target not found
 */
@Transactional
public List<MockResponse> cloneAllResponses(Long sourceEndpointId, Long targetEndpointId)
```

## Implementation Details

### Clone a Single Response

1. Fetches source response by ID
2. Fetches target endpoint by ID
3. Creates new `MockResponse` with:
   - Reference to target endpoint
   - Name with " (copy)" appended
   - All other fields copied from source
4. Validates against target endpoint's schema
5. Saves and returns new response

### Clone All Responses

1. Fetches source endpoint by ID
2. Fetches target endpoint by ID
3. Gets all responses from source endpoint
4. For each source response:
   - Creates new response instance
   - Preserves original name (no " (copy)")
   - Validates against target schema
   - Saves to database
5. Returns list of all cloned responses

## Benefits

✅ **Reduce Duplication**: Create once, reuse many times
✅ **Consistency**: Same response structure across endpoints
✅ **Efficiency**: Faster setup for new endpoints
✅ **Maintainability**: Update original and re-clone if needed
✅ **Safe**: Each clone is independent (changing one doesn't affect others)
✅ **Validated**: Schema validation ensures compatibility

## Limitations

⚠️ **Independent Copies**: Cloned responses are separate instances. Updating the original doesn't update clones.
⚠️ **Manual Process**: You must explicitly trigger cloning (not automatic)
⚠️ **One-Time Operation**: After cloning, responses are disconnected from the source

## Future Enhancements

Potential improvements:
- [ ] Response templates with live references (changes to template affect all instances)
- [ ] Bulk clone to multiple endpoints at once
- [ ] Clone from endpoint in one backend to endpoint in another backend
- [ ] Clone history tracking (which responses were cloned from where)
- [ ] UI support in admin dashboard

## Complete Example

```bash
# Scenario: Create a RESTful API with consistent error handling

# 1. Create template endpoint for errors
curl -X POST http://localhost:8080/admin/api/mock-endpoints \
  -H "Content-Type: application/json" \
  -d '{
    "backendName": "templates",
    "method": "GET",
    "path": "/error-templates",
    "description": "Error response templates",
    "enabled": false
  }'
# Returns: { "id": 1000, ... }

# 2. Create standard error responses
curl -X POST http://localhost:8080/admin/api/mock-endpoints/1000/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "404 Not Found",
    "httpStatus": 404,
    "responseBody": "{\"error\":\"NotFound\",\"message\":\"The requested resource was not found\"}",
    "responseHeaders": "{\"Content-Type\":\"application/json\"}",
    "priority": 10,
    "enabled": true
  }'

curl -X POST http://localhost:8080/admin/api/mock-endpoints/1000/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "403 Forbidden",
    "httpStatus": 403,
    "responseBody": "{\"error\":\"Forbidden\",\"message\":\"You do not have permission to access this resource\"}",
    "responseHeaders": "{\"Content-Type\":\"application/json\"}",
    "priority": 10,
    "enabled": true
  }'

curl -X POST http://localhost:8080/admin/api/mock-endpoints/1000/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "500 Server Error",
    "httpStatus": 500,
    "responseBody": "{\"error\":\"InternalServerError\",\"message\":\"An unexpected error occurred\"}",
    "responseHeaders": "{\"Content-Type\":\"application/json\"}",
    "priority": 10,
    "enabled": true
  }'

# 3. Create actual endpoints
curl -X POST http://localhost:8080/admin/api/mock-endpoints \
  -H "Content-Type: application/json" \
  -d '{
    "backendName": "user-service",
    "method": "GET",
    "path": "/users",
    "enabled": true
  }'
# Returns: { "id": 1, ... }

curl -X POST http://localhost:8080/admin/api/mock-endpoints \
  -H "Content-Type: application/json" \
  -d '{
    "backendName": "user-service",
    "method": "GET",
    "path": "/users/{id}",
    "enabled": true
  }'
# Returns: { "id": 2, ... }

curl -X POST http://localhost:8080/admin/api/mock-endpoints \
  -H "Content-Type: application/json" \
  -d '{
    "backendName": "user-service",
    "method": "POST",
    "path": "/users",
    "enabled": true
  }'
# Returns: { "id": 3, ... }

# 4. Clone error responses to all endpoints
curl -X POST "http://localhost:8080/admin/api/mock-endpoints/1000/clone-responses?targetEndpointId=1"
curl -X POST "http://localhost:8080/admin/api/mock-endpoints/1000/clone-responses?targetEndpointId=2"
curl -X POST "http://localhost:8080/admin/api/mock-endpoints/1000/clone-responses?targetEndpointId=3"

# 5. Add endpoint-specific success responses
curl -X POST http://localhost:8080/admin/api/mock-endpoints/1/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Users List",
    "httpStatus": 200,
    "responseBody": "[{\"id\":1,\"name\":\"John\"},{\"id\":2,\"name\":\"Jane\"}]",
    "priority": 20,
    "enabled": true
  }'

curl -X POST http://localhost:8080/admin/api/mock-endpoints/2/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Single User",
    "httpStatus": 200,
    "responseBody": "{\"id\":1,\"name\":\"John\"}",
    "priority": 20,
    "enabled": true
  }'

curl -X POST http://localhost:8080/admin/api/mock-endpoints/3/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "User Created",
    "httpStatus": 201,
    "responseBody": "{\"id\":3,\"name\":\"New User\"}",
    "priority": 20,
    "enabled": true
  }'

# Now all endpoints have consistent error handling + their own success responses!
```

## Summary

The response cloning feature provides a **simple and effective way** to reuse response configurations across multiple endpoints while maintaining the clean OneToMany relationship architecture. It's perfect for:

- Standard error responses
- Common response patterns
- Multi-backend consistency
- Rapid mock setup

Each clone is **independent**, ensuring changes to one response don't unexpectedly affect others, while still providing the convenience of not having to manually recreate the same response multiple times.
