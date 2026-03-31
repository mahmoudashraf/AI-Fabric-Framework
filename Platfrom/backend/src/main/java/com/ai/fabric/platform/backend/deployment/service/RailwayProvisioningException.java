package com.ai.fabric.platform.backend.deployment.service;

public class RailwayProvisioningException extends RuntimeException {

    public RailwayProvisioningException(String message) {
        super(message);
    }

    public RailwayProvisioningException(String message, Throwable cause) {
        super(message, cause);
    }
}
