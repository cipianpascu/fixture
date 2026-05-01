# BFA Library

Shared Quarkus runtime library for schema-driven backend forwarding.

## Scope

This module contains the reusable runtime pieces that are shared by proxy-style applications:

- `BaseResource`
- shared config mapping in `ProxyProperties`
- request context and validation models
- schema loading and validation
- forwarding, auth, and outbound TLS services
- OpenAPI decoration from loaded contract schemas

This module does not define concrete HTTP resources. Concrete resources, their config, and their published contract schemas remain application-owned.

## Schema Contract

The library assumes a single schema location configured by:

```yaml
gateway:
  schemas:
    directory: classpath:schemas/
```

Behavior:

- all schema files from that directory are loaded once at startup
- loaded schemas are kept in memory for runtime validation and OpenAPI decoration
- schemas are never reloaded during normal runtime

This means downstream applications should place all contract and backend schemas under one shared directory, typically `src/main/resources/schemas/`.

## Runtime Responsibilities

- validate incoming requests against loaded OpenAPI contracts
- optionally trim JSON responses to schema-defined fields
- forward requests to configured backends
- enrich outbound requests with auth strategies
- apply outbound truststore and mTLS configuration
- decorate generated Swagger/OpenAPI output from in-memory contract schemas
