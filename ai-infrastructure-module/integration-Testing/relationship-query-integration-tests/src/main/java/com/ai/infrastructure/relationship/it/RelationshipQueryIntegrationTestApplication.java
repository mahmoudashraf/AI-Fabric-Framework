package com.ai.infrastructure.relationship.it;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal Spring Boot application exposing the relationship query module through
 * HTTP endpoints for real API integration tests.
 */
@SpringBootApplication(scanBasePackages = {
    "com.ai.infrastructure",
    "com.ai.infrastructure.relationship.it"
})
@Import({
    com.ai.infrastructure.config.AIInfrastructureAutoConfiguration.class,
    com.ai.infrastructure.relationship.config.RelationshipQueryAutoConfiguration.class
})
@EntityScan(basePackages = {
    "com.ai.infrastructure.entity",
    "com.ai.infrastructure.relationship.it.entity"
})
@EnableJpaRepositories(basePackages = {
    "com.ai.infrastructure.repository",
    "com.ai.infrastructure.relationship.it.repository"
})
public class RelationshipQueryIntegrationTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(RelationshipQueryIntegrationTestApplication.class, args);
    }
}
