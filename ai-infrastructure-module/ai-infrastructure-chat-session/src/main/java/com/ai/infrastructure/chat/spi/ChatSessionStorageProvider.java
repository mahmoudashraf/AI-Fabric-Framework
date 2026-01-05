package com.ai.infrastructure.chat.spi;

import com.ai.infrastructure.chat.domain.ChatSession;

import java.util.List;
import java.util.Optional;

public interface ChatSessionStorageProvider {
    ChatSession save(ChatSession session);
    Optional<ChatSession> findById(String conversationId);
    void deleteById(String conversationId);
    List<ChatSession> findByOwnerId(String ownerId);
    List<ChatSession> findExpiredSessions(int ttlMinutes);
}
