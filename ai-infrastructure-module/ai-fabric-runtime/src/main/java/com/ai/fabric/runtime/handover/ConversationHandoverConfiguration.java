package com.ai.fabric.runtime.handover;

import ai.fabric.chat.control.ConversationControlGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConversationHandoverProperties.class)
@ConditionalOnProperty(prefix = "loomai.conversation-handover", name = "enabled", havingValue = "true")
public class ConversationHandoverConfiguration {

    @Bean(name = "conversationHandoverSseExecutor")
    ThreadPoolTaskExecutor conversationHandoverSseExecutor(
        ConversationHandoverProperties properties
    ) {
        if (properties.getSseCoreThreads() < 1
            || properties.getSseMaxThreads() < properties.getSseCoreThreads()
            || properties.getSseQueueCapacity() < 0) {
            throw new IllegalStateException(
                "Conversation handover SSE executor limits are invalid."
            );
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getSseCoreThreads());
        executor.setMaxPoolSize(properties.getSseMaxThreads());
        executor.setQueueCapacity(properties.getSseQueueCapacity());
        executor.setThreadNamePrefix("conversation-handover-sse-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    @Bean
    ConversationHandoverSecurity conversationHandoverSecurity(
        ConversationHandoverProperties properties
    ) {
        return new ConversationHandoverSecurity(properties, Clock.systemUTC());
    }

    @Bean
    RuntimeConversationHandoverRepository runtimeConversationHandoverRepository(
        DataSource dataSource,
        PlatformTransactionManager transactionManager,
        ObjectMapper objectMapper,
        ConversationHandoverSecurity security
    ) {
        return new RuntimeConversationHandoverRepository(
            dataSource, transactionManager, objectMapper, security, Clock.systemUTC());
    }
}
