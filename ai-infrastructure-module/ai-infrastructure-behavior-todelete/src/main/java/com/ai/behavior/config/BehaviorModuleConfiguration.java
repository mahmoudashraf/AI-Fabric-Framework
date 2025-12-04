package com.ai.behavior.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Base configuration for the AI Behavior Analytics module.
 * <p>
 * Scheduling, caching, and async execution are enabled at the module level
 * so that services (workers, caches, async ingestion) can rely on these
 * Spring features without additional boilerplate.
 */
@Configuration
@EnableScheduling
@EnableCaching
@EnableAsync
public class BehaviorModuleConfiguration {
}
