package com.ai.infrastructure.annotation;

import com.ai.infrastructure.config.AIInfrastructureAutoConfiguration;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;

/**
 * Explicit entry point for enabling AI Fabric auto-configuration.
 *
 * <p>Note: the framework also registers auto-configuration via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * This annotation exists as a clearer "happy path" for consumers and documentation.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ImportAutoConfiguration(AIInfrastructureAutoConfiguration.class)
public @interface EnableAIInfrastructure {}

