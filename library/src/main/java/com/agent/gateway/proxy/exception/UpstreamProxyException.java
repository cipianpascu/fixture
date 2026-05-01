package com.agent.gateway.proxy.exception;

public class UpstreamProxyException extends RuntimeException {

    public UpstreamProxyException(String message) {
        super(message);
    }

    public UpstreamProxyException(String message, Throwable cause) {
        super(message, cause);
    }
}
