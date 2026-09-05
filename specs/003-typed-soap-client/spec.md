# Feature Specification: Typed SOAP Client

## User Scenario

A Spring application supplies JAXB-generated SOAP request and response classes and invokes a configured SOAP backend through the same configuration, authentication, transport, and history model as REST backends.

## Requirements

- FR-001: A public typed SOAP client accepts a backend name, generated JAXB request model, optional action override, and generated response model type.
- FR-002: SOAP 1.1 and 1.2 envelopes, content types, and action rules follow backend soap.version and soap.soap-action configuration.
- FR-003: The client reuses configured backend auth, request-context forwarding, TLS/mTLS, proxy, timeout, retry/circuit, history, and generated UUID behavior.
- FR-004: SOAP Fault responses raise SoapFaultException; malformed/transport failures raise categorized upstream/configuration failures.
- FR-005: README contains concrete ConfigurationIF and invocation examples.

## Success Criteria

- Local SOAP fixtures prove SOAP 1.1/1.2 request headers/envelopes, JAXB response decoding, action override, SOAP faults, and history lifecycle behavior.
- Maven module and reactor tests pass with no SOAP tests skipped.
