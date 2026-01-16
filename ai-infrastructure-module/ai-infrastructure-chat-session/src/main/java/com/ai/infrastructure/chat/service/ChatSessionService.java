package com.ai.infrastructure.chat.service;

import com.ai.infrastructure.chat.domain.ChatSession;

import java.util.List;

public interface ChatSessionService {

    String getConversationContext(String conversationId, String ownerId);

    void recordTurn(String conversationId, String ownerId, String userQuery, String aiResponse);

    ChatSession getSession(String conversationId, String ownerId);

    List<ChatSession> getUserConversations(String ownerId);

    void deleteConversation(String conversationId, String ownerId);
}

