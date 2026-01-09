package com.ai.infrastructure.relationship.it.realapi;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.config.ProviderConfiguration;
import com.ai.infrastructure.provider.AIProviderManager;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * Minimal Spring Boot configuration for Real API connectivity checks.
 *
 * <p>Intentionally avoids scanning the relationship-query IT web/controllers to prevent
 * unrelated bean wiring from failing the fast pre-check.</p>
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
@EnableConfigurationProperties(AIProviderConfig.class)
@ComponentScan(basePackageClasses = {AIProviderManager.class})
@Import(ProviderConfiguration.class)
class RelationshipQueryConnectivityTestApplication {
}

