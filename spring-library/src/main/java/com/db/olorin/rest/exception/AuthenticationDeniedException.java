package com.db.olorin.rest.exception;

public class AuthenticationDeniedException extends RuntimeException {

    public AuthenticationDeniedException(String message) {
        super(message);
    }
}
