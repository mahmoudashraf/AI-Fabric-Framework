package com.ai.fabric.platform.backend.config;

import java.util.concurrent.Executor;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

@Configuration
@EnableAsync
public class AsyncExecutionConfig {

    private final TaskDecorator taskDecorator;

    public AsyncExecutionConfig(ObjectProvider<TaskDecorator> taskDecoratorProvider) {
        this.taskDecorator = taskDecoratorProvider.getIfAvailable();
    }

    @Bean(name = "releaseExecutionExecutor")
    public Executor releaseExecutionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("release-exec-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        applyTaskDecorator(executor);
        executor.initialize();
        return executor;
    }

    @Bean(name = "hostedVerificationExecutor")
    public Executor hostedVerificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("hosted-verify-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(8);
        applyTaskDecorator(executor);
        executor.initialize();
        return executor;
    }

    @Bean(name = "deploymentDeletionExecutor")
    public Executor deploymentDeletionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("deploy-delete-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(16);
        applyTaskDecorator(executor);
        executor.initialize();
        return executor;
    }

    @Bean(name = "canonicalRolloutExecutor")
    public Executor canonicalRolloutExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("canonical-rollout-");
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(16);
        applyTaskDecorator(executor);
        executor.initialize();
        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }

    @Bean(name = "verificationSuiteExecutor")
    public Executor verificationSuiteExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("verification-suite-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(8);
        applyTaskDecorator(executor);
        executor.initialize();
        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }

    private void applyTaskDecorator(ThreadPoolTaskExecutor executor) {
        if (taskDecorator != null) {
            executor.setTaskDecorator(taskDecorator);
        }
    }
}
