package com.db.olorin.rest.service.auth;

import com.db.olorin.rest.config.ProxyProperties;
import com.db.olorin.rest.model.ProxyRequestContext;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FormAuthServiceTest {
    @Test
    void inlineFormAuthenticationAddsConfiguredTypedRequestValues() {
        ProxyProperties.BackendDefinition backend = mock(ProxyProperties.BackendDefinition.class);
        when(backend.securityConfig()).thenReturn(Map.of(
            "service", "inline",
            "form-params.client_id", "literal:products",
            "form-params.session", "header:X-Session-Id"
        ));
        FormAuthService service = new FormAuthService(backend, null, null);
        Map<String, String> headers = new LinkedHashMap<>();
        ProxyRequestContext context = new ProxyRequestContext(
            "POST", "/token", null, Map.of("X-Session-Id", java.util.List.of("session-1")), Map.of());

        String transformed = service.transformRequestBody(context, headers, "grant_type=client_credentials");

        assertThat(headers).containsEntry("content-type", "application/x-www-form-urlencoded");
        assertThat(transformed).contains("grant_type=client_credentials", "client_id=products", "session=session-1");
    }
}
