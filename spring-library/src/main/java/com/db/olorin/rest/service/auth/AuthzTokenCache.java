package com.db.olorin.rest.service.auth;

import com.db.olorin.rest.auth.AuthzTokens;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AuthzTokenCache {

    private final Map<Key, Entry> entries = new ConcurrentHashMap<>();

    public Optional<AuthzTokens> get(String sessionId, String backendName) {
        Key key = new Key(sessionId, backendName);
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.expiresAt().isAfter(Instant.now())) {
            entries.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.tokens());
    }

    public void put(
        String sessionId,
        String backendName,
        AuthzTokens tokens,
        Instant expiresAt,
        int maxSize) {
        if (tokens == null || expiresAt == null || !expiresAt.isAfter(Instant.now()) || maxSize <= 0) {
            return;
        }

        evictExpired();
        if (entries.size() >= maxSize) {
            entries.keySet().stream().findFirst().ifPresent(entries::remove);
        }

        if (entries.size() < maxSize) {
            entries.put(new Key(sessionId, backendName), new Entry(tokens, expiresAt));
        }
    }

    void clear() {
        entries.clear();
    }

    private void evictExpired() {
        Instant now = Instant.now();
        entries.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private record Key(String sessionId, String backendName) {
    }

    private record Entry(AuthzTokens tokens, Instant expiresAt) {
    }
}
