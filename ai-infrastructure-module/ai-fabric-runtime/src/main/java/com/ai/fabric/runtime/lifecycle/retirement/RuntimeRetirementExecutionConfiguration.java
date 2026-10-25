package com.ai.fabric.runtime.lifecycle.retirement;

import ai.fabric.execution.config.AIExecutionProperties;
import java.util.concurrent.Future;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.server.ResponseStatusException;

/** Uses the existing 0.8.41 executor injection point, including recovered and parallel chain work. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "loomai.runtime.target-retirement.enabled", havingValue = "true")
@EnableConfigurationProperties(AIExecutionProperties.class)
public class RuntimeRetirementExecutionConfiguration {
    @Bean(name = "aiFabricExecutionTaskExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "aiFabricExecutionTaskExecutor")
    ThreadPoolTaskExecutor retirementExecutionTaskExecutor(AIExecutionProperties properties,
        ObjectProvider<RuntimeTargetRetirementService> service, RuntimeRetirementFencingCoverage coverage) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ai-fabric-execution-");
        executor.setCorePoolSize(properties.getAsync().getCorePoolSize());
        executor.setMaxPoolSize(properties.getAsync().getMaxPoolSize());
        executor.setQueueCapacity(properties.getAsync().getQueueCapacity());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setTaskDecorator(task -> () -> {
            try {
                service.getObject().withObservedWorkerBoundary("specialist-execution", true, () -> {
                    task.run();
                    return null;
                });
            } catch (ResponseStatusException blocked) {
                if (!"RUNTIME_TARGET_AUTHORITY_FENCED".equals(blocked.getReason())) throw blocked;
                if (task instanceof Future<?> future) future.cancel(false);
            }
        });
        coverage.installed("aiFabricExecutionTaskExecutor.attempts");
        return executor;
    }
}
