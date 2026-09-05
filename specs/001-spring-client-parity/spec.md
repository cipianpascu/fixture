# Feature Specification: Spring REST Client Contract Parity

**Feature Branch**: 001-spring-client-parity

**Created**: 2026-09-05

**Status**: Approved for implementation

**Input**: Finalize the Spring REST client migration using the existing library
and proxy documentation and behavior as the contract.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Call a configured backend reliably (Priority: P1)

A consuming Spring application configures a named backend with its existing
gateway.* configuration items and calls it with generated request and response
models.

**Why this priority**: This is the core purpose of the library.

**Independent Test**: A local backend verifies base URL/path composition,
method, generated-model JSON, headers, query parameters, response binding,
transient retries, circuit behavior, and classified failures.

**Acceptance Scenarios**:

1. **Given** a backend base URL and configured path, **When** a caller uses a
   relative operation path, **Then** the target URL contains both configured
   path and operation path exactly once.
2. **Given** a transient upstream failure, **When** the call is retried,
   **Then** transient failures retry while caller, authorization, and
   configuration failures do not.
3. **Given** an HTTPS backend with configured TLS and proxy settings, **When**
   the call is made, **Then** the configured transport behavior is used.

---

### User Story 2 - Use existing security profiles (Priority: P1)

A consuming application reuses documented basic, JWT/authz, form, and Cloud
Run configuration profiles without changing the configuration model.

**Why this priority**: Backend credentials and authorization behavior are
security-critical.

**Independent Test**: Local auth and backend servers prove each auth mode,
header/cookie sources, token mapping, denials, cache expiry, and auth-service
TLS/Cloud Run behavior.

**Acceptance Scenarios**:

1. **Given** JWT auth or authz settings, **When** an incoming session is
   supplied through its configured header or cookie, **Then** token calls and
   configured backend headers match the source contract.
2. **Given** an invalid caller or denied grant, **When** an auth call fails,
   **Then** the caller receives the corresponding authentication or
   authorization failure without an unsafe retry.
3. **Given** form service and inline modes, **When** mappings are configured,
   **Then** outbound form data and mapped headers match the documented shape.

---

### User Story 3 - Preserve historization and correlation (Priority: P1)

An application can publish history around a backend call using the same
configured provider, mapper, delivery mode, and generated correlation UUID.

**Why this priority**: Historization must reliably pair request lifecycle
events and preserve configured failure semantics.

**Independent Test**: Test publishers and mappers prove submitted, fulfilled,
and failed lifecycle events, attributes, generated headers, queue behavior,
and confirmed/async fail-open and fail-closed modes.

**Acceptance Scenarios**:

1. **Given** a generated request ID configuration, **When** a request has no
   ID header, **Then** one UUID is inserted before the backend call and reused
   by every history event for that call.
2. **Given** a backend failure, **When** history is enabled, **Then** the
   mapper receives a failed lifecycle event with configured failure behavior.
3. **Given** history delivery is fail-closed, **When** publishing cannot be
   scheduled or confirmed, **Then** the call fails before the backend request.

---

### User Story 4 - Inject generated client interfaces (Priority: P2)

A consuming Spring application injects a named generated-style REST interface
and invokes methods with operation parameters and typed responses.

**Why this priority**: This provides the requested SOAP-like consuming
experience while preserving explicit backend selection.

**Independent Test**: A Spring application context injects a generated-style
interface and proves path, query, header, body, empty response, and error
behavior against a local backend.

**Acceptance Scenarios**:

1. **Given** an annotated generated client interface, **When** the application
   injects it by backend name, **Then** no manual proxy creation is required.
2. **Given** a method with typed request, path, query, and header parameters,
   **When** it is called, **Then** each value is mapped to its HTTP location.

### Edge Cases

- Configured backend and operation paths may each contain leading/trailing
  slashes.
- A JWT response may omit expiry, be expired, or contain selected token fields.
- An existing generated correlation header must not be replaced.
- A failed call may occur after submitted history but before a response body.
- Proxy bypass patterns may match exact hosts, wildcard suffixes, or no host.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The Spring library MUST accept the documented gateway.*
  configuration vocabulary in one ConfigurationIF section without changing key
  semantics.
- **FR-002**: The client MUST compose configured backend and operation paths,
  preserve configured timeout/TLS/proxy/HTTP version behavior, and sanitize
  unsafe forwarded headers.
- **FR-003**: The client MUST expose strongly typed request and response
  operations, including no-content responses and typed generated interfaces.
- **FR-004**: The client MUST support documented basic, JWT/authz, form, and
  Cloud Run flows with equivalent token mapping and categorized failures.
- **FR-005**: JWT/authz caching MUST use returned token expiry and configured
  expiry skew; entries without a valid future expiry MUST not be cached.
- **FR-006**: The client MUST emit submitted, fulfilled, and failed history
  events and preserve configured delivery and fail-open behavior.
- **FR-007**: The UUID generator MUST generate once per logical request and
  reuse the value in outbound headers and history attributes.
- **FR-008**: Retry and circuit behavior MUST retry only transient dependency
  failures and MUST not retry caller, authorization, or configuration errors.
- **FR-009**: The README MUST document installation, configuration, typed and
  generated-interface usage, security, transport, history, limits, and test
  examples.
- **FR-010**: Each in-scope source-contract behavior MUST have a Spring unit
  or local integration test.

### Key Entities

- **Backend definition**: Named configured upstream with endpoint, transport,
  security, history, and timeout settings.
- **Auth service profile**: Named reusable credential/token endpoint with its
  own transport and cache settings.
- **Typed operation**: Generated-model request, operation metadata, and typed
  result associated with one backend.
- **History event**: Lifecycle record with request context, attributes, status,
  and optional backend result.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every source test family for configuration, transport, auth,
  history, and TLS has an equivalent Spring test suite with passing cases.
- **SC-002**: A consumer-style Spring context injects and calls a generated
  client interface against a local backend without manual library wiring.
- **SC-003**: Contract test coverage includes all supported auth modes,
  transport settings, history delivery modes, and failure categories.
- **SC-004**: Maven module tests and the full reactor test suite pass with no
  skipped parity tests.

## Assumptions

- Existing library and proxy documentation and verified tests define expected
  behavior where implementation details differ.
- Runtime schema loading, validation, and SpringDoc remain intentionally out
  of scope for this client-only module.
- Applications provide generated model classes and the core configuration
  section at startup.
- Vendored configuration interfaces will later be replaced by common-api
  without changing consumer behavior.
