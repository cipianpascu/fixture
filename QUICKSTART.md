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
    "mockEndpointId": {endpoint-id},
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
3. Set up your production backends in routing mode
4. Create comprehensive mocks for your test environments
5. Integrate with your CI/CD pipeline

## Support

For issues and questions:
- Check the [README.md](README.md) troubleshooting section
- Review application logs in `logs/backend-gateway.log`
- Create an issue in the repository

Happy testing! 🚀
