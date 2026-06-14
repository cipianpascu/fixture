package com.agent.gateway.proxy.service.auth;

import java.time.Duration;

public record ResolvedAuthCacheConfig(
    boolean enabled,
    Duration expirySkew,
    int maxSize
) {
    public static ResolvedAuthCacheConfig disabled() {
        return new ResolvedAuthCacheConfig(false, Duration.ofSeconds(30), 10_000);
    }
}
