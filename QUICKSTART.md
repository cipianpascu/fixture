# Quick Start Guide

Get the Backend Gateway up and running in minutes!

## Option 1: Quick Start with Docker Compose (Recommended)

### 1. Start Both Modes Simultaneously
```bash
docker-compose up -d
```

This will start:
- **PostgreSQL database** on port 5432
- **Routing mode** on port 8080
- **Fixture mode** on port 8081

### 2. Verify Services
```bash
# Check routing mode
curl http://localhost:8080/actuator/health

# Check fixture mode
curl http://localhost:8081/actuator/health
```

### 3. Setup Mock Data (Fixture Mode)
```bash
cd examples
chmod +x create-mock-example.sh
./create-mock-example.sh
```

### 4. Test Fixture Mode
```bash
# Get users
curl http://localhost:8081/api/v1/user-service/users

# Get specific user
curl http://localhost:8081/api/v1/user-service/users/1
```

## Option 2: Run with Maven

### Prerequisites
- Java 21 installed
- PostgreSQL running (for fixture mode)

When running with Maven, the application listens on `http://localhost:8080` by default for either profile unless you pass `--server.port=...`.

### Routing Mode
```bash
# Uses H2 in-memory database
mvn spring-boot:run -Dspring-boot.run.profiles=routing
```

Test: `curl http://localhost:8080/api/v1/health`

### Fixture Mode
```bash
# Requires PostgreSQL
mvn spring-boot:run -Dspring-boot.run.profiles=fixture
```

Test: `curl http://localhost:8080/api/v1/health`

## Quick Testing

### Using Shell Scripts
```bash
cd examples
chmod +x test-requests.sh

# Test fixture mode
./test-requests.sh fixture

# Test routing mode
./test-requests.sh routing
```

### Using Postman
1. Import `examples/Backend-Gateway.postman_collection.json`
2. Set baseUrl variable to `http://localhost:8080` or `http://localhost:8081`
3. Run requests

### Manual Testing

#### Create a Backend (Admin API)
```bash
curl -X POST http://localhost:8080/admin/api/backends \
  -H "Content-Type: application/json" \
  -d '{
    "name": "test-service",
    "baseUrl": "http://example.com",
    "path": "/api",
    "securityType": "JWT",
    "securityConfig": "{}",
    "enabled": true
  }'
```

#### Create Mock Endpoint
```bash
curl -X POST http://localhost:8080/admin/api/mock-endpoints \
  -H "Content-Type: application/json" \
  -d '{
    "backendName": "test-service",
    "method": "GET",
    "path": "/hello",
    "description": "Test endpoint",
    "enabled": true
  }'
```

#### Create Mock Response (Replace {endpoint-id} with actual ID)
```bash
curl -X POST http://localhost:8080/admin/api/mock-endpoints/{endpoint-id}/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Success",
    "httpStatus": 200,
    "responseBody": "{\"message\":\"Hello World\"}",
    "responseHeaders": "{\"Content-Type\":\"application/json\"}",
    "priority": 10,
    "enabled": true
  }'
```

#### Test Your Mock
```bash
curl http://localhost:8080/api/v1/test-service/hello
```

## Access Swagger UI

Open your browser: `http://localhost:8080/swagger-ui/index.html`

For per-backend discovery, use `http://localhost:8080/api/backends/catalog` or `http://localhost:8080/api/backends/with-schemas`.
Per-backend OpenAPI documents are available at `http://localhost:8080/api/backends/{backendName}/openapi.json` and are always returned as JSON.
The verified per-backend deep link is `http://localhost:8080/swagger-ui/index.html?url=/api/backends/{backendName}/openapi.json`.

## Access H2 Console (Routing Mode Only)

Open your browser: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:routingdb`
- Username: `sa`
- Password: (leave empty)

## View Metrics

```bash
# Prometheus metrics
curl http://localhost:8080/actuator/prometheus

# Application metrics
curl http://localhost:8080/actuator/metrics

# Health details
curl http://localhost:8080/actuator/health
```

## Common Use Cases

### 1. Route to Real Backend (Routing Mode)
Configure in `application-routing.yml`, then:
```bash
curl http://localhost:8080/api/v1/{backend-name}/{endpoint} \
  -H "Authorization: Bearer your-token"
```

### 2. Mock API Response (Fixture Mode)
1. Create backend via Admin API
2. Create mock endpoint via Admin API
3. Create mock response(s) via Admin API
4. Call the mocked endpoint

### 3. Conditional Mock Responses
Create multiple responses with different priorities and match conditions:

#### Match by Query Parameters
**High priority response for specific query param:**
```json
{
  "name": "Admin users",
  "matchConditions": "{\"queryParams\":{\"role\":\"admin\"}}",
  "responseBody": "[{\"id\":1,\"role\":\"admin\"}]",
  "priority": 20
}
```

**Default response:**
```json
{
  "name": "All users",
  "matchConditions": "{}",
  "responseBody": "[{\"id\":1},{\"id\":2}]",
  "priority": 10
}
```

#### Match by Path Parameters
Return different responses based on path parameters like `{id}`:

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
    "responseBody": "{\"id\":1,\"name\":\"John\"}",
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
    "responseBody": "{\"id\":2,\"name\":\"Jane\"}",
    "priority": 20,
    "enabled": true
  }'

# 4. Test different IDs
curl http://localhost:8080/api/v1/user-service/users/1  # Returns John
curl http://localhost:8080/api/v1/user-service/users/2  # Returns Jane
```

#### Supported Match Conditions
```json
{
  "pathParams": {"id": "1", "orderId": "456"},
  "queryParams": {"role": "admin", "status": "active"},
  "headers": {"X-Custom-Header": "value"},
  "bodyContains": "search-term"
}
```

**Priority matters!** Higher priority responses are checked first. Use higher priority for more specific matches.

### 4. Clone Responses to Multiple Endpoints

Reuse responses across endpoints without duplication:

**Clone a single response to another endpoint:**
```bash
# Clone response ID 10 to endpoint ID 5
curl -X POST "http://localhost:8080/admin/api/mock-responses/10/clone?targetEndpointId=5"
```

**Clone all responses from one endpoint to another:**
```bash
# Clone all responses from endpoint 1 to endpoint 5
curl -X POST "http://localhost:8080/admin/api/mock-endpoints/1/clone-responses?targetEndpointId=5"
```

**Response:**
```json
{
  "clonedCount": 3,
  "responses": [...],
  "message": "Successfully cloned 3 responses"
}
```

**Use Case - Standard Error Responses:**
```bash
# 1. Create a template endpoint with standard errors
curl -X POST http://localhost:8080/admin/api/mock-endpoints \
  -H "Content-Type: application/json" \
  -d '{
    "backendName": "templates",
    "method": "GET",
    "path": "/error-templates",
    "enabled": false
  }'
# Returns: {"id": 999, ...}

# 2. Add standard error responses (404, 403, 500)
curl -X POST http://localhost:8080/admin/api/mock-endpoints/999/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "404 Not Found",
    "httpStatus": 404,
    "responseBody": "{\"error\":\"Resource not found\"}",
    "priority": 10,
    "enabled": true
  }'

# 3. Clone to all your endpoints
curl -X POST "http://localhost:8080/admin/api/mock-endpoints/999/clone-responses?targetEndpointId=1"
curl -X POST "http://localhost:8080/admin/api/mock-endpoints/999/clone-responses?targetEndpointId=2"
# Now all endpoints have consistent error handling!
```

## Stopping Services

### Docker Compose
```bash
docker-compose down

# Remove volumes (careful - deletes database data!)
docker-compose down -v
```

### Maven
Press `Ctrl+C` in the terminal

## Troubleshooting

### Port Already in Use
```bash
# Check what's using port 8080
lsof -i :8080

# Kill the process or change the port in application.yml
```

### Database Connection Failed (Fixture Mode)
```bash
# Check PostgreSQL is running
pg_isready -h localhost -p 5432

# Or start with Docker
docker run -d -p 5432:5432 \
  -e POSTGRES_DB=backend_gateway \
  -e POSTGRES_PASSWORD=postgres \
  postgres:15-alpine
```

### Can't Find Mock Response
- Check backend name matches exactly
- Verify endpoint path and method
- Ensure mock response is enabled
- Review match conditions

## Next Steps

1. Read the full [README.md](README.md) for detailed documentation
2. Explore the Admin API via Swagger UI
3. Learn about [Match Conditions](docs/MATCH_CONDITIONS.md) for advanced request matching (path params, query params, headers)
4. Learn about [Response Cloning](docs/RESPONSE_CLONING.md) for reusing responses across endpoints
5. Set up your production backends in routing mode
6. Create comprehensive mocks for your test environments
7. Integrate with your CI/CD pipeline

## Support

For issues and questions:
- Check the [README.md](README.md) troubleshooting section
- Review application logs in `logs/backend-gateway.log`
- Create an issue in the repository

Happy testing! 🚀
