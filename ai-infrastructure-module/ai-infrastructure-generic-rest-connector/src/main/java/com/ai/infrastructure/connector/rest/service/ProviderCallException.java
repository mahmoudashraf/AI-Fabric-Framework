package com.ai.infrastructure.connector.rest.service;

public class ProviderCallException extends RuntimeException {

    private final ProviderErrorClass errorClass;
    private final int status;

    public ProviderCallException(ProviderErrorClass errorClass, int status, String message) {
        super(message);
        this.errorClass = errorClass;
        this.status = status;
    }

    public ProviderCallException(ProviderErrorClass errorClass, int status, String message, Throwable cause) {
        super(message, cause);
        this.errorClass = errorClass;
        this.status = status;
    }

    public ProviderErrorClass errorClass() {
        return errorClass;
    }

    public int status() {
        return status;
    }
}
