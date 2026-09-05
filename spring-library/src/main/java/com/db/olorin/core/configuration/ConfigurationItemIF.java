package com.db.olorin.core.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * A named configuration value with convenience typed accessors.
 */
public interface ConfigurationItemIF extends Serializable {

    String getName();

    String getValue();

    @JsonIgnore
    void setValue(boolean value);

    @JsonIgnore
    void setValue(int value);

    @JsonIgnore
    void setValue(long value);

    @JsonIgnore
    void setValue(double value);

    void setName(String name);

    @JsonProperty("value")
    void setValue(String value);

    String getValueOrDefaultIfNull(String defaultIfNull);

    boolean getBooleanValue();

    int getIntValue();

    long getLongValue();

    double getDoubleValue();

    boolean getBooleanValueOrDefaultIfNull(boolean value);

    int getIntValueOrDefaultIfNull(int value);

    long getLongValueOrDefaultIfNull(long value);

    double getDoubleValueOrDefaultIfNull(double value);
}
