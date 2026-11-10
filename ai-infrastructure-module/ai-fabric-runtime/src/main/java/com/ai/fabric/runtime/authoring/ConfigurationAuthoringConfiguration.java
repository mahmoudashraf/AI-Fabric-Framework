package com.ai.fabric.runtime.authoring;

import ai.fabric.core.AICoreService;
import ai.fabric.llm.structured.StructuredJsonCallExecutor;
import com.ai.fabric.budget.openai.BudgetGuardedSpringAiModelResolver;
import com.ai.fabric.runtime.budget.RuntimeBudgetBindingService;
import com.ai.fabric.runtime.budget.RuntimeBudgetTurnScopes;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "loomai.authoring", name = "enabled", havingValue = "true")
public class ConfigurationAuthoringConfiguration {
    @Bean
    WebMvcConfigurer authoringJsonHttpConfiguration(ObjectMapper mapper) {
        return new WebMvcConfigurer() {
            @Override public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
                builder.configureMessageConvertersList(converters -> converters.addFirst(new AuthoringHttpMessageConverter(mapper)));
            }
        };
    }

    @Bean(name = "configurationAuthoringExecutor", destroyMethod = "shutdownNow")
    ThreadPoolExecutor configurationAuthoringExecutor() {
        return new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8),
            Thread.ofPlatform().name("configuration-authoring-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    ConfigurationAuthoringService configurationAuthoringService(AICoreService aiCore,
        StructuredJsonCallExecutor structured, ObjectMapper mapper, RuntimeBudgetTurnScopes turns,
        RuntimeBudgetBindingService budgets, BudgetGuardedSpringAiModelResolver guardedResolver,
        @Qualifier("configurationAuthoringExecutor") ThreadPoolExecutor executor,
        @Value("${loomai.authoring.deadline-ms:45000}") long deadlineMs,
        @Value("${loomai.authoring.max-output-tokens:4096}") int maxOutputTokens) {
        // Required guarded-resolver dependency prevents enabling an unaccounted provider path.
        return new ConfigurationAuthoringService(aiCore, structured, mapper, turns, budgets, executor, deadlineMs, maxOutputTokens);
    }
}
