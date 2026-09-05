package com.db.olorin.rest.exception;

public class UpstreamProxyException extends RuntimeException {

    public UpstreamProxyException(String message) {
        super(message);
    }

    public UpstreamProxyException(String message, Throwable cause) {
        super(message, cause);
    }
}
