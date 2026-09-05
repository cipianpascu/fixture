package com.db.olorin.core.configuration;

import java.io.Serializable;
import java.util.List;

/**
 * A configuration identified by its name and containing named configuration items.
 */
public interface ConfigurationIF extends Serializable {

    String getName();

    List<ConfigurationItemIF> getConfigurationItems();

    List<ConfigurationItemIF> getConfigurationItemsByGroup(String groupName);

    ConfigurationItemIF getConfigurationItem(String name);

    ConfigurationItemIF getConfigurationItemOrDefault(String name, String defaultValue);
}
