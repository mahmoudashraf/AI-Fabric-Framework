package com.ai.infrastructure.chat.storage;

import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.domain.SessionStatus;
import com.ai.infrastructure.chat.repository.ChatSessionRepository;
import com.ai.infrastructure.chat.spi.ChatSessionStorageProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnMissingBean(ChatSessionStorageProvider.class)
public class DefaultDatabaseChatSessionStorage implements ChatSessionStorageProvider {

    private final ChatSessionRepository repository;

    @Override
    public ChatSession save(ChatSession session) {
        ChatSession saved = repository.save(session);
        log.debug("Session saved to database: conversationId={}, owner={}", saved.getId(), saved.getOwnerId());
        return saved;
    }

    @Override
    public Optional<ChatSession> findById(String conversationId) {
        return repository.findById(conversationId);
    }

    @Override
    public void deleteById(String conversationId) {
        repository.deleteById(conversationId);
        log.debug("Session deleted from database: {}", conversationId);
    }

    @Override
    public List<ChatSession> findByOwnerId(String ownerId) {
        return repository.findByOwnerId(ownerId);
    }

    @Override
    public List<ChatSession> findExpiredSessions(int ttlMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(ttlMinutes);
        return repository.findByLastInteractionAtBeforeAndStatus(cutoff, SessionStatus.ACTIVE);
    }
}
