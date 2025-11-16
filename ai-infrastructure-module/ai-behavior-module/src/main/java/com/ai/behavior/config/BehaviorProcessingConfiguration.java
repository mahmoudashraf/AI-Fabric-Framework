package com.ai.behavior.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@RequiredArgsConstructor
public class BehaviorProcessingConfiguration {

    private final BehaviorProperties properties;

    @Bean(name = "aiBehaviorExecutor")
    public TaskExecutor aiBehaviorExecutor() {
        var settings = properties.getProcessing().getAsyncExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(settings.getCorePoolSize());
        executor.setMaxPoolSize(settings.getMaxPoolSize());
        executor.setQueueCapacity(settings.getQueueCapacity());
        executor.setThreadNamePrefix("ai-behavior-");
        executor.initialize();
        return executor;
    }
}
