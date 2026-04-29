package com.ai.fabric.platform.backend.partner.security;

public class PartnerUnauthorizedException extends RuntimeException {
    public PartnerUnauthorizedException(String message) {
        super(message);
    }
}
