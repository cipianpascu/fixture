package com.db.olorin.rest.client;

/**
 * Executes JSON operations using generated request and response model classes.
 */
public interface TypedRestClient {

    <I, O> O exchange(String clientName, RestRequest<I, O> request);
}
