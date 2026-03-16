# Backend Gateway - Architecture

## Overview

Backend Gateway is a dual-mode API gateway service that can operate either as a routing gateway (production) or as a mocking service (lower environments). This document describes the architecture, design decisions, and component interactions.

## System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Client Applications                    │
└────────────────────────┬────────────────────────────────────┘
                         │
                         │ HTTP/HTTPS Requests
                         │
┌────────────────────────▼────────────────────────────────────┐
│                    Backend Gateway                           │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              Gateway Controller                       │  │
│  │    (Request Routing & Mode Detection)                │  │
│  └───────────┬──────────────────────┬───────────────────┘  │
│              │                      │                       │
│    ┌─────────▼─────────┐  ┌────────▼──────────┐           │
│    │  Routing Mode     │  │  Fixture Mode      │           │
│    │  (ProxyService)   │  │  (MockService)     │           │
│    └─────────┬─────────┘  └────────┬───────────┘           │
│              │                      │                       │
│    ┌─────────▼─────────┐  ┌────────▼───────────┐          │
│    │  Resilience4j     │  │  Database          │          │
│    │  - Circuit Breaker│  │  - Mock Endpoints  │          │
│    │  - Retry          │  │  - Mock Responses  │          │
│    │  - Timeout        │  │  - Backend Configs │          │
│    └─────────┬─────────┘  └────────────────────┘          │
│              │                                              │
│  ┌───────────▼────────────────────────────────────────┐   │
│  │          Logging & Monitoring                       │   │
│  │  - Request/Response Logging                        │   │
│  │  - Prometheus Metrics                              │   │
│  │  - Health Checks                                   │   │
│  └─────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                         │
         ┌───────────────┴───────────────┐
         │                               │
┌────────▼────────┐            ┌────────▼────────┐
│  Backend APIs   │            │   PostgreSQL    │
│  (Production)   │            │   (Fixture DB)  │
└─────────────────┘            └─────────────────┘
```

## Component Architecture

### 1. Controller Layer

#### GatewayController
- **Responsibility**: Main entry point for all gateway requests
- **Path Pattern**: `/api/v1/{backendName}/**`
- **Functions**:
  - Parse incoming requests
  - Extract backend name and path
  - Route to appropriate mode handler
  - Return responses to clients

#### AdminController
- **Responsibility**: Administrative API for mock management
- **Path Pattern**: `/admin/api/**`
- **Functions**:
  - CRUD operations for backends
  - CRUD operations for mock endpoints
  - CRUD operations for mock responses
  - Only active in fixture mode

### 2. Service Layer

#### ProxyService (Routing Mode)
- **Responsibility**: Forward requests to real backend services
- **Key Features**:
  - HTTP request proxying using RestTemplate
  - Security header pass-through
  - Circuit breaker protection
  - Retry mechanism
  - Timeout handling
  - Request/response logging

**Flow Diagram:**
```
Request → Validate Backend → Copy Headers → Apply Security 
  → Circuit Breaker → Retry → Timeout → Forward to Backend 
  → Return Response
```

#### MockService (Fixture Mode)
- **Responsibility**: Serve mock responses based on database configuration
- **Key Features**:
  - Dynamic endpoint matching
  - Conditional response selection
  - Priority-based response ordering
  - Request parameter matching
  - Simulated latency

**Matching Algorithm:**
1. Find enabled endpoints for backend
2. Match HTTP method and path pattern
3. Get responses ordered by priority (DESC)
4. Evaluate match conditions for each response
5. Return first matching response
6. Apply configured delay

**Match Condition Example:**
```json
{
  "queryParams": {"status": "active"},
  "headers": {"X-User-Type": "premium"},
  "bodyContains": "special_field"
}
```

#### BackendConfigService
- **Responsibility**: Manage backend configurations
- **Functions**:
  - Initialize backends from config file (routing mode)
  - CRUD operations for backends
  - Backend validation
  - Enable/disable backends

### 3. Data Layer

#### Entities

**BackendConfig**
- Stores backend service definitions
- Fields: name, baseUrl, path, securityType, securityConfig, enabled
- Used in both modes

**MockEndpoint**
- Defines mock API endpoints
- Fields: backendName, method, path, openApiSchema, enabled
- One-to-many relationship with MockResponse

**MockResponse**
- Stores mock response data
- Fields: httpStatus, responseBody, responseHeaders, matchConditions, priority, delayMs
- Many-to-one relationship with MockEndpoint

#### Repositories
- Standard Spring Data JPA repositories
- Custom query methods for specific lookups
- Optimized queries with proper indexing

### 4. Configuration Layer

#### GatewayProperties
- Loads gateway configuration from YAML
- Defines mode (ROUTING or FIXTURE)
- Backend definitions for routing mode

#### Profile-based Configuration
- **application.yml**: Base configuration
- **application-routing.yml**: Routing mode specific
- **application-fixture.yml**: Fixture mode specific

### 5. Cross-Cutting Concerns

#### Exception Handling
- Global exception handler (`@RestControllerAdvice`)
- Standardized error response format
- Proper HTTP status code mapping

#### Logging
- Request/Response interceptor
- Performance timing
- SLF4J with Logback
- Configurable log levels per package

#### Monitoring
- Spring Boot Actuator endpoints
- Prometheus metrics export
- Custom metrics for gateway operations
- Health checks for dependencies

#### Resilience
- Circuit Breaker: Protects against cascading failures
- Retry: Handles transient failures
- Timeout: Prevents hung requests
- Configurable per backend (future enhancement)

## Design Decisions

### 1. Dual-Mode Operation

**Why?**
- Single codebase for both production and testing
- Consistent behavior and configuration
- Simplified deployment and maintenance

**How?**
- Profile-based configuration
- Strategy pattern for mode selection
- Conditional bean creation

### 2. Database-Driven Mocks (Fixture Mode)

**Why?**
- Dynamic mock updates without redeployment
- Admin UI/API for non-technical users
- Version control for mock configurations
- Complex matching scenarios

**Trade-offs:**
- Requires database setup
- Slightly more complex than file-based mocks
- Need for database backups

### 3. Security Pass-Through

**Why?**
- Simplifies gateway logic
- Maintains end-to-end security
- Supports all authentication types
- No credential storage required

**Implementation:**
- Copy all headers (except Host)
- No token validation at gateway
- Backend services handle authentication
- Support for mTLS pass-through

### 4. Resilience4j Integration

**Why?**
- Industry-standard resilience library
- Lightweight and performant
- Excellent Spring Boot integration
- Comprehensive feature set

**Patterns Used:**
- **Circuit Breaker**: Fail fast when backend is down
- **Retry**: Automatic retry for transient failures
- **Timeout**: Prevent indefinite waits

### 5. Match Conditions for Mocks

**Why?**
- Single endpoint, multiple scenarios
- Dynamic behavior based on request
- Support for A/B testing
- Conditional responses

**Priority System:**
- Higher priority checked first
- Fallback to default response
- Enables specific + general patterns

## Data Flow

### Routing Mode Request Flow
```
1. Client Request → GatewayController
2. Extract backend name and path
3. Load BackendConfig from database
4. Validate backend is enabled
5. ProxyService.proxyRequest()
6. Copy request headers
7. Apply security configuration
8. Circuit Breaker wraps request
9. Retry on failure
10. Timeout protection
11. Forward to backend via RestTemplate
12. Return response to client
```

### Fixture Mode Request Flow
```
1. Client Request → GatewayController
2. Extract backend name, method, path
3. MockService.findMatchingResponse()
4. Query enabled mock endpoints
5. Match path pattern
6. Get responses by priority
7. Evaluate match conditions
8. Select first matching response
9. Apply configured delay
10. Build response with headers
11. Return mock response to client
```

### Admin API Flow (Fixture Mode)
```
1. Admin Request → AdminController
2. Validate request body
3. Service layer performs business logic
4. Database transaction
5. Return created/updated entity
6. Client receives confirmation
```

## Security Considerations

### Authentication Types Supported
- **API Key**: Custom header-based
- **OAuth2**: Bearer token
- **JWT**: JSON Web Token
- **Basic Auth**: Base64-encoded credentials
- **mTLS**: Certificate-based (pass-through)
- **None**: No authentication

### Security Best Practices
- No credential storage in gateway
- Pass-through authentication to backends
- HTTPS recommended for production
- Secure database connections
- No sensitive data in logs

## Scalability

### Horizontal Scaling
- Stateless design
- No session management
- Database handles state (fixture mode)
- Load balancer ready

### Performance Optimization
- Connection pooling (RestTemplate)
- JPA query optimization
- Lazy loading for relationships
- Response caching (future enhancement)

### Resource Management
- Configurable timeout values
- Circuit breaker prevents resource exhaustion
- Thread pool management via Spring Boot

## Future Enhancements

### Planned Features
1. **Admin Web UI**: Visual interface for mock management
2. **OpenAPI Import**: Auto-generate mocks from OpenAPI specs
3. **Request Recording**: Capture real responses for mocks
4. **Mock Versioning**: Track changes over time
5. **Rate Limiting**: Per-backend rate limits
6. **Advanced Routing**: Load balancing, canary releases
7. **WebSocket Support**: Proxy WebSocket connections
8. **GraphQL Support**: Specialized GraphQL handling
9. **Response Templating**: Dynamic response generation
10. **Analytics Dashboard**: Usage statistics and insights

## Deployment Architecture

### Development
```
Developer Machine → H2/PostgreSQL → Local Backend Gateway
```

### Testing
```
CI/CD Pipeline → Docker Container → PostgreSQL → Mock Backend Gateway
```

### Production
```
Load Balancer → Multiple Gateway Instances → Real Backend Services
                       ↓
                  PostgreSQL (Config only)
```

## Technology Choices

| Component | Technology | Reason |
|-----------|-----------|--------|
| Language | Java 21 | Modern Java features, performance, ecosystem |
| Framework | Spring Boot 3.2.3 | Production-ready, comprehensive features |
| Database | PostgreSQL/H2 | Reliability, JSON support, in-memory option |
| Resilience | Resilience4j | Modern, lightweight, Spring integration |
| HTTP Client | RestTemplate | Spring native, simple API |
| Metrics | Micrometer/Prometheus | Industry standard, flexible |
| Build Tool | Maven | Mature, widely used, dependency management |
| Testing | JUnit 5, Mockito | Standard Java testing tools |
| Documentation | OpenAPI/Swagger | Interactive API docs |

## Monitoring and Observability

### Metrics Collected
- Request count per backend
- Response times (p50, p95, p99)
- Error rates
- Circuit breaker states
- Active connections
- JVM metrics

### Health Checks
- Application health
- Database connectivity
- Backend reachability (optional)

### Logging Strategy
- Structured logging
- Request correlation IDs
- Performance metrics
- Error tracking with stack traces

## Conclusion

The Backend Gateway provides a flexible, resilient solution for API routing and mocking. Its dual-mode architecture allows it to serve both production and testing needs with a single codebase, while maintaining high performance and reliability standards.
