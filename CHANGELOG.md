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

## [Unreleased]

### Planned Features
- Admin Web UI for mock management
- Import/Export mock configurations
- OpenAPI schema-based mock generation
- Request recording and replay
- Mock versioning
- Performance benchmarking tools
- Support for WebSocket proxying
- GraphQL support
- Rate limiting per backend
- API key management
- Multi-tenancy support
