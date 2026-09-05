# Tasks: Typed SOAP Client

- [X] T001 Add SOAP public API and generated-model contract documentation in spring-library/src/main/java/com/db/olorin/rest/client/TypedSoapClient.java
- [X] T002 Add SOAP 1.1, SOAP 1.2, action, response, and fault integration tests in spring-library/src/test/java/com/db/olorin/rest/client/SpringTypedSoapClientIntegrationTest.java
- [X] T003 Implement JAXB envelope marshal/unmarshal and SOAP Fault parsing in spring-library/src/main/java/com/db/olorin/rest/client/SpringTypedSoapClient.java
- [X] T004 Integrate configured auth, forwarded request context, TLS/proxy, retry/circuit, and history in spring-library/src/main/java/com/db/olorin/rest/client/SpringTypedSoapClient.java
- [X] T005 Wire TypedSoapClient through spring-library/src/main/java/com/db/olorin/rest/config/RestClientAutoConfiguration.java
- [X] T006 Document SOAP configuration and typed invocation examples in spring-library/README.md
- [X] T007 Run module and full reactor validation and mark evidence in specs/003-typed-soap-client/tasks.md (2026-09-05: `mvn -pl spring-library test` passed 42 tests; `mvn test` completed the library, Spring library, fixture, and proxy suites with no reported failures; `git diff --check` passed.)
