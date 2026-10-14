package com.ai.fabric.platform.backend.deployment.retirement;

public class TargetRetirementFailure extends org.springframework.web.server.ResponseStatusException {
    private final String code;
    public TargetRetirementFailure(String code) { super(org.springframework.http.HttpStatus.CONFLICT, code); this.code = code; }
    public String code() { return code; }
}
