# Tasks: Spring REST Client Contract Parity

**Input**: Design documents from specs/001-spring-client-parity/

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/generated-client.md

**Tests**: Required by FR-010 and the BFA constitution. Tests are written before the corresponding implementation.

## Phase 1: Setup

- [X] T001 Confirm Java ignore patterns and Maven module wiring in .gitignore and pom.xml
- [X] T002 Record the legacy library and proxy test families used as the parity inventory in docs/SPRING_REST_CLIENT_READINESS.md

## Phase 2: Foundational Configuration and Failure Taxonomy

- [X] T003 [P] Add adapter contract tests for direct and indexed gateway configuration values in spring-library/src/test/java/com/db/olorin/rest/config/RestConfigurationAdapterTest.java
- [X] T004 [P] Add failure-category tests in spring-library/src/test/java/com/db/olorin/rest/client/SpringTypedRestClientIntegrationTest.java
- [X] T005 Implement exact gateway configuration list, nullable, and validation handling in spring-library/src/main/java/com/db/olorin/rest/config/RestConfigurationAdapter.java
- [X] T006 Preserve the public exception taxonomy from transport through the client API in spring-library/src/main/java/com/db/olorin/rest/client/SpringTypedRestClient.java

## Phase 3: User Story 1 - Call a configured backend reliably (P1)

**Goal**: Execute typed backend calls with correct path composition, request locations, transport behavior, and retry/circuit classification.

**Independent Test**: A local server verifies composed URL, multi-valued headers/query/cookies, transient-only retry, error categories, and no-content handling.

- [X] T007 [P] [US1] Add typed request location and configured-path tests in spring-library/src/test/java/com/db/olorin/rest/client/SpringTypedRestClientIntegrationTest.java
- [X] T008 [P] [US1] Add proxy selector, HTTP version, gzip, and header-sanitization tests in spring-library/src/test/java/com/db/olorin/rest/client/TransportContractTest.java
- [X] T009 [US1] Expand the public typed request contract for multi-valued headers, query parameters, and cookies in spring-library/src/main/java/com/db/olorin/rest/client/RestRequest.java
- [X] T010 [US1] Implement normalized path/URI composition and unsafe header filtering in spring-library/src/main/java/com/db/olorin/rest/client/SpringTypedRestClient.java
- [X] T011 [US1] Implement transient-only retry and documented circuit behavior in spring-library/src/main/java/com/db/olorin/rest/client/SpringTypedRestClient.java
- [X] T012 [US1] Implement proxy bypass and transport helpers in spring-library/src/main/java/com/db/olorin/rest/client/SpringTypedRestClient.java

## Phase 4: User Story 2 - Use existing security profiles (P1)

**Goal**: Reuse basic, JWT/authz, form, and Cloud Run profiles with expiry-safe cache semantics.

**Independent Test**: Local auth/backend fixtures prove source extraction, mapped credentials, denials, expiry, and form modes.

- [X] T013 [P] [US2] Add JWT expiry/cache and error-mapping tests in spring-library/src/test/java/com/db/olorin/rest/service/auth/JwtAuthServiceTest.java
- [X] T014 [P] [US2] Add basic, form-service, inline-form, and Cloud Run integration tests in spring-library/src/test/java/com/db/olorin/rest/service/auth/AuthServiceContractTest.java
- [X] T015 [US2] Derive cache expiry from JWT exp and configured skew in spring-library/src/main/java/com/db/olorin/rest/service/auth/JwtAuthService.java
- [X] T016 [US2] Complete mapped source, token response, and categorized failure handling in spring-library/src/main/java/com/db/olorin/rest/service/auth/AuthServiceCaller.java
- [X] T017 [US2] Complete form service and inline request mapping in spring-library/src/main/java/com/db/olorin/rest/service/auth/FormAuthService.java
- [X] T018 [US2] Verify Cloud Run audience/token injection in spring-library/src/main/java/com/db/olorin/rest/service/auth/CloudRunAuthService.java

## Phase 5: User Story 3 - Preserve historization and correlation (P1)

**Goal**: Emit complete, correlated lifecycle events while retaining configured delivery semantics.

**Independent Test**: Test publishers/mappers observe submitted, fulfilled, and failed events sharing one generated ID in sync and async modes.

- [X] T019 [P] [US3] Add lifecycle, generated-header, and failed-event tests in spring-library/src/test/java/com/db/olorin/rest/history/HistoryUuidCorrelationTest.java
- [X] T020 [P] [US3] Port provider, fail-open/closed, queue, and publisher mapping coverage in spring-library/src/test/java/com/db/olorin/rest/history/HistoryServiceContractTest.java
- [X] T021 [US3] Preserve one request context and emit failed history events with response/failure detail in spring-library/src/main/java/com/db/olorin/rest/client/SpringTypedRestClient.java
- [X] T022 [US3] Complete history delivery and publisher configuration behavior in spring-library/src/main/java/com/db/olorin/rest/history/HistoryService.java

## Phase 6: User Story 4 - Inject generated client interfaces (P2)

**Goal**: Inject generated-style interfaces with explicit parameter-to-HTTP mapping.

**Independent Test**: A Spring context invokes an injected interface against a local backend and validates every parameter location and no-content output.

- [X] T023 [P] [US4] Add generated-interface context contract tests in spring-library/src/test/java/com/db/olorin/rest/client/OlorinRestClientInjectorTest.java
- [X] T024 [US4] Add public path, query, header, cookie, and body parameter annotations in spring-library/src/main/java/com/db/olorin/rest/client/
- [X] T025 [US4] Implement annotation-driven method argument mapping and void/generic validation in spring-library/src/main/java/com/db/olorin/rest/client/OlorinRestClientInjector.java
- [X] T026 [US4] Update the generated-client contract example in specs/001-spring-client-parity/contracts/generated-client.md

## Phase 7: Polish and Contract Validation

- [X] T027 [P] Add TLS truststore, mTLS, PEM, and protocol tests in spring-library/src/test/java/com/db/olorin/rest/service/TlsContextFactoryTest.java
- [X] T028 [P] Add GCP publisher mapping tests in spring-library/src/test/java/com/db/olorin/rest/history/GcpPubSubHistoryPublisherTest.java
- [X] T029 Update the consumer README and remove only resolved readiness gaps in spring-library/README.md and docs/SPRING_REST_CLIENT_READINESS.md
- [X] T030 Run module and full-reactor Maven test suites and capture the result in docs/SPRING_REST_CLIENT_READINESS.md
- [X] T031 Mark completed work and final validation evidence in specs/001-spring-client-parity/tasks.md

## Final validation evidence

- 2026-09-05: mvn -pl spring-library test passed with 36 tests, 0 failures, 0 errors, and 0 skipped tests.
- 2026-09-05: mvn test completed across the Maven reactor; no Surefire report records a failure or error.
- 2026-09-05: git diff --check passed.

## Dependencies and Execution Order

T001-T006 establish the configuration and error foundation. US1 must complete before generated-interface work because it consumes the typed request API. US2 and US3 depend on the shared request context but are independently testable after T009. US4 depends on US1. Polish follows all stories.

## Parallel Opportunities

- T003 and T004 can proceed independently.
- Within US1, T007 and T008 are separate test fixtures.
- Within US2 and US3, the listed test tasks are independent.
- TLS and publisher contract tests are independent from documentation work.

## Implementation Strategy

First make the typed transport contract correct and testable, then layer source-compatible security and history, then expose generated-interface injection. Complete with the migrated source-contract tests, documentation, and the full reactor validation.
