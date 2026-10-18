package com.ai.fabric.platform.backend.deployment.budget;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class ProviderUsageAllowanceFailure extends ResponseStatusException {
    ProviderUsageAllowanceFailure(HttpStatus status, String code) { super(status, code); }
    static ProviderUsageAllowanceFailure invalid() { return new ProviderUsageAllowanceFailure(HttpStatus.BAD_REQUEST, "PROVIDER_ALLOWANCE_INVALID"); }
    static ProviderUsageAllowanceFailure denied(String code) { return new ProviderUsageAllowanceFailure(HttpStatus.FORBIDDEN, code); }
    static ProviderUsageAllowanceFailure conflict(String code) { return new ProviderUsageAllowanceFailure(HttpStatus.CONFLICT, code); }
    static ProviderUsageAllowanceFailure missing(String code) { return new ProviderUsageAllowanceFailure(HttpStatus.NOT_FOUND, code); }
}
