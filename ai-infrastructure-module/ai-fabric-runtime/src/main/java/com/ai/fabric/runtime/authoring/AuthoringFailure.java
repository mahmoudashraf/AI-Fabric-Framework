package com.ai.fabric.runtime.authoring;

import org.springframework.http.HttpStatus;

final class AuthoringFailure extends RuntimeException {
    final HttpStatus status;
    final String code;

    AuthoringFailure(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
