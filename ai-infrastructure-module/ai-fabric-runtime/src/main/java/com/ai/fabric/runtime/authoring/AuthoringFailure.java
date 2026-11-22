package com.ai.fabric.runtime.authoring;

import org.springframework.http.HttpStatus;

final class AuthoringFailure extends RuntimeException {
    final HttpStatus status;
    final String code;
    final String contractIssue;
    final String contractField;

    AuthoringFailure(HttpStatus status, String code, String message) {
        this(status, code, message, null, null);
    }

    AuthoringFailure(HttpStatus status, String code, String message, String contractIssue, String contractField) {
        super(message);
        this.status = status;
        this.code = code;
        this.contractIssue = contractIssue;
        this.contractField = contractField;
    }
}
