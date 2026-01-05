package com.ai.infrastructure.chat.repository;

import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.domain.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

    List<ChatSession> findByOwnerId(String ownerId);

    List<ChatSession> findByLastInteractionAtBeforeAndStatus(LocalDateTime cutoff, SessionStatus status);

    long countByOwnerId(String ownerId);
}
