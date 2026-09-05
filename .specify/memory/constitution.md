<!--
Sync Impact Report
- Version change: template → 1.0.0
- Added principles: Contract-First Compatibility, Test-Proven Parity,
  Production-Safe Security, Explicit Client API, Honest Documentation
- Added sections: Compatibility Constraints, Delivery Workflow
- Removed sections: none
- Follow-up TODOs: none
-->
# BFA Constitution

## Core Principles

### I. Contract-First Compatibility

The existing library README, proxy README, configuration vocabulary, and
verified Quarkus behavior are the source contract for migrations. A Spring
implementation MUST preserve supported configuration semantics or explicitly
reject unsupported values. It MUST NOT silently reinterpret gateway.* keys.

### II. Test-Proven Parity

Every migrated behavior MUST have a Spring test that demonstrates the same
observable outcome as its source-library counterpart. Tests precede behavior
changes where practical and include unit coverage plus a local HTTP integration
test for each transport or auth flow.

### III. Production-Safe Security

Authentication, authorization, TLS, mTLS, proxy routing, token caching, and
history correlation MUST retain failure categories and fail-closed/open
semantics from the contract. Credentials and tokens MUST NOT be logged or
embedded in examples.

### IV. Explicit Client API

The public REST client API MUST be strongly typed, documented, and usable by a
consuming Spring application without component scanning library internals.
Generated interface support MUST state precisely which method and parameter
shapes it supports.

### V. Honest Documentation

The library README MUST be a consumer guide: installation, configuration,
security, generated models, history, and runnable examples. Readiness claims
MUST match implemented and tested behavior. Known gaps belong in a tracked
specification, not hidden behind broad parity claims.

## Compatibility Constraints

The Spring client excludes runtime schema loading, validation, OpenAPI
decoration, and SpringDoc by design. Generated OpenAPI or JSON Schema models
are application-owned. The core ConfigurationIF and ConfigurationItemIF
contracts are vendored temporarily and MUST be replaceable by common-api
without public API changes.

## Delivery Workflow

Each migration feature MUST have a Spec Kit specification, technical plan, and
task list. Completion requires all tasks marked complete, Maven module tests,
full reactor tests, documentation review, and a comparison against the source
contract test matrix.

## Governance

This constitution governs BFA migration work. Amendments require an updated
Sync Impact Report, semantic-version evaluation, and review of dependent
specifications and templates. Code review MUST verify the five core
principles; exceptions require a documented rationale and an explicit
follow-up task.

**Version**: 1.0.0 | **Ratified**: 2026-09-05 | **Last Amended**: 2026-09-05
