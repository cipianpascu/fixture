package com.agent.gateway.proxy.exception;

public class SoapFaultException extends RuntimeException {

    public SoapFaultException(String message) {
        super(message);
    }
}
