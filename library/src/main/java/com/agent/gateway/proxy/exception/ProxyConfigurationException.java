package com.agent.gateway.proxy.exception;

public class ProxyConfigurationException extends RuntimeException {

    public ProxyConfigurationException(String message) {
        super(message);
    }

    public ProxyConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
