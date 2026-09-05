# Tasks: Opt-in Request Context Forwarding

- [X] T001 Add configuration adapter and backend contract tests in spring-library/src/test/java/com/db/olorin/rest/config/RestConfigurationAdapterTest.java
- [X] T002 Add MVC request-context forwarding, precedence, and no-context tests in spring-library/src/test/java/com/db/olorin/rest/client/SpringTypedRestClientIntegrationTest.java
- [X] T003 Add forward-headers and forward-cookies to spring-library/src/main/java/com/db/olorin/rest/config/ProxyProperties.java and spring-library/src/main/java/com/db/olorin/rest/config/RestConfigurationAdapter.java
- [X] T004 Implement allow-listed request context propagation in spring-library/src/main/java/com/db/olorin/rest/client/SpringTypedRestClient.java
- [X] T005 Document the ASM token forwarding scenario in spring-library/README.md
- [X] T006 Run module tests and mark validation evidence in specs/002-request-context-forwarding/tasks.md

Validation: 2026-09-05 mvn -pl spring-library test passed with 38 tests, 0 failures, 0 errors, and 0 skipped tests.
