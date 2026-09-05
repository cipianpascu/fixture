# Implementation Plan: Spring REST Client Contract Parity

**Branch**: `001-spring-client-parity` | **Date**: 2026-09-05 | **Spec**: [spec.md](spec.md)

**Input**: Finalize the reusable Spring REST-client module against the documented legacy library and proxy contract.

**Note**: This template is filled in by the `$speckit-plan` command; its definition describes the execution workflow.

## Summary

Complete the Spring Boot client module as a client-only replacement for the legacy request-forwarding runtime. The implementation keeps one application-provided ConfigurationIF section, resolves named backend definitions, applies auth and transport policies, emits correlated history, and exposes both explicit typed calls and generated-interface injection. Contract tests use local HTTP servers and Spring application contexts; no remote backend, schema loader, or SpringDoc dependency is introduced.

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Java 21, Spring Boot 3.3.x

**Primary Dependencies**: Spring Web RestClient, Jackson, Resilience4j, Google Auth Library, JAXB

**Storage**: N/A; in-memory bounded auth token cache and circuit state only

**Testing**: JUnit 5, Spring Boot Test, Mockito, JDK HttpServer local integration fixtures

**Target Platform**: Spring Boot 3 applications on a Java 21 JVM

**Project Type**: Maven library module

**Performance Goals**: avoid duplicate auth calls for valid cached credentials and avoid blocking retries for non-transient failures

**Constraints**: preserve documented gateway.* key semantics; no schema validation or SpringDoc; no credential values in logs; a UUID is generated once per logical request

**Scale/Scope**: one new spring-library module, configuration/transport/auth/history/interface injection, and migrated source-contract tests

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Status | Evidence |
|---|---|---|
| Contract-first compatibility | PASS | Legacy README, proxy README, and legacy tests are the behavioral source. |
| Test-proven parity | PASS | Each in-scope area has a local unit or integration test task before implementation. |
| Production-safe security | PASS | Auth failures retain categories, tokens are expiry-aware, and logs avoid credentials. |
| Explicit client API | PASS | Typed request and parameter annotations define all outbound locations. |
| Honest documentation | PASS | README and readiness record are updated only after passing validation. |

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file ($speckit-plan command output)
├── research.md          # Phase 0 output ($speckit-plan command)
├── data-model.md        # Phase 1 output ($speckit-plan command)
├── quickstart.md        # Phase 1 output ($speckit-plan command)
├── contracts/           # Phase 1 output ($speckit-plan command)
└── tasks.md             # Phase 2 output ($speckit-tasks command - NOT created by $speckit-plan)
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
spring-library/
├── src/main/java/com/db/olorin/rest/
│   ├── client/          # public typed and generated-interface clients
│   ├── config/          # ConfigurationIF adapter and Spring auto-configuration
│   ├── service/auth/    # security profiles and expiry-aware caching
│   ├── service/         # TLS and transport helpers
│   ├── history/         # lifecycle mapping and publishers
│   └── exception/       # stable failure taxonomy
└── src/test/java/com/db/olorin/rest/
    ├── client/          # local HTTP and context contract tests
    ├── config/
    ├── history/
    └── service/
```

**Structure Decision**: Extend the existing spring-library module. Public APIs remain under com.db.olorin.rest; internal configuration conversion remains isolated in config.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | N/A | N/A |
