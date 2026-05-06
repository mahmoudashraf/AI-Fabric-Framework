package com.ai.fabric.platform.backend.deployment.service;

public class CoolifyApiException extends RuntimeException {

    private final int statusCode;
    private final String path;

    public CoolifyApiException(String message, int statusCode, String path) {
        super(message);
        this.statusCode = statusCode;
        this.path = path;
    }

    public CoolifyApiException(String message, int statusCode, String path, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.path = path;
    }

    public int statusCode() {
        return statusCode;
    }

    public String path() {
        return path;
    }
}
