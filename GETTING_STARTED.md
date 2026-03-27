# Getting Started with Backend Gateway

Welcome! This guide will help you get up and running with the Backend Gateway in under 10 minutes.

## 🚀 Super Quick Start (2 minutes)

```bash
# Clone and navigate
cd /work/WorkGit/bfa

# Start everything with Docker
docker-compose up -d

# Wait 30 seconds, then test
curl http://localhost:8080/api/v1/health  # Routing mode
curl http://localhost:8081/api/v1/health  # Fixture mode
```

✅ **Done!** You now have both modes running.

## 📚 What You Just Started

- **Routing Mode** (port 8080): Routes requests to real backends
- **Fixture Mode** (port 8081): Serves mock responses
- **PostgreSQL** (port 5432): Database for fixture mode

## 🎯 Next Steps (Choose Your Path)

### Path A: I Want to Test Mocking (Fixture Mode)

```bash
# 1. Create sample mocks
cd examples
chmod +x create-mock-example.sh
./create-mock-example.sh

# 2. Test the mocks
curl http://localhost:8081/api/v1/user-service/users
curl http://localhost:8081/api/v1/user-service/users/1
```

### Path B: I Want to Route to My Backend

```bash
# 1. Edit routing configuration
nano src/main/resources/application-routing.yml

# 2. Add your backend:
# backends:
#   - name: my-service
#     baseUrl: http://my-backend:8080
#     path: /api/v1
#     securityType: JWT
#     enabled: true

# 3. Restart routing mode
docker-compose restart backend-gateway-routing

# 4. Test
curl http://localhost:8080/api/v1/my-service/endpoint \
  -H "Authorization: Bearer your-token"
```

### Path C: I Want to Explore the API

Open in browser:
- **Swagger UI**: http://localhost:8080/swagger-ui/index.html
- **API Docs**: http://localhost:8080/api-docs
- **Backend OpenAPI Catalog**: http://localhost:8080/api/backends/catalog
- **Health**: http://localhost:8080/actuator/health
- **Metrics**: http://localhost:8080/actuator/metrics

If you start the app locally with Maven instead of Docker, both `routing` and `fixture` profiles default to port `8080` unless you override `server.port`.

## 🛠 Development Setup

### Option 1: Docker (Recommended)
Already done in Quick Start! ✅

### Option 2: Local with Maven

```bash
# Prerequisites
java -version  # Should be Java 21
mvn -version   # Should be Maven 3.6+

# Build
mvn clean install

# Run routing mode
mvn spring-boot:run -Dspring-boot.run.profiles=routing

# Or run fixture mode (requires PostgreSQL)
mvn spring-boot:run -Dspring-boot.run.profiles=fixture
```

### Option 3: IDE (IntelliJ/Eclipse/VS Code)

1. **Import Project**: File → Open → Select `/work/WorkGit/bfa/pom.xml`
2. **Wait for dependencies** to download
3. **Run Configuration**:
   - Main class: `com.agent.gateway.BackendGatewayApplication`
   - VM options: `-Dspring.profiles.active=routing` or `fixture`
4. **Run** the application

## 📖 Understanding the Modes

### Routing Mode 🔀
**Use Case**: Production environments

**What it does**:
- Forwards requests to real backend services
- Passes through authentication headers
- Provides circuit breaker protection
- Logs all requests/responses

**Configuration**: `application-routing.yml`

### Fixture Mode 🎭
**Use Case**: Development/Testing environments

**What it does**:
- Returns mock responses from database
- Matches requests dynamically
- Simulates network latency
- Manages mocks via Admin API

**Configuration**: Database-driven via Admin API

## 🎮 Common Tasks

### Task 1: Create a New Mock

```bash
# 1. Create backend
curl -X POST http://localhost:8081/admin/api/backends \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-service",
    "baseUrl": "http://example.com",
    "path": "/api",
    "securityType": "JWT",
    "enabled": true
  }'

# 2. Create endpoint (assuming backend ID is 1)
curl -X POST http://localhost:8081/admin/api/mock-endpoints \
  -H "Content-Type: application/json" \
  -d '{
    "backendName": "my-service",
    "method": "GET",
    "path": "/data",
    "description": "Get data",
    "enabled": true
  }'

# 3. Create response (assuming endpoint ID is 1)
curl -X POST http://localhost:8081/admin/api/mock-endpoints/1/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Success",
    "httpStatus": 200,
    "responseBody": "{\"result\":\"success\"}",
    "responseHeaders": "{\"Content-Type\":\"application/json\"}",
    "priority": 10,
    "enabled": true
  }'

# 4. Test
curl http://localhost:8081/api/v1/my-service/data
```

### Task 2: View All Mocks

```bash
# List backends
curl http://localhost:8081/admin/api/backends | jq

# List endpoints
curl http://localhost:8081/admin/api/mock-endpoints | jq

# List responses for endpoint 1
curl http://localhost:8081/admin/api/mock-endpoints/1/responses | jq
```

### Task 3: Update a Mock Response

```bash
curl -X PUT http://localhost:8081/admin/api/mock-responses/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Updated Response",
    "httpStatus": 200,
    "responseBody": "{\"result\":\"updated\"}",
    "priority": 10,
    "enabled": true
  }'
```

### Task 4: Add Conditional Response

```bash
# High priority response for admin users
curl -X POST http://localhost:8081/admin/api/mock-endpoints/1/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Admin Response",
    "matchConditions": "{\"headers\":{\"X-User-Role\":\"admin\"}}",
    "httpStatus": 200,
    "responseBody": "{\"data\":\"admin-only-data\"}",
    "responseHeaders": "{\"Content-Type\":\"application/json\"}",
    "priority": 20,
    "enabled": true
  }'

# Test
curl http://localhost:8081/api/v1/my-service/data \
  -H "X-User-Role: admin"
```

## 📦 Using Postman

1. **Import Collection**:
   - Open Postman
   - Import → File → `examples/Backend-Gateway.postman_collection.json`

2. **Set Base URL**:
   - Variables → `baseUrl` → `http://localhost:8080` or `8081`

3. **Explore Requests**:
   - Gateway Endpoints
   - Admin API
   - Actuator

## 🧪 Running Tests

```bash
# Unit tests
mvn test

# With coverage
mvn test jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

## 📊 Monitoring

### View Metrics

```bash
# Prometheus format
curl http://localhost:8080/actuator/prometheus

# JSON format
curl http://localhost:8080/actuator/metrics

# Specific metric
curl http://localhost:8080/actuator/metrics/http.server.requests
```

### Health Checks

```bash
# Overall health
curl http://localhost:8080/actuator/health

# Detailed health (includes components)
curl http://localhost:8080/actuator/health | jq
```

## 🐛 Troubleshooting

### Issue: Port already in use

```bash
# Find and kill process using port 8080
lsof -ti:8080 | xargs kill -9

# Or change port
export SERVER_PORT=8081
```

### Issue: Database connection failed

```bash
# Check PostgreSQL is running
docker ps | grep postgres

# Start if not running
docker-compose up -d postgres

# Check logs
docker-compose logs postgres
```

### Issue: Mock not returning expected response

```bash
# Check mock exists
curl http://localhost:8081/admin/api/mock-endpoints | jq '.[] | select(.path=="/your-path")'

# Check responses are enabled
curl http://localhost:8081/admin/api/mock-endpoints/{id}/responses | jq

# Check application logs
docker-compose logs -f backend-gateway-fixture
```

## 📚 Learning Resources

### Documentation
- **README.md** - Complete project documentation
- **QUICKSTART.md** - Detailed quick start guide
- **docs/ARCHITECTURE.md** - System architecture
- **docs/DEPLOYMENT.md** - Deployment guide
- **CONTRIBUTING.md** - How to contribute

### Examples
- **examples/create-mock-example.sh** - Mock creation script
- **examples/test-requests.sh** - Testing script
- **examples/Backend-Gateway.postman_collection.json** - Postman collection

### Interactive
- **Swagger UI**: http://localhost:8080/swagger-ui/index.html
- **Backend OpenAPI Catalog**: http://localhost:8080/api/backends/catalog
- **H2 Console** (routing mode): http://localhost:8080/h2-console

## 🎓 Concepts to Understand

### 1. Backend Configuration
A backend represents a real service that you want to route to or mock.

```yaml
name: user-service          # Unique identifier
baseUrl: http://...         # Where to route requests
path: /api/v1/users        # Base path
securityType: JWT          # Authentication type
```

### 2. Mock Endpoint
Defines an API endpoint to mock.

```json
{
  "backendName": "user-service",
  "method": "GET",
  "path": "/users/{id}",
  "enabled": true
}
```

### 3. Mock Response
The response to return for a mock endpoint.

```json
{
  "name": "Success",
  "httpStatus": 200,
  "responseBody": "{}",
  "priority": 10,          // Higher = checked first
  "matchConditions": "{}", // When to use this response
  "delayMs": 100          // Simulate latency
}
```

### 4. Match Conditions
Control when a response is used.

```json
{
  "queryParams": {"status": "active"},  // Match query param
  "headers": {"X-Role": "admin"},       // Match header
  "bodyContains": "search_term"         // Match body content
}
```

## 🚦 What's Next?

### For Testing/Development
1. ✅ Create mocks for your APIs
2. ✅ Set up conditional responses
3. ✅ Configure response delays
4. ✅ Share mock configurations with team

### For Production
1. ✅ Configure real backend URLs
2. ✅ Set up proper authentication
3. ✅ Configure circuit breakers
4. ✅ Set up monitoring dashboards
5. ✅ Deploy to Kubernetes/Cloud

## 💡 Pro Tips

1. **Use priority wisely**: Higher priority for specific cases, lower for defaults
2. **Test match conditions**: Start simple, add complexity as needed
3. **Monitor metrics**: Watch for circuit breaker trips
4. **Version your mocks**: Use git to track mock database changes
5. **Use meaningful names**: Makes debugging much easier

## 🆘 Getting Help

- **Documentation**: Check the docs/ folder
- **Examples**: Look in examples/ folder
- **Logs**: `docker-compose logs -f`
- **Health**: `curl http://localhost:8080/actuator/health`
- **Issues**: Create a GitHub issue

## 🎉 Success Checklist

- [ ] Application starts successfully
- [ ] Can access Swagger UI
- [ ] Health endpoint returns 200
- [ ] Created a backend via Admin API
- [ ] Created a mock endpoint
- [ ] Created a mock response
- [ ] Tested mock and got expected response
- [ ] Viewed metrics at `/actuator/metrics`
- [ ] Explored Postman collection

Congratulations! You're now ready to use the Backend Gateway! 🚀

---

**Need more details?** Check out [QUICKSTART.md](QUICKSTART.md) or [README.md](README.md)

**Ready to deploy?** See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)

**Want to understand the architecture?** Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
