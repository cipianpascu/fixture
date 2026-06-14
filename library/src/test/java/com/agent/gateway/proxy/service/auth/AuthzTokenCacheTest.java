package com.agent.gateway.proxy.service.auth;

import com.agent.gateway.proxy.auth.AuthzTokens;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthzTokenCacheTest {

    @Test
    void storesEntriesBySessionAndBackend() {
        AuthzTokenCache cache = new AuthzTokenCache();
        AuthzTokens tokens = new AuthzTokens("authz", null, null, List.of("scope"));

        cache.put("session-1", "orders", tokens, Instant.now().plusSeconds(60), 100);

        assertEquals(tokens, cache.get("session-1", "orders").orElseThrow());
        assertTrue(cache.get("session-1", "customers").isEmpty());
        assertTrue(cache.get("session-2", "orders").isEmpty());
    }

    @Test
    void doesNotReturnExpiredEntries() {
        AuthzTokenCache cache = new AuthzTokenCache();
        AuthzTokens tokens = new AuthzTokens("authz", null, null, List.of("scope"));

        cache.put("session-1", "orders", tokens, Instant.now().minusSeconds(1), 100);

        assertTrue(cache.get("session-1", "orders").isEmpty());
    }
}
