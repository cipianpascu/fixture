package com.db.olorin.rest.service.auth;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public record ResolvedAuthServiceConfig(
    String name,
    boolean enabled,
    String serviceUrl,
    String sessionIdHeader,
    String sessionIdCookie,
    Duration timeout,
    Optional<String> tlsProfile,
    Optional<String> securityType,
    Map<String, String> securityConfig,
    ResolvedAuthCacheConfig cache
) {
}
