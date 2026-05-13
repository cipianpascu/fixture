package com.agent.gateway.proxy.exception;

public class AuthenticationDeniedException extends RuntimeException {

    public AuthenticationDeniedException(String message) {
        super(message);
    }
}
