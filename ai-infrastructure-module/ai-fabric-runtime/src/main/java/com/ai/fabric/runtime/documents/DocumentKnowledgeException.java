package com.ai.fabric.runtime.documents;

import org.springframework.http.HttpStatus;

public class DocumentKnowledgeException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public DocumentKnowledgeException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public DocumentKnowledgeException(String code, HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
