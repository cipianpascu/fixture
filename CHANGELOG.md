# Changelog

All notable changes to the Backend Gateway project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-03-14

### Added
- Initial release of Backend Gateway
- Dual-mode operation: Routing and Fixture modes
- Support for multiple security types (API Key, OAuth2, JWT, Basic Auth, mTLS)
- Security credentials pass-through from incoming requests
- Database-driven mock responses with dynamic matching
- Resilience patterns (Circuit Breaker, Retry, Timeout) using Resilience4j
- Request/Response logging with performance metrics
- Prometheus metrics integration
- Admin REST API for managing backends and mocks
- OpenAPI/Swagger documentation
- Docker and Docker Compose support
- Comprehensive README and Quick Start guide
- Example scripts for testing and mock creation
- Postman collection for API testing
- Unit tests for core services
- Global exception handling
- Health check endpoints
- H2 in-memory database support for routing mode
- PostgreSQL support for fixture mode

### Features by Mode

#### Routing Mode
- File-based backend configuration
- Real-time proxying to backend services
- Security header pass-through
- Circuit breaker protection
- Request logging and metrics

#### Fixture Mode
- Database-driven mock configuration
- Dynamic response matching based on:
  - Query parameters
  - Request headers
  - Request body content
- Priority-based response selection
- Configurable response delays
- Admin API for CRUD operations on:
  - Backend configurations
  - Mock endpoints
  - Mock responses

### Technical Stack
- Java 21
- Spring Boot 3.2.3
- Spring Data JPA
- Resilience4j 2.2.0
- PostgreSQL / H2
- Maven
- Docker

### Documentation
- Complete README with examples
- Quick Start guide
- API documentation via Swagger UI
- Postman collection
- Example shell scripts
- Docker Compose configuration

## [1.1.0] - 2026-03-18

### Added - OpenAPI Schema Support
- **OpenAPI 3.0 Schema Integration**: Per-backend schema storage and management
- **Automatic Mock Generation**: Generate endpoints and responses from OpenAPI specifications
- **Guided Mock Generation**: Specify exact values for properties with guided values system
  - Support for nested properties, arrays, and complex types
  - Customizable array sizes and specific item values
- **Schema Validation**: Validate mocks and requests against OpenAPI schemas
  - Three validation modes: STRICT, WARN, OFF (configurable per backend)
  - Request validation in fixture mode
  - Response validation when creating/updating mocks
  - Endpoint validation against schema
- **Per-Backend Swagger UI**: Interactive API documentation
  - Beautiful dashboard listing all backends with schemas
  - Full Swagger UI integration per backend
  - Live API testing with "Try it out" functionality
  - OpenAPI spec serving per backend
- **Schema Migration**: Handle schema updates with two strategies
  - DELETE_MOCKS: Remove all existing mocks
  - REVALIDATE_AND_DISABLE: Validate and disable incompatible mocks

### Admin API Endpoints Added
- `POST /admin/api/backends/{id}/schema` - Upload OpenAPI schema
- `GET /admin/api/backends/{id}/schema` - Retrieve schema
- `DELETE /admin/api/backends/{id}/schema` - Delete schema
- `POST /admin/api/backends/{id}/generate-schema` - **Generate OpenAPI schema from manual mocks** (Reverse generation)
- `GET /admin/api/backends/{id}/validate-mocks` - Validate all mocks
- `POST /admin/api/backends/{id}/generate-mocks` - Generate mocks with guided values
- `POST /admin/api/backends/{id}/generate-response` - Generate single response

### Swagger UI Endpoints Added
- `GET /api/backends/with-schemas` - List all backends with OpenAPI schemas (JSON)
- `GET /api/backends/{backendName}/openapi.json` - OpenAPI specification
- Standard Swagger UI: `/swagger-ui.html?url=/api/backends/{backendName}/openapi.json`

### Technical Changes
- Added `openApiSchema` field to BackendConfig entity (TEXT column)
- Added `validationMode` enum field to BackendConfig (STRICT, WARN, OFF)
- Created `ValidationMode` enum
- Created `OpenApiSchemaService` for schema parsing and validation
- Created `MockGeneratorService` for automatic mock generation
- Created `SchemaManagementService` for schema lifecycle management
- Created `SwaggerController` for per-backend OpenAPI spec serving
- Uses standard SpringDoc Swagger UI (no custom templates needed)
- Enhanced `MockService` with validation integration
- Enhanced `GatewayController` with request validation
- Updated test suite with new mock dependencies

### Documentation Added
- `docs/OPENAPI_SCHEMA_SUPPORT.md` - Complete guide with SpringDoc Swagger UI usage
- `docs/SCHEMA_FEATURE_SUMMARY.md` - Implementation summary
- Updated `README.md` with schema features and Swagger UI endpoints
- Updated `CHANGELOG.md` with version 1.1.0 release notes

### Files Created (9)
1. `src/main/java/com/agent/gateway/model/ValidationMode.java`
2. `src/main/java/com/agent/gateway/service/OpenApiSchemaService.java`
3. `src/main/java/com/agent/gateway/service/MockGeneratorService.java`
4. `src/main/java/com/agent/gateway/service/SchemaManagementService.java`
5. `src/main/java/com/agent/gateway/controller/SwaggerController.java`
6. `src/main/java/com/agent/gateway/dto/SchemaUploadRequest.java`
7. `src/main/java/com/agent/gateway/dto/GuidedMockGenerationRequest.java`
8. `docs/OPENAPI_SCHEMA_SUPPORT.md`
9. `docs/SCHEMA_FEATURE_SUMMARY.md`

### Benefits
- ✅ Contract-driven testing with schema validation
- ✅ Automatic mock generation from API specifications
- ✅ Interactive API documentation per backend
- ✅ Maintain consistency between mocks and real APIs
- ✅ Accelerate development with guided mock generation
- ✅ Self-documenting APIs via OpenAPI standards

## [Unreleased]

### Planned Features
- Admin Web UI for mock management
- Import/Export mock configurations
- Request recording and replay
- Mock versioning
- Performance benchmarking tools
- Support for WebSocket proxying
- GraphQL support
- Rate limiting per backend
- API key management
- Multi-tenancy support
- Schema versioning and history
- OpenAPI 3.1 support
