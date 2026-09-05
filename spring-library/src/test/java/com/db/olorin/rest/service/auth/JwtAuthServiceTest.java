package com.db.olorin.rest.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.db.olorin.rest.auth.AuthzTokens;
import com.db.olorin.rest.config.ProxyProperties;
import com.db.olorin.rest.exception.AuthResponseMappingException;
import com.db.olorin.rest.model.ProxyRequestContext;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JwtAuthServiceTest {

    @Test
    void cachesAuthzTokensUntilTheEarliestJwtExpiryMinusConfiguredSkew() {
        AuthServiceCallerFactory callers = mock(AuthServiceCallerFactory.class);
        AuthServiceCaller caller = mock(AuthServiceCaller.class);
        when(callers.getConfig("auth")).thenReturn(serviceConfig(Duration.ofSeconds(30)));
        when(callers.get("auth")).thenReturn(caller);
        String token = jwt(Instant.now().plusSeconds(180));
        when(caller.postJson(anyString(), any(), eq(AuthzTokens.class))).thenReturn(tokens(token));

        JwtAuthService service = new JwtAuthService(backend(), callers, new AuthzTokenCache());
        Map<String, String> headers = new java.util.LinkedHashMap<>();
        service.enrichHeaders(context(), headers, null);
        service.enrichHeaders(context(), new java.util.LinkedHashMap<>(), null);

        assertThat(headers).containsEntry("Authorization", "Bearer " + token);
        verify(caller, times(1)).postJson(anyString(), any(), eq(AuthzTokens.class));
    }

    @Test
    void doesNotCacheTokenWhoseExpiryFallsInsideConfiguredSkew() {
        AuthServiceCallerFactory callers = mock(AuthServiceCallerFactory.class);
        AuthServiceCaller caller = mock(AuthServiceCaller.class);
        when(callers.getConfig("auth")).thenReturn(serviceConfig(Duration.ofSeconds(30)));
        when(callers.get("auth")).thenReturn(caller);
        when(caller.postJson(anyString(), any(), eq(AuthzTokens.class))).thenReturn(tokens(jwt(Instant.now().plusSeconds(5))));

        JwtAuthService service = new JwtAuthService(backend(), callers, new AuthzTokenCache());
        service.enrichHeaders(context(), new java.util.LinkedHashMap<>(), null);
        service.enrichHeaders(context(), new java.util.LinkedHashMap<>(), null);

        verify(caller, times(2)).postJson(anyString(), any(), eq(AuthzTokens.class));
    }

    @Test
    void rejectsAnAuthzResponseWithNoUsableTokenOrAuthorizationDecision() {
        AuthServiceCallerFactory callers = mock(AuthServiceCallerFactory.class);
        AuthServiceCaller caller = mock(AuthServiceCaller.class);
        when(callers.getConfig("auth")).thenReturn(serviceConfig(Duration.ZERO));
        when(callers.get("auth")).thenReturn(caller);
        when(caller.postJson(anyString(), any(), eq(AuthzTokens.class))).thenReturn(new AuthzTokens());

        JwtAuthService service = new JwtAuthService(backend(), callers, new AuthzTokenCache());

        assertThatThrownBy(() -> service.enrichHeaders(context(), new java.util.LinkedHashMap<>(), null))
            .isInstanceOf(AuthResponseMappingException.class)
            .hasMessageContaining("usable token");
    }

    private ProxyProperties.BackendDefinition backend() {
        ProxyProperties.BackendDefinition backend = mock(ProxyProperties.BackendDefinition.class);
        ProxyProperties.AuthzRequestConfig authz = mock(ProxyProperties.AuthzRequestConfig.class);
        when(backend.name()).thenReturn("products");
        when(backend.securityConfig()).thenReturn(Map.of("bearer-source", "authorization_token"));
        when(backend.authRequest()).thenReturn(Optional.empty());
        when(backend.authzRequest()).thenReturn(Optional.of(authz));
        when(authz.service()).thenReturn(Optional.of("auth"));
        when(authz.path()).thenReturn("/authz/{sessionId}");
        when(authz.branchCustomerNumber()).thenReturn(Optional.of("header:X-Branch"));
        when(authz.serviceShopTransactions()).thenReturn(Optional.of(List.of()));
        return backend;
    }

    private ResolvedAuthServiceConfig serviceConfig(Duration skew) {
        return new ResolvedAuthServiceConfig("auth", true, "http://auth.example", "X-Session-Id", "session", Duration.ofSeconds(1),
            Optional.empty(), Optional.empty(), Map.of(), new ResolvedAuthCacheConfig(true, skew, 10));
    }

    private ProxyRequestContext context() {
        return new ProxyRequestContext("POST", "/products", null,
            Map.of("X-Session-Id", List.of("session-1"), "X-Branch", List.of("branch-1")), Map.of());
    }

    private AuthzTokens tokens(String authorizationToken) {
        return new AuthzTokens(authorizationToken, null, null, List.of());
    }

    private String jwt(Instant expiresAt) {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(("{\"exp\":" + expiresAt.getEpochSecond() + "}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".signature";
    }
}
