package com.ai.infrastructure.chat.service;

import com.ai.infrastructure.chat.domain.ChatSession;

import java.util.List;
import java.util.Map;

public interface ChatSessionService {

    String getConversationContext(String conversationId, String ownerId);

    void recordTurn(String conversationId,
                    String ownerId,
                    String userQuery,
                    String aiResponse,
                    Map<String, Object> turnMetadata,
                    Map<String, Object> uiMetadata);

    ChatSession getSession(String conversationId, String ownerId);

    void mergeSessionMetadata(String conversationId, String ownerId, Map<String, Object> updates);

    List<ChatSession> getUserConversations(String ownerId);

    void deleteConversation(String conversationId, String ownerId);
}
