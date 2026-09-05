# Feature Specification: Opt-in Request Context Forwarding

**Created**: 2026-09-05

## User Scenario

A Spring MVC application receives an ASM token in x-asm-rctoken and calls a configured backend. The application configures that header once on the backend and the library forwards its current value without every call site manually copying it.

## Requirements

- FR-001: A backend may configure an allow-list of forwarded header names and cookie names using gateway.backends[n].forward-headers and gateway.backends[n].forward-cookies.
- FR-002: The library reads only the current servlet request when one is available.
- FR-003: An explicitly supplied RestRequest header or cookie wins over a forwarded value.
- FR-004: Missing request context, missing allowed values, and hop-by-hop headers are safe no-ops.
- FR-005: The README includes an ASM x-asm-rctoken scenario in ConfigurationIF item form.

## Success Criteria

- A local Spring request-context test proves forwarding of x-asm-rctoken and a selected cookie.
- A test proves explicit request values override context values and no context does not fail a call.
