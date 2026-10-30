package com.ai.fabric.runtime.specialist.assistance;

import ai.fabric.execution.config.AIExecutionProperties;
import com.ai.fabric.runtime.budget.RuntimeBudgetTurnScopes;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AIExecutionProperties.class)
@ConditionalOnProperty(name = "loomai.runtime.target-retirement.enabled", havingValue = "false", matchIfMissing = true)
public class SpecialistAssistanceExecutionConfiguration {
    @Bean(name = "aiFabricExecutionTaskExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "aiFabricExecutionTaskExecutor")
    ThreadPoolTaskExecutor executionTaskExecutor(AIExecutionProperties properties, ObjectProvider<RuntimeBudgetTurnScopes> budgetTurns) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ai-fabric-execution-");
        executor.setCorePoolSize(properties.getAsync().getCorePoolSize());
        executor.setMaxPoolSize(properties.getAsync().getMaxPoolSize());
        executor.setQueueCapacity(properties.getAsync().getQueueCapacity());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setTaskDecorator(task -> {
            var turns = budgetTurns.getIfAvailable();
            return turns == null ? task : turns.propagateTask(task);
        });
        return executor;
    }
}
