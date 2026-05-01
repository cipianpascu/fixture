package com.agent.gateway.proxy.service.auth;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;

import java.io.IOException;

public class CloudRunAuthRequestFilter implements ClientRequestFilter {

    private static final String CLOUD_RUN_AUTH_HEADER = "X-Serverless-Authorization";

    private final CloudRunIdTokenProvider idTokenProvider;
    private final String audience;

    public CloudRunAuthRequestFilter(CloudRunIdTokenProvider idTokenProvider, String audience) {
        this.idTokenProvider = idTokenProvider;
        this.audience = audience;
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        String idToken = idTokenProvider.getIdToken(audience);
        requestContext.getHeaders().putSingle(CLOUD_RUN_AUTH_HEADER, "Bearer " + idToken);
    }
}
