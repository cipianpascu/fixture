# Validation Quickstart

1. Run mvn -pl spring-library test to execute the library contract suite.
2. Run mvn test from the repository root to verify the complete reactor.
3. Review the generated-client contract in contracts/generated-client.md and the configuration example in spring-library/README.md.
4. The local integration tests start a JDK HTTP server, configure an in-memory ConfigurationIF, inject a generated interface, and verify path, query, headers, body, auth, history, retry, and categorized failure behavior without external services.

Expected result: all Maven tests pass, no parity tests are skipped, and a consumer can copy the README configuration and generated-interface example into a Spring Boot application.
