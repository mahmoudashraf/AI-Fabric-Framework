package com.ai.fabric.runtime.auth;

import org.springframework.util.StringUtils;

import java.util.List;

public final class RuntimeLegacyIdentityContract {

    public static final String LEGACY_ENDPOINT_SUNSET = "Wed, 30 Sep 2026 00:00:00 GMT";
    public static final String QUERY_SUCCESSOR_PATH = "/api/chat/me/query";
    public static final String SUGGESTIONS_SUCCESSOR_PATH = "/api/chat/me/suggestions";
    public static final String AUTH_CONTEXT_SUCCESSOR_PATH = "/api/chat/me/auth-context";
    public static final String CONVERSATIONS_SUCCESSOR_PATH = "/api/chat/me/conversations";
    public static final String CONVERSATION_DETAIL_SUCCESSOR_PATH = "/api/chat/me/conversations/{conversationId}";

    private RuntimeLegacyIdentityContract() {
    }

    public static String successorPath(String requestPath) {
        if (!StringUtils.hasText(requestPath)) {
            return null;
        }
        String normalized = requestPath.trim();
        return switch (normalized) {
            case "/api/chat/query" -> QUERY_SUCCESSOR_PATH;
            case "/api/chat/suggestions" -> SUGGESTIONS_SUCCESSOR_PATH;
            case "/api/chat/auth-context" -> AUTH_CONTEXT_SUCCESSOR_PATH;
            case "/api/chat/conversations" -> CONVERSATIONS_SUCCESSOR_PATH;
            case "/api/chat/conversations/{conversationId}" -> CONVERSATION_DETAIL_SUCCESSOR_PATH;
            default -> null;
        };
    }

    public static List<String> successorPaths() {
        return List.of(
            QUERY_SUCCESSOR_PATH,
            SUGGESTIONS_SUCCESSOR_PATH,
            AUTH_CONTEXT_SUCCESSOR_PATH,
            CONVERSATIONS_SUCCESSOR_PATH,
            CONVERSATION_DETAIL_SUCCESSOR_PATH
        );
    }
}
