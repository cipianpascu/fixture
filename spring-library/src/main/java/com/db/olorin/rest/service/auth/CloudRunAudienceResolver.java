package com.db.olorin.rest.service.auth;

import com.db.olorin.rest.exception.ProxyConfigurationException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

final class CloudRunAudienceResolver {

    private CloudRunAudienceResolver() {
    }

    static String resolveAudience(String baseUrl, Map<String, String> securityConfig, String targetName) {
        String configuredAudience = securityConfig != null ? securityConfig.get("audience") : null;
        if (configuredAudience != null && !configuredAudience.isBlank()) {
            return configuredAudience;
        }

        try {
            URI uri = new URI(baseUrl);
            if (uri.getScheme() == null || uri.getAuthority() == null) {
                throw new ProxyConfigurationException(
                    "Cloud Run %s baseUrl must be an absolute URL: '%s'".formatted(targetName, baseUrl));
            }
            return new URI(uri.getScheme(), uri.getAuthority(), "/", null, null).toString();
        } catch (URISyntaxException e) {
            throw new ProxyConfigurationException(
                "Invalid Cloud Run audience/baseUrl '%s' for %s".formatted(baseUrl, targetName), e);
        }
    }
}
