package com.ai.infrastructure.it;

import com.ai.infrastructure.access.policy.EntityAccessPolicy;
import com.ai.infrastructure.compliance.policy.ComplianceCheckProvider;
import com.ai.infrastructure.compliance.policy.ComplianceCheckResult;
import com.ai.infrastructure.config.AIInfrastructureAutoConfiguration;
import com.ai.infrastructure.chat.repository.ChatSessionRepository;
import com.ai.infrastructure.chat.spi.ChatSessionAccessControlPolicy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Test Application for AI Infrastructure Integration Tests
 * 
 * This is a minimal Spring Boot application used to test the AI Infrastructure
 * module in isolation. It provides a clean environment for integration testing
 * without dependencies on the main backend application.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@SpringBootApplication(scanBasePackages = {"com.ai.infrastructure", "com.ai.infrastructure.it"})
@Import(AIInfrastructureAutoConfiguration.class)
@EntityScan(basePackages = {
    "com.ai.infrastructure.entity",
    "com.ai.infrastructure.it.entity",
    "com.ai.infrastructure.migration.domain"
})
@EnableJpaRepositories(basePackages = {
    "com.ai.infrastructure.repository",
    "com.ai.infrastructure.it.repository",
    "com.ai.infrastructure.migration.repository"
})
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }

    @Bean
    public EntityAccessPolicy testEntityAccessPolicy() {
        return (userId, entity) -> true;
    }

    @Bean
    public ComplianceCheckProvider testComplianceCheckProvider() {
        return request -> ComplianceCheckResult.builder()
            .compliant(true)
            .details("Test compliance provider approval")
            .build();
    }

    @Bean
    public ChatSessionAccessControlPolicy chatSessionAccessControlPolicy(ChatSessionRepository repository) {
        return new ChatSessionAccessControlPolicy() {
            @Override
            public boolean canUserCreateConversation(String ownerId) {
                return repository.countByOwnerId(ownerId) < 200;
            }

            @Override
            public boolean canUserAccessConversation(String requestingUser, String conversationId) {
                return repository.findById(conversationId)
                    .map(session -> session.isOwnedBy(requestingUser))
                    .orElse(true);
            }

            @Override
            public boolean canUserDeleteConversation(String requestingUser, String conversationId) {
                return canUserAccessConversation(requestingUser, conversationId);
            }

            @Override
            public boolean canUserViewHistory(String requestingUser, String conversationId) {
                return canUserAccessConversation(requestingUser, conversationId);
            }
        };
    }
}
