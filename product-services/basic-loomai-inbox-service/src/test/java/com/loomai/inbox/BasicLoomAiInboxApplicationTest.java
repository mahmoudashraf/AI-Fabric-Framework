package com.loomai.inbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "BASIC_INBOX_BINDING_ID=test-binding",
        "BASIC_INBOX_TENANT_ID=test-tenant",
        "BASIC_INBOX_DEPLOYMENT_ID=test-deployment",
        "BASIC_INBOX_PROVIDER_API_KEY=provider-key-that-is-at-least-32-chars",
        "BASIC_INBOX_CONNECTOR_CALLBACK_URL=http://localhost:8080/integrations/conversation-channel/events",
        "BASIC_INBOX_CALLBACK_SIGNING_SECRET=callback-signing-secret-at-least-32-chars",
        "BASIC_INBOX_DATA_ENCRYPTION_SECRET=data-encryption-secret-at-least-32-chars",
        "BASIC_INBOX_OPERATORS=operator|Test Operator|operator-api-key-that-is-at-least-32-chars|test-binding",
        "BASIC_INBOX_JDBC_URL=jdbc:h2:mem:basic-inbox-context;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "BASIC_INBOX_CALLBACK_POLL_MS=600000"
    }
)
class BasicLoomAiInboxApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void startsWithoutAiOrEmbeddingProviders() {
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.containsBean("aiEmbeddingService")).isFalse();
        assertThat(applicationContext.getBean(ObjectMapper.class)).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM inbox_callback_outbox",
            Long.class
        )).isZero();
    }
}
