package com.db.olorin.rest.exception;

public class ProxyConfigurationException extends RuntimeException {

    public ProxyConfigurationException(String message) {
        super(message);
    }

    public ProxyConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
