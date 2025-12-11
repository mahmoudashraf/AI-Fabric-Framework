package com.ai.infrastructure.config;

import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.storage.AIStorageProperties;
import com.ai.infrastructure.storage.StorageStrategyProvider;
import com.ai.infrastructure.storage.strategy.AISearchableEntityStorageStrategy;
import com.ai.infrastructure.storage.strategy.SingleTableStorageStrategy;
import com.ai.infrastructure.storage.strategy.impl.PerTypeRepositoryFactory;
import com.ai.infrastructure.storage.strategy.impl.PerTypeTableStorageStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
@EnableConfigurationProperties(AIStorageProperties.class)
@Slf4j
public class AISearchableStorageStrategyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public StorageStrategyProvider storageStrategyProvider(AIStorageProperties properties) {
        return new StorageStrategyProvider(properties);
    }

    @Bean
    @ConditionalOnProperty(
        name = "ai-infrastructure.storage.strategy",
        havingValue = "SINGLE_TABLE",
        matchIfMissing = true
    )
    @ConditionalOnMissingBean(AISearchableEntityStorageStrategy.class)
    public AISearchableEntityStorageStrategy singleTableStorageStrategy(
        AISearchableEntityRepository repository
    ) {
        log.info("Using SINGLE_TABLE storage strategy");
        return new SingleTableStorageStrategy(repository);
    }

    @Bean
    @ConditionalOnProperty(
        name = "ai-infrastructure.storage.strategy",
        havingValue = "PER_TYPE_TABLE"
    )
    public PerTypeRepositoryFactory perTypeRepositoryFactory(
        NamedParameterJdbcTemplate jdbcTemplate,
        AIEntityConfigurationLoader configurationLoader
    ) {
        return new PerTypeRepositoryFactory(jdbcTemplate, configurationLoader);
    }

    @Bean
    @ConditionalOnProperty(
        name = "ai-infrastructure.storage.strategy",
        havingValue = "PER_TYPE_TABLE"
    )
    @ConditionalOnMissingBean(AISearchableEntityStorageStrategy.class)
    public AISearchableEntityStorageStrategy perTypeTableStorageStrategy(
        PerTypeRepositoryFactory repositoryFactory
    ) {
        log.info("Using PER_TYPE_TABLE storage strategy");
        return new PerTypeTableStorageStrategy(repositoryFactory);
    }
}
