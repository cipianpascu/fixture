# Research: Spring REST Client Contract Parity

## Configuration representation

Decision: adapt a single ConfigurationIF named com.db.olorin.rest.configuration into ProxyProperties, supporting direct values and indexed list entries.

Rationale: this retains the configuration-section and key-reference mechanism agreed for the consuming application, without requiring Spring Environment ownership.

Alternatives considered: bind Spring properties directly. Rejected because the application provides the configuration contract as ConfigurationIF.

## Typed and generated interface contract

Decision: retain TypedRestClient for explicit calls and add parameter annotations for path, query, header, cookie, and body values on generated interfaces.

Rationale: generated model classes stay application-owned while HTTP mapping is unambiguous and does not rely on compiler parameter-name retention.

Alternatives considered: infer locations from parameter names. Rejected because it is ambiguous and fragile.

## Transport and resilience

Decision: construct an immutable JDK HttpClient per backend transport profile and apply a retry policy only to classified dependency failures; use a sliding failure ratio circuit policy equivalent to the documented legacy settings.

Rationale: this supports TLS, proxy selection and HTTP version while preserving caller and authorization errors.

Alternatives considered: retry every RestClientException. Rejected because it retries deterministic 4xx and configuration failures.

## JWT expiry

Decision: derive cache expiry from the JWT exp claim minus configured skew, and do not cache absent, malformed, or expired values.

Rationale: fixed cache lifetimes can send expired credentials.

Alternatives considered: fixed five-minute cache. Rejected as incompatible with the source contract.

## History lifecycle

Decision: create one request context before auth and history handling, enrich it once, emit submitted before transport, fulfilled after a response, and failed for any categorized call failure.

Rationale: it gives target applications a stable generated UUID and complete pairing lifecycle.

Alternatives considered: derive headers independently for every event. Rejected because it can generate non-pairable IDs.
