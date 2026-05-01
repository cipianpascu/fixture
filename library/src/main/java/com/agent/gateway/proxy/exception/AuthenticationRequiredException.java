package com.agent.gateway.proxy.exception;

public class AuthenticationRequiredException extends RuntimeException {

    public AuthenticationRequiredException(String message) {
        super(message);
    }
}
