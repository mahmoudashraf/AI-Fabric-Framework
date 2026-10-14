package com.ai.fabric.platform.backend.deployment.admission;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class AdmissionFailure extends ResponseStatusException {
    public AdmissionFailure(HttpStatus status, String code) { super(status, code); }
}
