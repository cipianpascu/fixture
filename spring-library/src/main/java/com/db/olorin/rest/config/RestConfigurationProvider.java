package com.db.olorin.rest.config;

import com.db.olorin.core.configuration.ConfigurationIF;

/**
 * Implemented by the host application after it has loaded its static core configuration.
 */
@FunctionalInterface
public interface RestConfigurationProvider {
    ConfigurationIF configuration();
}
