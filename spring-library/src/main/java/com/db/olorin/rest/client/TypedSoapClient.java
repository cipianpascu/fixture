package com.db.olorin.rest.client;

/**
 * Invokes configured SOAP backends with JAXB-generated request and response
 * model classes. The configured backend must use protocol=soap.
 */
public interface TypedSoapClient {
    <I, O> O exchange(String backendName, I requestBody, Class<O> responseType);

    <I, O> O exchange(String backendName, I requestBody, String soapActionOverride, Class<O> responseType);
}
