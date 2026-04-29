package com.ai.fabric.platform.backend.partner.security;

public class PartnerForbiddenException extends RuntimeException {
    public PartnerForbiddenException(String message) {
        super(message);
    }
}
