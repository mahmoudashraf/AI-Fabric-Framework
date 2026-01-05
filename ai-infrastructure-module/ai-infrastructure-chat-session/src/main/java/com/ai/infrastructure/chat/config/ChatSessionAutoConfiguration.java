package com.ai.infrastructure.chat.config;

import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.repository.ChatSessionRepository;
import com.ai.infrastructure.chat.service.ChatSessionServiceImpl;
import com.ai.infrastructure.chat.storage.DefaultDatabaseChatSessionStorage;
import com.ai.infrastructure.chat.strategy.SlidingWindowMemoryStrategy;
import com.ai.infrastructure.chat.strategy.SummaryMemoryStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(ChatSessionProperties.class)
@ConditionalOnProperty(prefix = "ai.chat", name = "enabled", havingValue = "true", matchIfMissing = true)
@EntityScan(basePackageClasses = ChatSession.class)
@EnableJpaRepositories(basePackageClasses = ChatSessionRepository.class)
@Import({
    SlidingWindowMemoryStrategy.class,
    SummaryMemoryStrategy.class,
    DefaultDatabaseChatSessionStorage.class,
    ChatSessionServiceImpl.class
})
public class ChatSessionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Clock chatSessionClock() {
        return Clock.systemUTC();
    }
}
