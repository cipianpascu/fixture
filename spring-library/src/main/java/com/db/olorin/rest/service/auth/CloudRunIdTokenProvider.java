package com.db.olorin.rest.service.auth;

public interface CloudRunIdTokenProvider {

    String getIdToken(String audience);
}
