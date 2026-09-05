package com.db.olorin.rest.exception;

public class SoapFaultException extends RuntimeException {

    public SoapFaultException(String message) {
        super(message);
    }
}
