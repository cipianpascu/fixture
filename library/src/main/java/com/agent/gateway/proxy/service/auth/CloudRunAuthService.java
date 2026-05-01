package com.agent.gateway.proxy.service.auth;

import com.agent.gateway.proxy.model.ProxyRequestContext;

import java.util.Map;

public class CloudRunAuthService implements AuthService {

    private static final String CLOUD_RUN_AUTH_HEADER = "X-Serverless-Authorization";

    private final CloudRunIdTokenProvider idTokenProvider;
    private final String audience;

    public CloudRunAuthService(
        CloudRunIdTokenProvider idTokenProvider,
        String baseUrl,
        Map<String, String> securityConfig) {
        this.idTokenProvider = idTokenProvider;
        this.audience = CloudRunAudienceResolver.resolveAudience(baseUrl, securityConfig, "backend");
    }

    @Override
    public void enrichHeaders(ProxyRequestContext request, Map<String, String> headers) {
        String idToken = idTokenProvider.getIdToken(audience);
        headers.put(CLOUD_RUN_AUTH_HEADER, "Bearer " + idToken);
    }
}
