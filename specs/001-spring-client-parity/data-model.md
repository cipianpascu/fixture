# Data Model

## BackendDefinition

- name: unique enabled backend identifier.
- baseUrl and path: normalized once before an operation path is joined.
- auth, tls, proxy, timeout, HTTP version, retry/circuit, and history settings: immutable configuration consumed by a call.

## RestRequest

- method, relative path, request body, response type.
- multi-valued headers, query parameters, and cookies.
- validation: path is relative to the selected backend; header and parameter names are nonblank.

## ProxyRequestContext

- method, logical path, remote address, inbound headers, and inbound cookies.
- lifespan: one instance for the entire auth, transport, and history lifecycle.

## Auth token cache entry

- key: auth profile and source session identity.
- value: outbound authorization values.
- expiresAt: derived JWT expiry minus skew; absent if not safely cacheable.

## History event

- status: SUBMITTED, FULFILLED, or FAILED.
- request context, transformed request, outbound headers, status code, response body, and failure detail.
- correlation: generated or existing configured request header shared by all lifecycle events.
