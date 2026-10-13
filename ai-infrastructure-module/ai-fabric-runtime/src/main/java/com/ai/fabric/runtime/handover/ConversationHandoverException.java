package com.ai.fabric.runtime.handover;

import org.springframework.http.HttpStatus;

public final class ConversationHandoverException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public ConversationHandoverException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public HttpStatus status() { return status; }
}
