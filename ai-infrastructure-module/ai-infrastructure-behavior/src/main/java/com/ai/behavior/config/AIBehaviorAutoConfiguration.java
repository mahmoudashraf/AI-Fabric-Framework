package com.ai.behavior.config;

import com.ai.infrastructure.config.AIInfrastructureAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@AutoConfiguration
@AutoConfigureAfter(AIInfrastructureAutoConfiguration.class)
@EnableConfigurationProperties(BehaviorModuleProperties.class)
@EnableAsync
@EnableScheduling
public class AIBehaviorAutoConfiguration {

    @Bean(name = "behaviorAsyncExecutor")
    public TaskExecutor behaviorAsyncExecutor(BehaviorModuleProperties properties) {
        BehaviorModuleProperties.Performance.AsyncExecutor executorProps = properties.getPerformance().getAsyncExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("behavior-async-");
        executor.setCorePoolSize(executorProps.getCorePoolSize());
        executor.setMaxPoolSize(executorProps.getMaxPoolSize());
        executor.setQueueCapacity(executorProps.getQueueCapacity());
        executor.initialize();
        return executor;
    }
}
