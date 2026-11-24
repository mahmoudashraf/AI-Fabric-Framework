package com.ai.fabric.platform.backend.assistant;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Only application-authored contract instructions, never caller values or provider exception bodies. */
final class AuthoringContractException extends ResponseStatusException {
    private final String code;
    AuthoringContractException(String code, String instruction) {
        super(HttpStatus.BAD_REQUEST, instruction);
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,127}") || instruction == null || instruction.length() > 2000)
            throw new IllegalArgumentException("Invalid authoring contract finding");
        this.code = code;
    }
    String code() { return code; }
}
