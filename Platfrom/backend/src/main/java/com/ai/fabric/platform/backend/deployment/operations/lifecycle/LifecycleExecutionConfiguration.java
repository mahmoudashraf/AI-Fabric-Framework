package com.ai.fabric.platform.backend.deployment.operations.lifecycle;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class LifecycleExecutionConfiguration {
    @Bean(name = "lifecycleNativeExecutor")
    ThreadPoolTaskExecutor lifecycleNativeExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("native-lifecycle-");
        executor.setCorePoolSize(2); executor.setMaxPoolSize(2); executor.setQueueCapacity(20);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
