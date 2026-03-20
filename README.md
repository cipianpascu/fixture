# Backend Gateway for Agents

A Spring Boot-based API Gateway that serves dual purposes:
1. **Routing Mode**: Acts as an API gateway (similar to Apigee) routing requests to multiple backend services with security pass-through
2. **Fixture Mode**: Serves as a mocking/fixture service with database-driven dynamic responses for lower environments

## Features

### Core Capabilities
- ✅ Dual mode operation (Routing vs Fixture)
- ✅ Support for multiple authentication types (API Key, OAuth2, JWT, Basic Auth, mTLS)
- ✅ Security credentials pass-through from incoming requests
- ✅ **OpenAPI 3.0 Schema Support** - Auto-generate mocks, validate data, document APIs
- ✅ Database-driven mock responses with dynamic matching
- ✅ Guided mock generation with custom values
- ✅ Request/Response validation against OpenAPI schemas
- ✅ Resilience patterns (Circuit Breaker, Retry, Timeout)
- ✅ Request/Response logging and monitoring
- ✅ Prometheus metrics integration
- ✅ Admin API for mock management
- ✅ OpenAPI/Swagger documentation

### Technology Stack
- **Java 21**
- **Spring Boot 3.2.3**
- **Spring Data JPA**
- **PostgreSQL** (fixture mode) / **H2** (routing mode)
- **Resilience4j** (Circuit breaker, retry, timeout)
- **Lombok**
- **Maven**

## Quick Start

Port note:
When you run the application directly with Maven, it listens on `http://localhost:8080` by default for both profiles unless you override `server.port`.
When you run `docker-compose up`, routing mode is exposed on `http://localhost:8080` and fixture mode is exposed on `http://localhost:8081`.

### Prerequisites
- Java 21
- Maven 3.6+
- PostgreSQL 12+ (for fixture mode)

### Build
```bash
mvn clean install
```

### Running in Routing Mode
```bash
# Using H2 in-memory database
mvn spring-boot:run -Dspring-boot.run.profiles=routing
```

### Running in Fixture Mode
```bash
# Requires PostgreSQL database
mvn spring-boot:run -Dspring-boot.run.profiles=fixture
```

## Configuration

### Routing Mode Configuration
In routing mode, backends are configured via `application-routing.yml`:

```yaml
gateway:
  mode: routing
  backends:
    - name: backend-service-1
      baseUrl: http://localhost:9001
      path: /api/v1/service1
      securityType: API_KEY
      securityConfig:
        headerName: X-API-Key
      enabled: true
```

### Fixture Mode Configuration
In fixture mode, backends and mocks are managed via database using the Admin API.

### Database Configuration
Update `application.yml` or environment variables:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/backend_gateway
    username: postgres
    password: postgres
```

## API Usage

### Gateway Endpoints

#### Forward Request to Backend
```bash
# Request format: /api/v1/{backendName}/{remainingPath}
curl -X GET http://localhost:8080/api/v1/backend-service-1/users \
  -H "X-API-Key: your-api-key"
```

#### Health Check
```bash
curl http://localhost:8080/api/v1/health
```

### Admin API (Fixture Mode)

#### Backend Management

**Get All Backends**
```bash
curl http://localhost:8080/admin/api/backends
```

**Create Backend**
```bash
curl -X POST http://localhost:8080/admin/api/backends \
  -H "Content-Type: application/json" \
  -d '{
    "name": "user-service",
    "baseUrl": "http://localhost:9001",
    "path": "/api/v1/users",
    "securityType": "OAUTH2",
    "securityConfig": "{}",
    "enabled": true
  }'
```

**Update Backend**
```bash
curl -X PUT http://localhost:8080/admin/api/backends/1 \
  -H "Content-Type: application/json" \
  -d '{
    "baseUrl": "http://localhost:9002",
    "path": "/api/v2/users",
    "securityType": "JWT",
    "enabled": true
  }'
```

**Delete Backend**
```bash
curl -X DELETE http://localhost:8080/admin/api/backends/1
```

#### Mock Endpoint Management

**Create Mock Endpoint**
```bash
curl -X POST http://localhost:8080/admin/api/mock-endpoints \
  -H "Content-Type: application/json" \
  -d '{
    "backendName": "user-service",
    "method": "GET",
    "path": "/users/{id}",
    "description": "Get user by ID",
    "enabled": true
  }'
```

**Get Mock Endpoints by Backend**
```bash
curl http://localhost:8080/admin/api/mock-endpoints/backend/user-service
```

#### Mock Response Management

**Create Mock Response**
```bash
curl -X POST http://localhost:8080/admin/api/mock-endpoints/1/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Success Response",
    "matchConditions": "{\"queryParams\":{\"status\":\"active\"}}",
    "httpStatus": 200,
    "responseBody": "{\"id\":1,\"name\":\"John Doe\",\"email\":\"john@example.com\"}",
    "responseHeaders": "{\"Content-Type\":\"application/json\"}",
    "priority": 10,
    "enabled": true,
    "delayMs": 100
  }'
```

**Update Mock Response**
```bash
curl -X PUT http://localhost:8080/admin/api/mock-responses/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Updated Response",
    "httpStatus": 200,
    "responseBody": "{\"id\":1,\"name\":\"Jane Doe\"}",
    "priority": 5,
    "enabled": true
  }'
```

## Mock Response Matching

Mock responses support dynamic matching based on:
- **Query Parameters**: Match specific query parameter values
- **Headers**: Match request header values
- **Body Content**: Match if request body contains specific text

### Match Conditions Example
```json
{
  "queryParams": {
    "status": "active",
    "role": "admin"
  },
  "headers": {
    "X-User-Type": "premium"
  },
  "bodyContains": "special_field"
}
```

Responses are evaluated by priority (highest first), and the first matching response is returned.

## OpenAPI Schema Support

Backend Gateway now supports OpenAPI 3.0 schemas for each backend, enabling powerful features:

### Features
- **Auto-generate mocks** from OpenAPI specifications
- **Guided mock generation** with custom values for specific fields
- **Validate mock responses** against schema definitions
- **Validate incoming requests** in fixture mode
- **Configurable validation** modes (STRICT, WARN, OFF)

### Upload Schema

```bash
curl -X POST http://localhost:8080/admin/api/backends/1/schema \
  -F "schemaFile=@openapi.yaml" \
  -F "migrationOption=REVALIDATE_AND_DISABLE"
```

### Generate Schema from Manual Mocks

```bash
POST /admin/api/backends/1/generate-schema
```

Automatically generates OpenAPI 3.0 schema from existing mock endpoints and responses. Perfect for documenting manually created APIs!

### Generate Mocks from Schema

```bash
POST /admin/api/backends/1/generate-mocks
Content-Type: application/json

{
  "generateEndpoints": true,
  "generateResponses": true,
  "guidedValues": {
    "id": 1,
    "name": "John Doe",
    "users[].__size": 10
  }
}
```

### Generate Responses for One Existing Mock Endpoint

```bash
POST /admin/api/mock-endpoints/1/generate-responses
Content-Type: application/json

{
  "id": 1,
  "name": "John Doe",
  "users[].__size": 10
}
```

This generates schema-based `MockResponse` records for one already-existing `MockEndpoint`.
### Validation Modes
{{ ... }}
- **WARN**: Allow operations but log warnings  
- **OFF**: No validation (default)

**See [OpenAPI Schema Support Documentation](docs/OPENAPI_SCHEMA_SUPPORT.md) for detailed information.**

## Monitoring & Metrics

### Actuator Endpoints
- Health: `http://localhost:8080/actuator/health`
- Metrics: `http://localhost:8080/actuator/metrics`
- Prometheus: `http://localhost:8080/actuator/prometheus`

### Swagger UI

**Global Gateway Documentation:**
- Main Swagger UI: `http://localhost:8080/swagger-ui.html`
- Global `/api-docs` documents the admin API and discovery endpoints. The catch-all gateway dispatcher is intentionally not exposed as a concrete OpenAPI operation.

**Per-Backend OpenAPI Documentation:**
- Agent-friendly catalog: `http://localhost:8080/api/backends/catalog`
- List backends with schemas: `http://localhost:8080/api/backends/with-schemas`
- Get backend OpenAPI spec: `http://localhost:8080/api/backends/{backendName}/openapi.json`
- View in Swagger UI: `http://localhost:8080/swagger-ui.html?url=/api/backends/{backendName}/openapi.json`

Each backend with an uploaded OpenAPI schema can be viewed in the standard Swagger UI by specifying its OpenAPI URL.
The catalog and `with-schemas` endpoints are the intended discovery surface for humans and agent clients.
`/api/backends/{backendName}/openapi.json` always returns JSON, even when the schema was originally uploaded in YAML.

## Resilience Configuration

Circuit breaker, retry, and timeout configurations are in `application.yml`:

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
```

## Security Types Supported

- **API_KEY**: Pass API key via custom header
- **OAUTH2**: Pass OAuth2 token via Authorization header
- **JWT**: Pass JWT token via Authorization header
- **BASIC_AUTH**: Pass Basic auth credentials via Authorization header
- **MTLS**: Mutual TLS authentication
- **NONE**: No authentication

All security credentials are passed through from incoming requests to backend services.

## Project Structure

```
backend-gateway/
├── src/main/java/com/agent/gateway/
│   ├── config/              # Configuration classes
│   ├── controller/          # REST controllers
│   ├── entity/              # JPA entities
│   ├── model/               # Enums and models
│   ├── repository/          # JPA repositories
│   └── service/             # Business logic
├── src/main/resources/
│   ├── application.yml      # Base configuration
│   ├── application-routing.yml   # Routing mode config
│   └── application-fixture.yml   # Fixture mode config
└── pom.xml                  # Maven dependencies
```

## Development

### Running Tests
```bash
mvn test
```

### Building Docker Image
```bash
docker build -t backend-gateway:latest .
```

### Environment Variables
Key environment variables:
- `SPRING_PROFILES_ACTIVE`: Set to `routing` or `fixture`
- `SPRING_DATASOURCE_URL`: Database connection URL
- `SPRING_DATASOURCE_USERNAME`: Database username
- `SPRING_DATASOURCE_PASSWORD`: Database password

## Troubleshooting

### Database Connection Issues
Ensure PostgreSQL is running and credentials are correct:
```bash
psql -U postgres -h localhost -d backend_gateway
```

### Circuit Breaker Opening
Check backend service health and adjust resilience configuration if needed.

### Mock Not Matching
- Verify backend name matches exactly
- Check method and path pattern
- Review match conditions JSON format
- Check priority ordering

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License.

## Support

For issues and questions, please create an issue in the repository.
