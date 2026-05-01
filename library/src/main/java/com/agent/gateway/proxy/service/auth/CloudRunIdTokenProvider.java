package com.agent.gateway.proxy.service.auth;

public interface CloudRunIdTokenProvider {

    String getIdToken(String audience);
}
