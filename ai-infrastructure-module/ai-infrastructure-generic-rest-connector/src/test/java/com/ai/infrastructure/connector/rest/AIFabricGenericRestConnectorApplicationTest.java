package com.ai.infrastructure.connector.rest;

import ai.fabric.chat.channel.ConversationChannelEvent;
import ai.fabric.chat.channel.ConversationChannelEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.flyway.enabled=false"
)
@Import(AIFabricGenericRestConnectorApplicationTest.TestDataSourceConfiguration.class)
class AIFabricGenericRestConnectorApplicationTest {

    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;

    @Autowired
    AIFabricGenericRestConnectorApplicationTest(
        ApplicationContext applicationContext,
        ObjectMapper objectMapper
    ) {
        this.applicationContext = applicationContext;
        this.objectMapper = objectMapper;
    }

    @Test
    void startsWithoutAiProviderConfiguration() {
        assertThat(applicationContext.containsBean("entityManagerFactory")).isFalse();
    }

    @Test
    void keepsDatabaseInfrastructureBehindConnectorPersistenceOptIn() {
        SpringBootApplication annotation = AIFabricGenericRestConnectorApplication.class
            .getAnnotation(SpringBootApplication.class);

        assertThat(annotation.exclude()).containsExactlyInAnyOrder(
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataJpaRepositoriesAutoConfiguration.class
        );
    }

    @Test
    void deserializesConversationChannelEventsWithIsoInstantTimestamps() throws Exception {
        ConversationChannelEvent event = objectMapper.readValue("""
            {
              "eventId": "event-1",
              "type": "HANDOVER_CLAIMED",
              "channelBindingId": "basic-inbox",
              "handoverId": "handover-1",
              "externalConversationRef": "provider:handover-1",
              "externalActorRef": "operator-1",
              "safeActorDisplayName": "Operator",
              "messageId": null,
              "text": null,
              "expectedControlVersion": 1,
              "metadata": {"provider": "basic-inbox"},
              "occurredAt": "2026-10-13T08:28:29.675154Z"
            }
            """, ConversationChannelEvent.class);

        assertThat(event.type()).isEqualTo(ConversationChannelEventType.HANDOVER_CLAIMED);
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-10-13T08:28:29.675154Z"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestDataSourceConfiguration {

        @Bean(destroyMethod = "close")
        HikariDataSource connectorTestDataSource() {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:h2:mem:connector-startup;DB_CLOSE_DELAY=-1");
            config.setUsername("sa");
            config.setPassword("");
            return new HikariDataSource(config);
        }
    }
}
