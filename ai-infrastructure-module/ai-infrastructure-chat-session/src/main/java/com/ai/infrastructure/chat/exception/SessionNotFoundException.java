package com.ai.infrastructure.chat.exception;

public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(String conversationId) {
        super("Conversation not found: " + conversationId);
    }
}
