# OpenAPI Schema Support

The Backend Gateway now supports OpenAPI 3.0 schemas per backend, enabling automatic mock generation, validation, and API documentation.

## Table of Contents
- [Overview](#overview)
- [Features](#features)
- [Configuration](#configuration)
- [Schema Upload](#schema-upload)
- [Mock Generation](#mock-generation)
- [Validation](#validation)
- [Per-Backend Swagger UI](#per-backend-swagger-ui)
- [API Examples](#api-examples)
- [Best Practices](#best-practices)

## Overview

Each backend can have an associated OpenAPI 3.0 specification that:
- **Describes the API** structure and contracts
- **Generates mock endpoints** automatically from the schema
- **Creates mock responses** with guided values
- **Validates mock data** against the schema
- **Validates incoming requests** in fixture mode

## Features

### 1. Schema Storage
- OpenAPI 3.0 schema stored in `BackendConfig.openApiSchema` column
- Supports both JSON and YAML formats
- Managed via Admin API

### 2. Validation Modes
Configurable per backend via `validationMode`:
- **STRICT**: Block operations that fail validation
- **WARN**: Allow operations but log warnings
- **OFF**: No validation (default)

### 3. Mock Generation
- Auto-generate mock endpoints from schema paths and operations
- Create responses based on schema response definitions
- Guide response generation with specific values
- Support for complex types (objects, arrays, nested structures)

### 4. Validation
- **Mock Response Validation**: Ensures responses match schema
- **Request Validation**: Validates incoming requests (fixture mode)
- **Endpoint Validation**: Checks if endpoints exist in schema
- **Type Checking**: Validates JSON structure and types

## Configuration

### Backend Configuration

```yaml
# Database entity fields
openApiSchema: "..." # OpenAPI 3.0 specification (JSON/YAML)
validationMode: OFF  # STRICT, WARN, or OFF
```

### Validation Mode Settings

| Mode | Description | Use Case |
|------|-------------|----------|
| `STRICT` | Blocks invalid operations | Production-like testing |
| `WARN` | Logs warnings but allows | Development |
| `OFF` | No validation | Quick prototyping |

## Schema Upload

### Upload OpenAPI Schema

```bash
POST /admin/api/backends/{id}/schema
Content-Type: application/json

{
  "openApiSchema": "<OpenAPI 3.0 specification>",
  "migrationOption": "REVALIDATE_AND_DISABLE"
}
```

### Migration Options

- **DELETE_MOCKS**: Delete all existing mocks for the backend
- **REVALIDATE_AND_DISABLE**: Revalidate mocks and disable incompatible ones

### Get Schema

```bash
GET /admin/api/backends/{id}/schema
```

### Delete Schema

```bash
DELETE /admin/api/backends/{id}/schema
```

### Generate Schema from Manual Mocks

If you've created mocks manually and want to generate an OpenAPI schema from them:

```bash
POST /admin/api/backends/{id}/generate-schema
```

This will:
1. Analyze all mock endpoints for the backend
2. Analyze all mock responses (structure, types, status codes)
3. Generate a valid OpenAPI 3.0 specification
4. Save it to the backend automatically
5. Enable Swagger UI for your manually created API

**Response:**
```json
{
  "message": "Schema generated successfully from 5 mock endpoints",
  "schema": "{ ... generated OpenAPI 3.0 spec ... }"
}
```

**What gets generated:**
- ✅ All paths and HTTP methods from mock endpoints
- ✅ Response schemas inferred from mock response bodies
- ✅ Status codes from all mock responses
- ✅ Path parameters detected from URLs (e.g., `/users/{id}`)
- ✅ Response headers from mock responses
- ✅ Type inference (objects, arrays, strings, numbers, booleans)
- ✅ Format detection (date-time, email, uri)
## Mock Generation

### Auto-Generate Mocks from Schema

```bash
POST /admin/api/backends/{id}/generate-mocks
Content-Type: application/json

{
  "generateEndpoints": true,
  "generateResponses": true,
  "guidedValues": {
    "id": 123,
    "name": "John Doe",
    "email": "john@example.com",
    "items[].__size": 3,
    "items[0].id": 1
  }
}
```

### Guided Values Format

Specify values for specific properties in the response:

```json
{
  "id": 123,                    // Top-level property
  "user.name": "John Doe",      // Nested property
  "user.email": "john@example.com",
  "items[].__size": 5,          // Array size
  "items[0].id": 1,             // Specific array item property
  "items[0].name": "Item 1"
}
```

### Generate Single Response

```bash
POST /admin/api/backends/{backendName}/generate-response?path=/users&method=GET&statusCode=200
Content-Type: application/json

{
  "id": 1,
  "name": "Generated User",
  "email": "generated@example.com"
}
```

## Validation

### Validate All Mocks

```bash
GET /admin/api/backends/{id}/validate-mocks
```

**Response:**
```json
[
  {
    "id": 1,
    "name": "GET /users",
    "type": "endpoint",
    "valid": true,
    "message": null
  },
  {
    "id": 2,
    "name": "Success Response",
    "type": "response",
    "valid": false,
    "message": "Schema validation failed: Type mismatch at id: expected integer but got STRING"
  }
]
```

### Set Validation Mode

```bash
PUT /admin/api/backends/{id}
Content-Type: application/json

{
  "validationMode": "STRICT"
}
```

## Per-Backend Swagger UI

Each backend with an uploaded OpenAPI schema can be viewed using the standard SpringDoc Swagger UI.

### Access Swagger UI

These endpoints are intended to be used by both humans and agent clients. The global `/api-docs` endpoint documents the admin and discovery APIs; per-backend API contracts are exposed through the endpoints below.

**Agent-Friendly Catalog:**
```bash
GET http://localhost:8080/api/backends/catalog
```

Returns a structured catalog with server metadata and absolute URLs for each backend OpenAPI document:
```json
{
  "serverUrl": "http://localhost:8080",
  "backendCount": 1,
  "generatedAt": "2026-03-19T07:57:27",
  "backends": [
    {
      "name": "user-service",
      "title": "User Service API",
      "version": "1.0.0",
      "description": "User management endpoints",
      "backendBaseUrl": "http://localhost:9001",
      "backendPath": "/api/v1",
      "gatewayPathPrefix": "/api/v1/user-service",
      "securityType": "JWT",
      "enabled": true,
      "openapiUrl": "http://localhost:8080/api/backends/user-service/openapi.json",
      "swaggerUrl": "http://localhost:8080/swagger-ui.html?url=/api/backends/user-service/openapi.json",
      "openapiPath": "/api/backends/user-service/openapi.json",
      "swaggerPath": "/swagger-ui.html?url=/api/backends/user-service/openapi.json"
    }
  ]
}
```

**List All Backends with Schemas:**
```bash
GET http://localhost:8080/api/backends/with-schemas
```

Returns a JSON array with the same per-backend descriptors:
```json
[
  {
    "name": "user-service",
    "title": "User Service API",
    "version": "1.0.0",
    "description": "User management endpoints",
    "backendBaseUrl": "http://localhost:9001",
    "backendPath": "/api/v1",
    "gatewayPathPrefix": "/api/v1/user-service",
    "securityType": "JWT",
    "enabled": true,
    "openapiUrl": "http://localhost:8080/api/backends/user-service/openapi.json",
    "swaggerUrl": "http://localhost:8080/swagger-ui.html?url=/api/backends/user-service/openapi.json",
    "openapiPath": "/api/backends/user-service/openapi.json",
    "swaggerPath": "/swagger-ui.html?url=/api/backends/user-service/openapi.json"
  }
]
```

**Get Backend OpenAPI Specification:**
```bash
GET http://localhost:8080/api/backends/{backendName}/openapi.json
```

Returns the normalized OpenAPI 3.0 JSON specification for the backend.
If the schema was uploaded in YAML, this endpoint still returns JSON so clients can consume a consistent format.

**View in Swagger UI:**
```
http://localhost:8080/swagger-ui.html?url=/api/backends/{backendName}/openapi.json
```

This loads the backend's OpenAPI spec into the standard Swagger UI.

### Example Usage

```bash
# 1. Upload OpenAPI schema
curl -X POST http://localhost:8080/admin/api/backends/1/schema \
  -H "Content-Type: application/json" \
  -d '{
    "openApiSchema": "{ ... }",
    "migrationOption": "REVALIDATE_AND_DISABLE"
  }'

# 2. List backends with schemas
curl http://localhost:8080/api/backends/with-schemas

# Or get the catalog intended for agent discovery
curl http://localhost:8080/api/backends/catalog

# 3. View specific backend in Swagger UI
# Open in browser: 
# http://localhost:8080/swagger-ui.html?url=/api/backends/user-service/openapi.json

# 4. Use "Try it out" to test endpoints directly from Swagger UI
```

### Features

The standard SpringDoc Swagger UI provides:
- **Professional UI**: Industry-standard Swagger UI interface
- **Interactive Testing**: Test endpoints directly from the browser
- **Authentication Support**: Configure and test with different auth methods
- **Model Browser**: Explore all data models and schemas
- **Download Spec**: Export OpenAPI specification
- **Search**: Find endpoints quickly
- **Deep Linking**: Share links to specific operations
- **Auto-updates**: Benefits from SpringDoc community updates

## API Examples

### Example 1: Schema-First Workflow

Design API with OpenAPI, then generate mocks:

```bash
# 1. Upload OpenAPI schema
curl -X POST http://localhost:8080/admin/api/backends/1/schema \
  -H "Content-Type: application/json" \
  -d '{
    "openApiSchema": "{ ... OpenAPI 3.0 spec ... }",
    "migrationOption": "REVALIDATE_AND_DISABLE"
  }'

# 2. Enable strict validation
curl -X PUT http://localhost:8080/admin/api/backends/1 \
  -H "Content-Type: application/json" \
  -d '{
    "validationMode": "STRICT"
  }'

# 3. Generate mocks with guided values
curl -X POST http://localhost:8080/admin/api/backends/1/generate-mocks \
  -H "Content-Type: application/json" \
  -d '{
    "generateEndpoints": true,
    "generateResponses": true,
    "guidedValues": {
      "id": 1,
      "name": "John Doe",
      "users[].__size": 10
    }
  }'

# 4. Validate all generated mocks
curl http://localhost:8080/admin/api/backends/1/validate-mocks
```

### Example 2: Manual-First Workflow

Create mocks manually, then generate OpenAPI schema:

```bash
# 1. Create backend
curl -X POST http://localhost:8080/admin/api/backends \
  -H "Content-Type: application/json" \
  -d '{
    "name": "user-service",
    "baseUrl": "http://localhost:9001",
    "path": "/api/v1",
    "securityType": "JWT",
    "enabled": true
  }'

# 2. Create mock endpoints manually
curl -X POST http://localhost:8080/admin/api/mock-endpoints \
  -H "Content-Type: application/json" \
  -d '{
    "backendName": "user-service",
    "method": "GET",
    "path": "/users",
    "description": "Get all users",
    "enabled": true
  }'

# 3. Create mock responses manually
curl -X POST http://localhost:8080/admin/api/mock-endpoints/1/responses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Success",
    "httpStatus": 200,
    "responseBody": "{\"users\":[{\"id\":1,\"name\":\"John Doe\",\"email\":\"john@example.com\"}]}",
    "priority": 10,
    "enabled": true
  }'

# 4. Generate OpenAPI schema from your manual mocks
curl -X POST http://localhost:8080/admin/api/backends/1/generate-schema

# 5. Now your manually created API has Swagger UI!
# Open: http://localhost:8080/swagger-ui.html?url=/api/backends/user-service/openapi.json
```

**Benefits of Manual-First:**
- ✅ Start quickly without writing OpenAPI schema
- ✅ Iterate rapidly on mock design
- ✅ Generate documentation automatically when ready
- ✅ Enable Swagger UI after the fact
- ✅ Share API spec with team once mocks are stable

### Example 3: OpenAPI Schema

```json
{
  "openapi": "3.0.0",
  "info": {
    "title": "User Service API",
    "version": "1.0.0"
  },
  "paths": {
    "/users": {
      "get": {
        "summary": "Get all users",
        "responses": {
          "200": {
            "description": "Successful response",
            "content": {
              "application/json": {
                "schema": {
                  "type": "array",
                  "items": {
                    "$ref": "#/components/schemas/User"
                  }
                }
              }
            }
          }
        }
      },
      "post": {
        "summary": "Create user",
        "requestBody": {
          "content": {
            "application/json": {
              "schema": {
                "$ref": "#/components/schemas/UserInput"
              }
            }
          }
        },
        "responses": {
          "201": {
            "description": "User created",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/User"
                }
              }
            }
          }
        }
      }
    },
    "/users/{id}": {
      "get": {
        "summary": "Get user by ID",
        "parameters": [
          {
            "name": "id",
            "in": "path",
            "required": true,
            "schema": {
              "type": "integer"
            }
          }
        ],
        "responses": {
          "200": {
            "description": "User found",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/User"
                }
              }
            }
          },
          "404": {
            "description": "User not found",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/Error"
                }
              }
            }
          }
        }
      }
    }
  },
  "components": {
    "schemas": {
      "User": {
        "type": "object",
        "required": ["id", "name", "email"],
        "properties": {
          "id": {
            "type": "integer",
            "example": 1
          },
          "name": {
            "type": "string",
            "example": "John Doe"
          },
          "email": {
            "type": "string",
            "format": "email",
            "example": "john@example.com"
          },
          "createdAt": {
            "type": "string",
            "format": "date-time"
          }
        }
      },
      "UserInput": {
        "type": "object",
        "required": ["name", "email"],
        "properties": {
          "name": {
            "type": "string"
          },
          "email": {
            "type": "string",
            "format": "email"
          }
        }
      },
      "Error": {
        "type": "object",
        "properties": {
          "error": {
            "type": "string"
          },
          "message": {
            "type": "string"
          }
        }
      }
    }
  }
}
```

### Example 3: Generated Mock Response

Given the schema above and guided values:

```json
{
  "id": 42,
  "name": "Alice Smith",
  "email": "alice@example.com"
}
```

Generated response for `GET /users/42`:

```json
{
  "id": 42,
  "name": "Alice Smith",
  "email": "alice@example.com",
  "createdAt": "2026-03-18T22:00:00Z"
}
```

## Best Practices

### 1. Schema Management
- **Keep schemas updated**: Re-upload when API changes
- **Use versioning**: Include version in API title
- **Document examples**: Add example values in schema
- **Use $ref**: Keep schemas DRY with component references

### 2. Validation Modes
- **Development**: Use `WARN` to catch issues early
- **Testing**: Use `STRICT` for contract testing
- **Prototyping**: Use `OFF` for quick experiments

### 3. Mock Generation
- **Start simple**: Generate without guided values first
- **Refine incrementally**: Add guided values for specific scenarios
- **Use realistic data**: Provide meaningful test data
- **Verify output**: Always validate generated mocks

### 4. Migration Strategies
- **New schema**: Use `DELETE_MOCKS` to start fresh
- **Schema update**: Use `REVALIDATE_AND_DISABLE` to preserve valid mocks
- **Major changes**: Consider manual migration

### 5. Request Validation
- **Enable selectively**: Only for critical endpoints
- **Test thoroughly**: Ensure valid requests pass
- **Monitor logs**: Watch for validation warnings
- **Update schemas**: Keep in sync with real API

## Validation Details

### What Gets Validated

#### Mock Responses
- Response structure matches schema
- Required fields are present
- Field types are correct
- Status codes are defined in schema

#### Incoming Requests
- Request structure matches schema
- Required query parameters present
- Required headers present
- Request body structure (if defined)

### Validation Limitations

Basic type and structure validation is performed. Full JSON Schema validation would require additional libraries. Current validation checks:

- **Type matching**: object, array, string, number, boolean
- **Required fields**: For objects
- **Basic structure**: Nesting and field presence

### Handling Validation Errors

**STRICT mode:**
```bash
# Request validation failure
400 Bad Request
{
  "error": "Request validation failed",
  "details": "Required query parameter missing: status"
}

# Mock creation failure
400 Bad Request
{
  "error": "Schema validation failed: Type mismatch at id: expected integer but got STRING"
}
```

**WARN mode:**
```bash
# Logs warning but allows operation
WARN - Schema validation warning for mock response 'Success': Type mismatch at ...
```

## Troubleshooting

### Schema Not Parsing
- Verify valid OpenAPI 3.0 format
- Check JSON/YAML syntax
- Ensure all $ref references are resolvable
- Check logs for parsing errors

### Mocks Not Generating
- Verify schema has paths defined
- Check schema has response definitions
- Ensure content-type is application/json
- Review logs for generation errors

### Validation Always Failing
- Verify validation mode is set correctly
- Check schema matches actual response structure
- Review validation error messages
- Test with simpler response first

### Guided Values Not Applied
- Check property path syntax
- Verify property exists in schema
- Ensure guided values match expected types
- Use proper array notation

## Advanced Features

### Custom Response Generation

You can create custom responses by:
1. Generating base response from schema
2. Retrieving generated response
3. Modifying as needed
4. Saving customized version

### Schema Versioning (Manual)

While automatic versioning isn't supported, you can:
1. Export current schema before updates
2. Upload new schema with migration option
3. Compare validation results
4. Roll back if needed by re-uploading old schema

### Integration Testing

Use schemas to ensure fixture mode matches production:
1. Upload production OpenAPI spec
2. Enable STRICT validation
3. Generate mocks with production-like data
4. Run integration tests against fixture mode
5. Validate all requests/responses match contract

## Summary

OpenAPI schema support in Backend Gateway enables:
- ✅ Automatic mock generation from API specifications
- ✅ Guided mock creation with custom values
- ✅ Request and response validation
- ✅ Contract testing in lower environments
- ✅ API documentation and discovery
- ✅ Flexible validation modes for different use cases

This ensures your mocks stay in sync with real APIs and provides confidence in your testing environment.
