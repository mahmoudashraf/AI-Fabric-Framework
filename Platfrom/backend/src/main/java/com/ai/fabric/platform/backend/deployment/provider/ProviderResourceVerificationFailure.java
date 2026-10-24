package com.ai.fabric.platform.backend.deployment.provider;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ProviderResourceVerificationFailure extends ResponseStatusException {
    private final String code;

    public ProviderResourceVerificationFailure(String code) {
        super(HttpStatus.CONFLICT, code);
        this.code = code;
    }

    public String code() { return code; }
}
