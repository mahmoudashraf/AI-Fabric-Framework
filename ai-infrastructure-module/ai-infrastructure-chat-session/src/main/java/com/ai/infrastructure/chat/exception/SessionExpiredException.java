package com.ai.infrastructure.chat.exception;

public class SessionExpiredException extends RuntimeException {
    public SessionExpiredException(String conversationId) {
        super("Conversation expired: " + conversationId);
    }
}
