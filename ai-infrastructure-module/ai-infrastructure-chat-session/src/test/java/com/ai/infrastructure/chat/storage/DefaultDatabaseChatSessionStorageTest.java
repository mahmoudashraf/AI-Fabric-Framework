package com.ai.infrastructure.chat.storage;

import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.domain.ChatTurn;
import com.ai.infrastructure.chat.domain.SessionStatus;
import com.ai.infrastructure.chat.repository.ChatSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create")
@Import(DefaultDatabaseChatSessionStorageTest.TestConfig.class)
class DefaultDatabaseChatSessionStorageTest {

    @Autowired
    private ChatSessionRepository repository;

    private DefaultDatabaseChatSessionStorage storage;
    private final Clock clock = Clock.systemUTC();

    @BeforeEach
    void setUp() {
        storage = new DefaultDatabaseChatSessionStorage(repository);
    }

    @Test
    void shouldSaveConversationToDatabase() {
        ChatSession session = ChatSession.newSession("conv-1", "owner-1", clock);
        session.addTurn(sampleTurn(), clock);

        ChatSession saved = storage.save(session);

        assertThat(saved.getId()).isEqualTo("conv-1");
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findById("conv-1")).isPresent();
    }

    @Test
    void shouldFindExpiredConversations() {
        ChatSession active = ChatSession.newSession("conv-2", "owner-1", clock);
        active.addTurn(sampleTurn(), clock);

        ChatSession expired = ChatSession.newSession("conv-3", "owner-1", clock);
        expired.setLastInteractionAt(LocalDateTime.now(clock).minusMinutes(120));
        expired.setStatus(SessionStatus.ACTIVE);

        repository.saveAll(List.of(active, expired));

        List<ChatSession> expiredSessions = storage.findExpiredSessions(60);

        assertThat(expiredSessions).extracting(ChatSession::getId).contains("conv-3");
    }

    @Test
    void shouldFindConversationByOwner() {
        ChatSession session = ChatSession.newSession("conv-4", "owner-xyz", clock);
        storage.save(session);

        List<ChatSession> sessions = storage.findByOwnerId("owner-xyz");

        assertThat(sessions).hasSize(1);
    }

    private ChatTurn sampleTurn() {
        return ChatTurn.builder()
            .userQuery("hello")
            .aiResponse("hi")
            .timestamp(LocalDateTime.now(clock))
            .build();
    }

    @Configuration
    @EnableJpaRepositories(basePackageClasses = ChatSessionRepository.class)
    @EntityScan(basePackageClasses = ChatSession.class)
    @Import(DefaultDatabaseChatSessionStorage.class)
    static class TestConfig {
    }
}
